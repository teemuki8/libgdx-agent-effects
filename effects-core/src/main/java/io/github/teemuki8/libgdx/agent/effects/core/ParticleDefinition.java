package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.List;
import java.util.Objects;

/** Immutable bounded point-emitter particle definition. */
public record ParticleDefinition(String name, String anchorName, Material2dDefinition material,
        int capacity, float emissionRate, float lifetimeSeconds, float initialSpeed,
        FloatCurve size, ColorGradient color, List<ParticleModifier> modifiers,
        ParticleCapacityPolicy capacityPolicy) implements EffectDefinition {
    private static final int HARD_CAPACITY = 1024 * 1024;
    private static final int HARD_MODIFIERS = 64;

    public ParticleDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(anchorName, "anchorName");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(color, "color");
        modifiers = List.copyOf(Objects.requireNonNull(modifiers, "modifiers"));
        Objects.requireNonNull(capacityPolicy, "capacityPolicy");
        if (name.isBlank() || anchorName.isBlank()) {
            throw new IllegalArgumentException("particle names must not be blank");
        }
        if (capacity <= 0 || capacity > HARD_CAPACITY || modifiers.size() > HARD_MODIFIERS) {
            throw new IllegalArgumentException("particle counts are outside hard bounds");
        }
        requireNonnegativeFinite(emissionRate, "emissionRate");
        requirePositiveFinite(lifetimeSeconds, "lifetimeSeconds");
        requireNonnegativeFinite(initialSpeed, "initialSpeed");
    }

    @Override public ParticleDefinition validate(EffectsLimits limits) {
        Objects.requireNonNull(limits, "limits");
        material.validate(limits);
        size.validate(limits);
        color.validate(limits);
        if (capacity > limits.maxParticles()
                || 1 + modifiers.size() + size.stops().size() + color.stops().size()
                        > limits.maxDefinitionNodes()) {
            throw new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED,
                    "particle definition exceeds configured limits");
        }
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
