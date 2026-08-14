package io.github.teemuki8.libgdx.agent.effects.runtime;

import io.github.teemuki8.libgdx.agent.effects.core.ColorGradient;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.RuntimeLimits;
import io.github.teemuki8.libgdx.agent.effects.core.TrailDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.TrailSnapshot;
import io.github.teemuki8.libgdx.agent.effects.core.TrailUvMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Explicitly stepped, fixed-capacity trail sampler. */
public final class TrailInstance implements AutoCloseable {
    private final TrailDefinition definition;
    private final float[] x;
    private final float[] y;
    private final float[] z;
    private final float[] age;
    private int head;
    private int size;
    private long evictedPoints;
    private float accumulator;
    private float anchorX;
    private float anchorY;
    private float anchorZ;
    private boolean hasAnchor;
    private float lastX;
    private float lastY;
    private float lastZ;
    private boolean hasLastSample;
    private boolean closed;

    /** Creates a trail using only caller-supplied limits and inputs. */
    public TrailInstance(TrailDefinition definition, RuntimeLimits limits) {
        this.definition = Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(limits, "limits");
        if (definition.pointLimit() > limits.maxTrailPoints()) {
            throw new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED,
                    "trail point limit exceeds runtime capacity");
        }
        x = new float[definition.pointLimit()];
        y = new float[definition.pointLimit()];
        z = new float[definition.pointLimit()];
        age = new float[definition.pointLimit()];
    }

    /** Updates the named application-owned anchor without retaining the supplied value. */
    public void setAnchor(EffectAnchor anchor) {
        requireOpen();
        Objects.requireNonNull(anchor, "anchor");
        if (!definition.anchorName().equals(anchor.name())) {
            throw new EffectsException(EffectsException.Kind.INVALID_EFFECT,
                    "unknown trail anchor: " + anchor.name());
        }
        anchorX = anchor.x();
        anchorY = anchor.y();
        anchorZ = anchor.z();
        hasAnchor = true;
    }

    /** Advances point age and sampling using explicit application time. */
    public void advance(float deltaSeconds) {
        requireOpen();
        if (!Float.isFinite(deltaSeconds) || deltaSeconds < 0f) {
            throw new EffectsException(EffectsException.Kind.INVALID_EFFECT,
                    "deltaSeconds must be finite and nonnegative");
        }
        ageAndExpire(deltaSeconds);
        if (!hasAnchor) {
            return;
        }
        accumulator += deltaSeconds;
        while (accumulator >= definition.sampleIntervalSeconds()) {
            accumulator -= definition.sampleIntervalSeconds();
            sampleCurrentAnchor();
        }
    }

    /** Copies the current bounded state into an immutable oldest-to-newest snapshot. */
    public TrailSnapshot snapshot() {
        requireOpen();
        float totalDistance = totalDistance();
        float traversed = 0f;
        List<TrailSnapshot.Point> points = new ArrayList<>(size);
        for (int offset = 0; offset < size; offset++) {
            int index = physicalIndex(offset);
            if (offset > 0) {
                int previous = physicalIndex(offset - 1);
                traversed += distance(x[previous], y[previous], z[previous],
                        x[index], y[index], z[index]);
            }
            float normalizedAge = Math.min(1f, age[index] / definition.lifetimeSeconds());
            float width = Math.max(0f, definition.width().sample(normalizedAge));
            ColorGradient.Color color = definition.color().sample(normalizedAge);
            float u = definition.uvMode() == TrailUvMode.STRETCH
                    ? (totalDistance == 0f ? 0f : traversed / totalDistance)
                    : traversed;
            points.add(new TrailSnapshot.Point(x[index], y[index], z[index], age[index],
                    width, color.r(), color.g(), color.b(), color.a(), u));
        }
        return new TrailSnapshot(definition.name(), points, evictedPoints);
    }

    @Override public void close() {
        closed = true;
        size = 0;
    }

    private void ageAndExpire(float deltaSeconds) {
        for (int offset = 0; offset < size; offset++) {
            int index = physicalIndex(offset);
            age[index] += deltaSeconds;
        }
        while (size > 0 && age[head] >= definition.lifetimeSeconds()) {
            head = (head + 1) % x.length;
            size--;
        }
    }

    private void sampleCurrentAnchor() {
        if (hasLastSample && distance(lastX, lastY, lastZ, anchorX, anchorY, anchorZ)
                < definition.minimumSampleDistance()) {
            return;
        }
        int index;
        if (size == x.length) {
            index = head;
            head = (head + 1) % x.length;
            evictedPoints++;
        } else {
            index = physicalIndex(size);
            size++;
        }
        x[index] = anchorX;
        y[index] = anchorY;
        z[index] = anchorZ;
        age[index] = 0f;
        lastX = anchorX;
        lastY = anchorY;
        lastZ = anchorZ;
        hasLastSample = true;
    }

    private float totalDistance() {
        float result = 0f;
        for (int offset = 1; offset < size; offset++) {
            int previous = physicalIndex(offset - 1);
            int current = physicalIndex(offset);
            result += distance(x[previous], y[previous], z[previous],
                    x[current], y[current], z[current]);
        }
        return result;
    }

    private int physicalIndex(int offset) {
        return (head + offset) % x.length;
    }

    private static float distance(float ax, float ay, float az,
            float bx, float by, float bz) {
        float dx = bx - ax;
        float dy = by - ay;
        float dz = bz - az;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private void requireOpen() {
        if (closed) {
            throw new EffectsException(EffectsException.Kind.INVALID_LIFECYCLE,
                    "trail instance is closed");
        }
    }
}
