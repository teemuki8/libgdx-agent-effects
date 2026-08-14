package io.github.teemuki8.libgdx.agent.effects.runtime;

import io.github.teemuki8.libgdx.agent.effects.core.ColorGradient;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.LightningDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.LightningSnapshot;
import io.github.teemuki8.libgdx.agent.effects.core.RuntimeLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Explicitly regenerated bounded seeded-lightning instance. */
public final class LightningInstance implements AutoCloseable {
    private final LightningDefinition definition;
    private final StableRandom random;
    private final float[] pointX;
    private final float[] pointY;
    private final float[] pointZ;
    private final float[] branchEndX;
    private final float[] branchEndY;
    private final float[] branchEndZ;
    private float startX;
    private float startY;
    private float startZ;
    private float endX;
    private float endY;
    private float endZ;
    private float ageSeconds;
    private float regenerationAccumulator;
    private long generation;
    private boolean hasStart;
    private boolean hasEnd;
    private boolean generated;
    private boolean closed;

    /** Creates a lightning instance with the repository-stable seed sequence. */
    public LightningInstance(LightningDefinition definition, RuntimeLimits limits, long seed) {
        this.definition = Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(limits, "limits");
        if (definition.segmentLimit() > limits.maxBeamSegments()
                || definition.branchLimit() > limits.maxLightningBranches()) {
            throw new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED,
                    "lightning definition exceeds runtime capacity");
        }
        random = new StableRandom(seed);
        pointX = new float[definition.segmentLimit() + 1];
        pointY = new float[definition.segmentLimit() + 1];
        pointZ = new float[definition.segmentLimit() + 1];
        branchEndX = new float[definition.branchLimit()];
        branchEndY = new float[definition.branchLimit()];
        branchEndZ = new float[definition.branchLimit()];
    }

    /** Updates either declared endpoint and regenerates once both are available. */
    public void setAnchor(EffectAnchor anchor) {
        requireOpen();
        Objects.requireNonNull(anchor, "anchor");
        if (definition.startAnchor().equals(anchor.name())) {
            startX = anchor.x();
            startY = anchor.y();
            startZ = anchor.z();
            hasStart = true;
        } else if (definition.endAnchor().equals(anchor.name())) {
            endX = anchor.x();
            endY = anchor.y();
            endZ = anchor.z();
            hasEnd = true;
        } else {
            throw new EffectsException(EffectsException.Kind.INVALID_EFFECT,
                    "unknown lightning anchor: " + anchor.name());
        }
        if (hasStart && hasEnd && !generated) {
            regenerate();
        }
    }

    /** Advances age and performs only explicitly timed regeneration. */
    public void advance(float deltaSeconds) {
        requireOpen();
        if (!Float.isFinite(deltaSeconds) || deltaSeconds < 0f) {
            throw new EffectsException(EffectsException.Kind.INVALID_EFFECT,
                    "deltaSeconds must be finite and nonnegative");
        }
        ageSeconds += deltaSeconds;
        regenerationAccumulator += deltaSeconds;
        while (hasStart && hasEnd
                && regenerationAccumulator >= definition.regenerationIntervalSeconds()) {
            regenerationAccumulator -= definition.regenerationIntervalSeconds();
            generation++;
            regenerate();
        }
    }

    /** Returns stable primary segments followed by stable branch segments. */
    public LightningSnapshot snapshot() {
        requireOpen();
        if (!generated || sameEndpoints()) {
            return new LightningSnapshot(definition.name(), List.of(), generation, ageSeconds);
        }
        float normalizedAge = Math.min(1f, ageSeconds / definition.lifetimeSeconds());
        float width = Math.max(0f, definition.width().sample(normalizedAge));
        ColorGradient.Color color = definition.color().sample(normalizedAge);
        List<LightningSnapshot.Segment> segments = new ArrayList<>(
                definition.segmentLimit() + definition.branchLimit());
        for (int index = 0; index < definition.segmentLimit(); index++) {
            segments.add(segment(pointX[index], pointY[index], pointZ[index],
                    pointX[index + 1], pointY[index + 1], pointZ[index + 1], width,
                    color, false));
        }
        for (int index = 0; index < definition.branchLimit(); index++) {
            int source = 1 + index % Math.max(1, definition.segmentLimit() - 1);
            segments.add(segment(pointX[source], pointY[source], pointZ[source],
                    branchEndX[index], branchEndY[index], branchEndZ[index], width * 0.7f,
                    color, true));
        }
        return new LightningSnapshot(definition.name(), segments, generation, ageSeconds);
    }

    @Override public void close() {
        closed = true;
    }

    private void regenerate() {
        float dx = endX - startX;
        float dy = endY - startY;
        float dz = endZ - startZ;
        float planarLength = (float) Math.sqrt(dx * dx + dy * dy);
        float normalX = planarLength == 0f ? 0f : -dy / planarLength;
        float normalY = planarLength == 0f ? 1f : dx / planarLength;
        for (int index = 0; index <= definition.segmentLimit(); index++) {
            float alpha = index / (float) definition.segmentLimit();
            float envelope = 1f - Math.abs(alpha * 2f - 1f);
            float offset = index == 0 || index == definition.segmentLimit() ? 0f
                    : (random.nextFloat() * 2f - 1f) * definition.roughness() * envelope;
            pointX[index] = lerp(startX, endX, alpha) + normalX * offset;
            pointY[index] = lerp(startY, endY, alpha) + normalY * offset;
            pointZ[index] = lerp(startZ, endZ, alpha);
        }
        for (int index = 0; index < definition.branchLimit(); index++) {
            int source = 1 + index % Math.max(1, definition.segmentLimit() - 1);
            float signed = random.nextFloat() < 0.5f ? -1f : 1f;
            float length = definition.roughness() * (0.5f + random.nextFloat());
            branchEndX[index] = pointX[source] + normalX * signed * length;
            branchEndY[index] = pointY[source] + normalY * signed * length;
            branchEndZ[index] = pointZ[source];
        }
        generated = true;
    }

    private static LightningSnapshot.Segment segment(float startX, float startY, float startZ,
            float endX, float endY, float endZ, float width, ColorGradient.Color color,
            boolean branch) {
        return new LightningSnapshot.Segment(startX, startY, startZ, endX, endY, endZ,
                width, color.r(), color.g(), color.b(), color.a(), branch);
    }

    private boolean sameEndpoints() {
        return startX == endX && startY == endY && startZ == endZ;
    }

    private static float lerp(float first, float second, float alpha) {
        return first + (second - first) * alpha;
    }

    private void requireOpen() {
        if (closed) {
            throw new EffectsException(EffectsException.Kind.INVALID_LIFECYCLE,
                    "lightning instance is closed");
        }
    }
}
