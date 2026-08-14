package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.Objects;

/** Closed effect-family vocabulary shared by core and protocol consumers. */
public enum EffectFamily {
    LEGACY_SHADER,
    MATERIAL_2D,
    MATERIAL_3D,
    TRAIL,
    BEAM,
    LIGHTNING,
    PARTICLE,
    DECAL,
    DISTORTION,
    POST_PROCESS_GRAPH;

    /** Derives the family represented by a closed general-effect definition. */
    public static EffectFamily from(EffectDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        return switch (definition) {
            case Material2dDefinition ignored -> MATERIAL_2D;
            case Material3dDefinition ignored -> MATERIAL_3D;
            case TrailDefinition ignored -> TRAIL;
            case BeamDefinition ignored -> BEAM;
            case LightningDefinition ignored -> LIGHTNING;
            case ParticleDefinition ignored -> PARTICLE;
            case DecalDefinition ignored -> DECAL;
            case DistortionFieldDefinition ignored -> DISTORTION;
            case PostProcessGraphDefinition ignored -> POST_PROCESS_GRAPH;
        };
    }
}
