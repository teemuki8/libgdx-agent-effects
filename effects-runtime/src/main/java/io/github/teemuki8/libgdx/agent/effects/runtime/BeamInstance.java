package io.github.teemuki8.libgdx.agent.effects.runtime;

import io.github.teemuki8.libgdx.agent.effects.core.BeamDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.BeamSnapshot;
import io.github.teemuki8.libgdx.agent.effects.core.ColorGradient;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.RuntimeLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Explicit endpoint-driven straight beam instance. */
public final class BeamInstance implements AutoCloseable {
    private final BeamDefinition definition;
    private float startX;
    private float startY;
    private float startZ;
    private float endX;
    private float endY;
    private float endZ;
    private float ageSeconds;
    private boolean hasStart;
    private boolean hasEnd;
    private boolean closed;

    /** Creates a beam constrained by the supplied runtime segment capacity. */
    public BeamInstance(BeamDefinition definition, RuntimeLimits limits) {
        this.definition = Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(limits, "limits");
        if (definition.segmentLimit() > limits.maxBeamSegments()) {
            throw new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED,
                    "beam segment limit exceeds runtime capacity");
        }
    }

    /** Updates either declared endpoint anchor. */
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
                    "unknown beam anchor: " + anchor.name());
        }
    }

    /** Advances the beam's explicitly supplied visual age. */
    public void advance(float deltaSeconds) {
        requireOpen();
        if (!Float.isFinite(deltaSeconds) || deltaSeconds < 0f) {
            throw new EffectsException(EffectsException.Kind.INVALID_EFFECT,
                    "deltaSeconds must be finite and nonnegative");
        }
        ageSeconds += deltaSeconds;
    }

    /** Returns ordered straight segments joining the current endpoints. */
    public BeamSnapshot snapshot() {
        requireOpen();
        if (!hasStart || !hasEnd || sameEndpoints()) {
            return new BeamSnapshot(definition.name(), List.of(), ageSeconds);
        }
        float normalizedAge = Math.min(1f, ageSeconds / definition.lifetimeSeconds());
        float width = Math.max(0f, definition.width().sample(normalizedAge));
        ColorGradient.Color color = definition.color().sample(normalizedAge);
        List<BeamSnapshot.Segment> segments = new ArrayList<>(definition.segmentLimit());
        for (int index = 0; index < definition.segmentLimit(); index++) {
            float start = index / (float) definition.segmentLimit();
            float end = (index + 1f) / definition.segmentLimit();
            segments.add(new BeamSnapshot.Segment(
                    lerp(startX, endX, start), lerp(startY, endY, start),
                    lerp(startZ, endZ, start), lerp(startX, endX, end),
                    lerp(startY, endY, end), lerp(startZ, endZ, end), width,
                    color.r(), color.g(), color.b(), color.a()));
        }
        return new BeamSnapshot(definition.name(), segments, ageSeconds);
    }

    @Override public void close() {
        closed = true;
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
                    "beam instance is closed");
        }
    }
}
