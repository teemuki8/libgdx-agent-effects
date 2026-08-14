package io.github.teemuki8.libgdx.agent.effects.libgdx;

import io.github.teemuki8.libgdx.agent.effects.core.BlendMode;
import io.github.teemuki8.libgdx.agent.effects.core.ColorGradient;
import io.github.teemuki8.libgdx.agent.effects.core.FloatCurve;
import io.github.teemuki8.libgdx.agent.effects.core.Material2dDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleCapacityPolicy;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleModifier;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderSource;
import java.util.List;

final class ParticleTestDefinitions {
    private ParticleTestDefinitions() {}

    static ParticleDefinition supported() {
        return definition(List.of(new ParticleModifier.Gravity(0f, -1f, 0f)));
    }

    static ParticleDefinition unsupportedGpu() {
        return definition(List.of(new ParticleModifier.Turbulence(0.5f)));
    }

    private static ParticleDefinition definition(List<ParticleModifier> modifiers) {
        Material2dDefinition material = new Material2dDefinition("particles",
                new ShaderSource("void main(){}", "void main(){}"),
                BlendMode.ADDITIVE, List.of(), List.of());
        return new ParticleDefinition("particles", "emitter", material, 16, 0f, 1f, 1f,
                new FloatCurve(List.of(new FloatCurve.Stop(0f, 0.1f))),
                new ColorGradient(List.of(new ColorGradient.Stop(0f, 1f, 1f, 1f, 1f))),
                modifiers, ParticleCapacityPolicy.DROP_NEWEST);
    }
}
