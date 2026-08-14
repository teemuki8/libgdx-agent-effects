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
                ParticleFallbackPolicy.FALLBACK_CPU, 4096);
        assertEquals(ParticleBackendEvidence.Backend.CPU, gl2.backend());

        ParticleBackendEvidence gl3 = ParticleBackendSelector.select(
                ParticleTestDefinitions.supported(), capabilities(3),
                ParticleFallbackPolicy.FALLBACK_CPU, 4096);
        assertEquals(ParticleBackendEvidence.Backend.GPU_GL3, gl3.backend());
        assertEquals(false, gl3.approximate());
    }

    @Test
    void unsupportedModifierFallsBackOrFailsByExplicitPolicy() {
        ParticleBackendEvidence fallback = ParticleBackendSelector.select(
                ParticleTestDefinitions.unsupportedGpu(), capabilities(3),
                ParticleFallbackPolicy.FALLBACK_CPU, 4096);
        assertEquals(ParticleBackendEvidence.Backend.CPU, fallback.backend());
        assertEquals(true, fallback.approximate());
        assertThrows(EffectsException.class, () -> ParticleBackendSelector.select(
                ParticleTestDefinitions.unsupportedGpu(), capabilities(3),
                ParticleFallbackPolicy.REQUIRE_GPU, 4096));
    }

    private static EffectCapabilities capabilities(int major) {
        return new EffectCapabilities(major, 0, 4096, true);
    }
}
