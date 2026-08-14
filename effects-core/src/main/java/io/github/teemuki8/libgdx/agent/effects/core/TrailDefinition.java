package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.Objects;

/** Immutable definition for one bounded sampled trail. */
public record TrailDefinition(
        String name,
        String anchorName,
        Material2dDefinition material,
        FloatCurve width,
        ColorGradient color,
        float sampleIntervalSeconds,
        float minimumSampleDistance,
        int pointLimit,
        float lifetimeSeconds,
        TrailJoin join,
        TrailCap cap,
        TrailUvMode uvMode,
        float miterLimit) implements EffectDefinition {

    private static final int HARD_POINT_CAP = 1024 * 1024;

    public TrailDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(anchorName, "anchorName");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(width, "width");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(join, "join");
        Objects.requireNonNull(cap, "cap");
        Objects.requireNonNull(uvMode, "uvMode");
        if (name.isBlank() || anchorName.isBlank()) {
            throw new IllegalArgumentException("trail names must not be blank");
        }
        requirePositiveFinite(sampleIntervalSeconds, "sampleIntervalSeconds");
        if (!Float.isFinite(minimumSampleDistance) || minimumSampleDistance < 0f) {
            throw new IllegalArgumentException("minimumSampleDistance must be finite and nonnegative");
        }
        if (pointLimit < 2 || pointLimit > HARD_POINT_CAP) {
            throw new IllegalArgumentException("pointLimit is outside hard bounds");
        }
        requirePositiveFinite(lifetimeSeconds, "lifetimeSeconds");
        if (!Float.isFinite(miterLimit) || miterLimit < 1f || miterLimit > 32f) {
            throw new IllegalArgumentException("miterLimit must be within [1, 32]");
        }
    }

    @Override public TrailDefinition validate(EffectsLimits limits) {
        Objects.requireNonNull(limits, "limits");
        material.validate(limits);
        width.validate(limits);
        color.validate(limits);
        if (name.length() > limits.maxShaderSourceChars()
                || anchorName.length() > limits.maxShaderSourceChars()) {
            throw new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED,
                    "trail text exceeds limits");
        }
        if (pointLimit > limits.maxTrailPoints()
                || 1 + width.stops().size() + color.stops().size()
                        > limits.maxDefinitionNodes()) {
            throw new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED,
                    "trail definition exceeds configured limits");
        }
        return this;
    }

    private static void requirePositiveFinite(float value, String name) {
        if (!Float.isFinite(value) || value <= 0f) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }
}
