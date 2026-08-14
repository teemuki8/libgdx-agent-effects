package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.Objects;

/** Immutable bounded decal style usable by either the 2D or 3D adapter. */
public record DecalDefinition(String name, EffectDefinition material, int capacity,
        float lifetimeSeconds, float fadeOutSeconds, float width, float height)
        implements EffectDefinition {
    private static final int HARD_CAPACITY = 1024 * 1024;

    public DecalDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(material, "material");
        if (!(material instanceof Material2dDefinition)
                && !(material instanceof Material3dDefinition)) {
            throw new IllegalArgumentException("decal material must be 2D or 3D");
        }
        if (name.isBlank() || capacity <= 0 || capacity > HARD_CAPACITY) {
            throw new IllegalArgumentException("decal identity or capacity is outside bounds");
        }
        requirePositiveFinite(lifetimeSeconds, "lifetimeSeconds");
        if (!Float.isFinite(fadeOutSeconds) || fadeOutSeconds < 0f
                || fadeOutSeconds > lifetimeSeconds) {
            throw new IllegalArgumentException("fadeOutSeconds is outside lifetime");
        }
        requirePositiveFinite(width, "width");
        requirePositiveFinite(height, "height");
    }

    @Override public DecalDefinition validate(EffectsLimits limits) {
        Objects.requireNonNull(limits, "limits");
        material.validate(limits);
        if (capacity > limits.maxDecals()) {
            throw new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED,
                    "decal capacity exceeds configured limit");
        }
        return this;
    }

    private static void requirePositiveFinite(float value, String name) {
        if (!Float.isFinite(value) || value <= 0f) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }
}
