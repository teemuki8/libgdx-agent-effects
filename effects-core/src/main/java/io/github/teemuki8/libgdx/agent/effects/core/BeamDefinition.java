package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.Objects;

/** Immutable endpoint-driven segmented beam definition. */
public record BeamDefinition(String name, String startAnchor, String endAnchor,
        Material2dDefinition material, FloatCurve width, ColorGradient color,
        int segmentLimit, float lifetimeSeconds) implements EffectDefinition {
    private static final int HARD_SEGMENT_CAP = 1024 * 1024;

    public BeamDefinition {
        requireText(name, "name");
        requireText(startAnchor, "startAnchor");
        requireText(endAnchor, "endAnchor");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(width, "width");
        Objects.requireNonNull(color, "color");
        if (startAnchor.equals(endAnchor)) {
            throw new IllegalArgumentException("beam anchors must be distinct");
        }
        if (segmentLimit <= 0 || segmentLimit > HARD_SEGMENT_CAP) {
            throw new IllegalArgumentException("segmentLimit is outside hard bounds");
        }
        if (!Float.isFinite(lifetimeSeconds) || lifetimeSeconds <= 0f) {
            throw new IllegalArgumentException("lifetimeSeconds must be finite and positive");
        }
    }

    @Override public BeamDefinition validate(EffectsLimits limits) {
        Objects.requireNonNull(limits, "limits");
        material.validate(limits);
        return this;
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
