package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.Objects;

/** Immutable bounded seeded-lightning definition. */
public record LightningDefinition(String name, String startAnchor, String endAnchor,
        Material2dDefinition material, FloatCurve width, ColorGradient color,
        int segmentLimit, int branchLimit, float roughness,
        float regenerationIntervalSeconds, float lifetimeSeconds) implements EffectDefinition {
    private static final int HARD_CAP = 1024 * 1024;

    public LightningDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(startAnchor, "startAnchor");
        Objects.requireNonNull(endAnchor, "endAnchor");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(width, "width");
        Objects.requireNonNull(color, "color");
        if (name.isBlank() || startAnchor.isBlank() || endAnchor.isBlank()
                || startAnchor.equals(endAnchor)) {
            throw new IllegalArgumentException("invalid lightning names");
        }
        if (segmentLimit <= 0 || segmentLimit > HARD_CAP
                || branchLimit < 0 || branchLimit > HARD_CAP) {
            throw new IllegalArgumentException("lightning counts are outside hard bounds");
        }
        requireNonnegativeFinite(roughness, "roughness");
        requirePositiveFinite(regenerationIntervalSeconds, "regenerationIntervalSeconds");
        requirePositiveFinite(lifetimeSeconds, "lifetimeSeconds");
    }

    @Override public LightningDefinition validate(EffectsLimits limits) {
        Objects.requireNonNull(limits, "limits");
        material.validate(limits);
        return this;
    }

    private static void requirePositiveFinite(float value, String name) {
        if (!Float.isFinite(value) || value <= 0f) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private static void requireNonnegativeFinite(float value, String name) {
        if (!Float.isFinite(value) || value < 0f) {
            throw new IllegalArgumentException(name + " must be finite and nonnegative");
        }
    }
}
