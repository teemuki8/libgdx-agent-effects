package io.github.teemuki8.libgdx.agent.effects.runtime;

import io.github.teemuki8.libgdx.agent.effects.core.DecalDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.DecalPlacement;
import io.github.teemuki8.libgdx.agent.effects.core.DecalSnapshot;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.RuntimeLimits;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Explicit fixed-capacity decal lifetime state. */
public final class DecalInstance implements AutoCloseable {
    private final DecalDefinition definition;
    private final boolean[] alive;
    private final long[] spawnId;
    private final long[] order;
    private final float[] x;
    private final float[] y;
    private final float[] z;
    private final float[] normalX;
    private final float[] normalY;
    private final float[] normalZ;
    private final float[] rotation;
    private final float[] r;
    private final float[] g;
    private final float[] b;
    private final float[] a;
    private final float[] age;
    private long nextSpawnId;
    private long droppedDecals;
    private boolean closed;

    /** Creates preallocated decal state within the supplied configured capacity. */
    public DecalInstance(DecalDefinition definition, RuntimeLimits limits) {
        this.definition = Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(limits, "limits");
        if (definition.capacity() > limits.maxDecals()) {
            throw new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED,
                    "decal capacity exceeds runtime limit");
        }
        int capacity = definition.capacity();
        alive = new boolean[capacity];
        spawnId = new long[capacity];
        order = new long[capacity];
        x = new float[capacity];
        y = new float[capacity];
        z = new float[capacity];
        normalX = new float[capacity];
        normalY = new float[capacity];
        normalZ = new float[capacity];
        rotation = new float[capacity];
        r = new float[capacity];
        g = new float[capacity];
        b = new float[capacity];
        a = new float[capacity];
        age = new float[capacity];
    }

    /** Adds a caller-resolved placement or records a deterministic dropped-newest event. */
    public long spawn(DecalPlacement placement) {
        requireOpen();
        Objects.requireNonNull(placement, "placement");
        long id = nextSpawnId++;
        int slot = freeSlot();
        if (slot < 0) {
            droppedDecals++;
            return id;
        }
        float length = (float) Math.sqrt(placement.normalX() * placement.normalX()
                + placement.normalY() * placement.normalY()
                + placement.normalZ() * placement.normalZ());
        alive[slot] = true;
        spawnId[slot] = id;
        order[slot] = placement.order();
        x[slot] = placement.x();
        y[slot] = placement.y();
        z[slot] = placement.z();
        normalX[slot] = placement.normalX() / length;
        normalY[slot] = placement.normalY() / length;
        normalZ[slot] = placement.normalZ() / length;
        rotation[slot] = placement.rotationDegrees();
        r[slot] = placement.r();
        g[slot] = placement.g();
        b[slot] = placement.b();
        a[slot] = placement.a();
        age[slot] = 0f;
        return id;
    }

    /** Ages and expires decals using explicit caller time. */
    public void advance(float deltaSeconds) {
        requireOpen();
        if (!Float.isFinite(deltaSeconds) || deltaSeconds < 0f) {
            throw new EffectsException(EffectsException.Kind.INVALID_EFFECT,
                    "deltaSeconds must be finite and nonnegative");
        }
        for (int index = 0; index < alive.length; index++) {
            if (alive[index]) {
                age[index] += deltaSeconds;
                if (age[index] >= definition.lifetimeSeconds()) {
                    alive[index] = false;
                }
            }
        }
    }

    /** Copies alive decals into declared render order with drop evidence. */
    public DecalSnapshot snapshot() {
        requireOpen();
        List<DecalSnapshot.Decal> decals = new ArrayList<>();
        for (int index = 0; index < alive.length; index++) {
            if (alive[index]) {
                float alpha = a[index] * fade(index);
                decals.add(new DecalSnapshot.Decal(spawnId[index], order[index],
                        x[index], y[index], z[index], normalX[index], normalY[index],
                        normalZ[index], rotation[index], definition.width(), definition.height(),
                        age[index], r[index], g[index], b[index], alpha));
            }
        }
        decals.sort(Comparator.comparingLong(DecalSnapshot.Decal::order)
                .thenComparingLong(DecalSnapshot.Decal::spawnId));
        return new DecalSnapshot(definition.name(), decals, droppedDecals);
    }

    @Override public void close() {
        closed = true;
        java.util.Arrays.fill(alive, false);
    }

    private float fade(int index) {
        if (definition.fadeOutSeconds() == 0f) {
            return 1f;
        }
        float remaining = definition.lifetimeSeconds() - age[index];
        return Math.min(1f, remaining / definition.fadeOutSeconds());
    }

    private int freeSlot() {
        for (int index = 0; index < alive.length; index++) {
            if (!alive[index]) {
                return index;
            }
        }
        return -1;
    }

    private void requireOpen() {
        if (closed) {
            throw new EffectsException(EffectsException.Kind.INVALID_LIFECYCLE,
                    "decal instance is closed");
        }
    }
}
