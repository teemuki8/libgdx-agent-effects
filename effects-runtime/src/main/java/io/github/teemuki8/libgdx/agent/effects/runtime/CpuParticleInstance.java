package io.github.teemuki8.libgdx.agent.effects.runtime;

import io.github.teemuki8.libgdx.agent.effects.core.ColorGradient;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleCapacityPolicy;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleModifier;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleSnapshot;
import io.github.teemuki8.libgdx.agent.effects.core.RuntimeLimits;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Deterministic fixed-step structure-of-arrays CPU particle simulation. */
public final class CpuParticleInstance implements AutoCloseable {
    private static final int HARD_BURST_CAP = 1024 * 1024;

    private final ParticleDefinition definition;
    private final RuntimeLimits limits;
    private final StableRandom random;
    private final boolean[] alive;
    private final long[] spawnId;
    private final float[] x;
    private final float[] y;
    private final float[] z;
    private final float[] velocityX;
    private final float[] velocityY;
    private final float[] velocityZ;
    private final float[] age;
    private long nextSpawnId;
    private long droppedParticles;
    private long evictedParticles;
    private float anchorX;
    private float anchorY;
    private float anchorZ;
    private float stepAccumulator;
    private float emissionAccumulator;
    private boolean hasAnchor;
    private boolean closed;

    /** Creates a preallocated CPU simulation with a repository-stable seed. */
    public CpuParticleInstance(ParticleDefinition definition, RuntimeLimits limits, long seed) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.limits = Objects.requireNonNull(limits, "limits");
        if (definition.capacity() > limits.maxParticles()) {
            throw new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED,
                    "particle capacity exceeds runtime limit");
        }
        random = new StableRandom(seed);
        alive = new boolean[definition.capacity()];
        spawnId = new long[definition.capacity()];
        x = new float[definition.capacity()];
        y = new float[definition.capacity()];
        z = new float[definition.capacity()];
        velocityX = new float[definition.capacity()];
        velocityY = new float[definition.capacity()];
        velocityZ = new float[definition.capacity()];
        age = new float[definition.capacity()];
    }

    /** Updates the declared point-emitter anchor. */
    public void setAnchor(EffectAnchor anchor) {
        requireOpen();
        Objects.requireNonNull(anchor, "anchor");
        if (!definition.anchorName().equals(anchor.name())) {
            throw new EffectsException(EffectsException.Kind.INVALID_EFFECT,
                    "unknown particle anchor: " + anchor.name());
        }
        anchorX = anchor.x();
        anchorY = anchor.y();
        anchorZ = anchor.z();
        hasAnchor = true;
    }

    /** Emits an explicit bounded burst immediately at the current anchor. */
    public void burst(int count) {
        requireOpen();
        if (count < 0 || count > HARD_BURST_CAP) {
            throw new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED,
                    "particle burst count is outside hard bounds");
        }
        if (!hasAnchor && count > 0) {
            throw new EffectsException(EffectsException.Kind.INVALID_EFFECT,
                    "particle anchor is not available");
        }
        for (int index = 0; index < count; index++) {
            spawn();
        }
    }

    /** Advances only complete configured fixed steps. */
    public void advance(float deltaSeconds) {
        requireOpen();
        if (!Float.isFinite(deltaSeconds) || deltaSeconds < 0f) {
            throw new EffectsException(EffectsException.Kind.INVALID_EFFECT,
                    "deltaSeconds must be finite and nonnegative");
        }
        stepAccumulator += deltaSeconds;
        int steps = (int) (stepAccumulator / limits.fixedStepSeconds());
        if (steps > limits.maxCatchUpSteps()) {
            stepAccumulator -= deltaSeconds;
            throw new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED,
                    "particle advance exceeds catch-up capacity");
        }
        for (int index = 0; index < steps; index++) {
            simulateStep(limits.fixedStepSeconds());
            stepAccumulator -= limits.fixedStepSeconds();
        }
    }

    /** Copies alive particles into stable spawn-id order with pressure evidence. */
    public ParticleSnapshot snapshot() {
        requireOpen();
        List<ParticleSnapshot.Particle> particles = new ArrayList<>();
        for (int index = 0; index < alive.length; index++) {
            if (alive[index]) {
                float normalizedAge = Math.min(1f, age[index] / definition.lifetimeSeconds());
                ColorGradient.Color color = definition.color().sample(normalizedAge);
                particles.add(new ParticleSnapshot.Particle(spawnId[index], x[index], y[index],
                        z[index], velocityX[index], velocityY[index], velocityZ[index], age[index],
                        definition.lifetimeSeconds(),
                        Math.max(0f, definition.size().sample(normalizedAge)),
                        color.r(), color.g(), color.b(), color.a()));
            }
        }
        particles.sort(Comparator.comparingLong(ParticleSnapshot.Particle::spawnId));
        return new ParticleSnapshot(definition.name(), particles,
                droppedParticles, evictedParticles);
    }

    @Override public void close() {
        closed = true;
        java.util.Arrays.fill(alive, false);
    }

    private void simulateStep(float deltaSeconds) {
        for (int index = 0; index < alive.length; index++) {
            if (!alive[index]) {
                continue;
            }
            applyModifiers(index, deltaSeconds);
            x[index] += velocityX[index] * deltaSeconds;
            y[index] += velocityY[index] * deltaSeconds;
            z[index] += velocityZ[index] * deltaSeconds;
            age[index] += deltaSeconds;
            if (age[index] >= definition.lifetimeSeconds()) {
                alive[index] = false;
            }
        }
        if (hasAnchor) {
            emissionAccumulator += definition.emissionRate() * deltaSeconds;
            while (emissionAccumulator >= 1f) {
                emissionAccumulator -= 1f;
                spawn();
            }
        }
    }

    private void applyModifiers(int index, float deltaSeconds) {
        for (ParticleModifier modifier : definition.modifiers()) {
            if (modifier instanceof ParticleModifier.Gravity gravity) {
                velocityX[index] += gravity.x() * deltaSeconds;
                velocityY[index] += gravity.y() * deltaSeconds;
                velocityZ[index] += gravity.z() * deltaSeconds;
            } else if (modifier instanceof ParticleModifier.Drag drag) {
                float factor = Math.max(0f, 1f - drag.coefficient() * deltaSeconds);
                velocityX[index] *= factor;
                velocityY[index] *= factor;
                velocityZ[index] *= factor;
            } else if (modifier instanceof ParticleModifier.Turbulence turbulence) {
                float phase = spawnId[index] * 0.754877666f + age[index] * 3.1f;
                velocityX[index] += (float) Math.sin(phase) * turbulence.strength()
                        * deltaSeconds;
                velocityY[index] += (float) Math.cos(phase) * turbulence.strength()
                        * deltaSeconds;
            }
        }
    }

    private void spawn() {
        int slot = freeSlot();
        if (slot < 0 && definition.capacityPolicy() == ParticleCapacityPolicy.DROP_NEWEST) {
            droppedParticles++;
            nextSpawnId++;
            return;
        }
        if (slot < 0) {
            slot = oldestSlot();
            evictedParticles++;
        }
        float angle = random.nextFloat() * (float) (Math.PI * 2.0);
        alive[slot] = true;
        spawnId[slot] = nextSpawnId++;
        x[slot] = anchorX;
        y[slot] = anchorY;
        z[slot] = anchorZ;
        velocityX[slot] = (float) Math.cos(angle) * definition.initialSpeed();
        velocityY[slot] = (float) Math.sin(angle) * definition.initialSpeed();
        velocityZ[slot] = 0f;
        age[slot] = 0f;
    }

    private int freeSlot() {
        for (int index = 0; index < alive.length; index++) {
            if (!alive[index]) {
                return index;
            }
        }
        return -1;
    }

    private int oldestSlot() {
        int result = 0;
        for (int index = 1; index < alive.length; index++) {
            if (spawnId[index] < spawnId[result]) {
                result = index;
            }
        }
        return result;
    }

    private void requireOpen() {
        if (closed) {
            throw new EffectsException(EffectsException.Kind.INVALID_LIFECYCLE,
                    "CPU particle instance is closed");
        }
    }
}
