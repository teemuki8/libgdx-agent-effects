package io.github.teemuki8.libgdx.agent.effects.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.teemuki8.libgdx.agent.effects.core.BlendMode;
import io.github.teemuki8.libgdx.agent.effects.core.ColorGradient;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCapabilities;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.FloatCurve;
import io.github.teemuki8.libgdx.agent.effects.core.Material2dDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleBackendEvidence;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleCapacityPolicy;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleModifier;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderSource;
import io.github.teemuki8.libgdx.agent.effects.libgdx.GpuParticleInstance;
import java.util.List;
import org.junit.jupiter.api.Test;

class GpuParticleFixtureTest {
    @Test
    void realGl3FixtureReportsGpuBackendAndSwapsState() throws Exception {
        GdxFixtureHost.runGl3(() -> {
            try (GpuParticleInstance particles = new GpuParticleInstance(definition(),
                    EffectsLimits.developmentDefaults(),
                    new EffectCapabilities(3, 0, 4096, true), 7L)) {
                particles.setAnchor("emitter", 0f, 0f, 0f);
                particles.burst(4);
                particles.advance(1f / 60f);
                assertEquals(ParticleBackendEvidence.Backend.GPU_GL3,
                        particles.backendEvidence().backend());
                assertEquals(1L, particles.generation());
                assertEquals(4, particles.snapshot().particles().size());
                assertTrue(particles.snapshot().particles().stream()
                        .anyMatch(particle -> particle.x() != 0f || particle.y() != 0f));
            }
        });
    }

    private static ParticleDefinition definition() {
        Material2dDefinition material = new Material2dDefinition("fixture-particles",
                new ShaderSource("void main(){}", "void main(){}"),
                BlendMode.ADDITIVE, List.of(), List.of());
        return new ParticleDefinition("fixture-particles", "emitter", material,
                16, 0f, 1f, 1f,
                new FloatCurve(List.of(new FloatCurve.Stop(0f, 0.1f))),
                new ColorGradient(List.of(new ColorGradient.Stop(0f, 1f, 1f, 1f, 1f))),
                List.of(new ParticleModifier.Gravity(0f, -1f, 0f)),
                ParticleCapacityPolicy.DROP_NEWEST);
    }
}
