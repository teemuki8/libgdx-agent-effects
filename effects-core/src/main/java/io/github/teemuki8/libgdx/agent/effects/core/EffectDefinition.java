package io.github.teemuki8.libgdx.agent.effects.core;

/** Closed immutable definition shared by all effect families. */
public sealed interface EffectDefinition permits Material2dDefinition, Material3dDefinition,
        TrailDefinition, BeamDefinition, LightningDefinition {

    /** Stable non-secret application name. */
    String name();

    /** Validates this definition against configured effect limits. */
    EffectDefinition validate(EffectsLimits limits);
}
