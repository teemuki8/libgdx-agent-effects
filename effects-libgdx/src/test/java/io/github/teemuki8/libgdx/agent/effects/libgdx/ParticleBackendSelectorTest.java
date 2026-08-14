package io.github.teemuki8.libgdx.agent.effects.libgdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.teemuki8.libgdx.agent.effects.core.EffectCapabilities;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleBackendEvidence;
import org.junit.jupiter.api.Test;

class ParticleBackendSelectorTest {

    @Test
    void gl2AlwaysSelectsCpuAndGl3SelectsSupportedGpu() {
        ParticleBackendEvidence gl2 = ParticleBackendSelector.select(
                ParticleTestDefinitions.supported(), capabilities(2),
                ParticleFallbackPolicy.FALLBACK_CPU, 1);
        assertEquals(ParticleBackendEvidence.Backend.CPU, gl2.backend());

        ParticleBackendEvidence gl3 = ParticleBackendSelector.select(
                ParticleTestDefinitions.supported(), capabilities(3, 2),
                ParticleFallbackPolicy.FALLBACK_CPU, 4096);
        assertEquals(ParticleBackendEvidence.Backend.GPU_GL3, gl3.backend());
        assertEquals(false, gl3.approximate());
    }

    @Test
    void unsupportedModifierFallsBackOrFailsByExplicitPolicy() {
        ParticleBackendEvidence fallback = ParticleBackendSelector.select(
                ParticleTestDefinitions.unsupportedGpu(), capabilities(3, 2),
                ParticleFallbackPolicy.FALLBACK_CPU, 4096);
        assertEquals(ParticleBackendEvidence.Backend.CPU, fallback.backend());
        assertEquals(true, fallback.approximate());
        assertThrows(EffectsException.class, () -> ParticleBackendSelector.select(
                ParticleTestDefinitions.unsupportedGpu(), capabilities(3, 2),
                ParticleFallbackPolicy.REQUIRE_GPU, 4096));
    }

    @Test
    void desktopGl30AndGles3DoNotSelectDesktopGlsl150Backend() {
        assertEquals(ParticleBackendEvidence.Backend.CPU, ParticleBackendSelector.select(
                ParticleTestDefinitions.supported(), capabilities(3, 0),
                ParticleFallbackPolicy.FALLBACK_CPU, 4096).backend());
        EffectCapabilities gles3 = new EffectCapabilities(3, 2, 4096, true,
                EffectCapabilities.Profile.OPENGL_ES);
        assertEquals(ParticleBackendEvidence.Backend.CPU, ParticleBackendSelector.select(
                ParticleTestDefinitions.supported(), gles3,
                ParticleFallbackPolicy.FALLBACK_CPU, 4096).backend());
        assertEquals(ParticleBackendEvidence.Backend.CPU, ParticleBackendSelector.select(
                ParticleTestDefinitions.supported(),
                new EffectCapabilities(4, 6, 4096, true),
                ParticleFallbackPolicy.FALLBACK_CPU, 4096).backend());
    }

    private static EffectCapabilities capabilities(int major) {
        return capabilities(major, 0);
    }

    private static EffectCapabilities capabilities(int major, int minor) {
        return new EffectCapabilities(major, minor, 4096, true,
                EffectCapabilities.Profile.DESKTOP_OPENGL);
    }
}
