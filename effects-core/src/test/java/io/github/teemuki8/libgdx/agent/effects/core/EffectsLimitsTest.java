package io.github.teemuki8.libgdx.agent.effects.core;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EffectsLimitsTest {
    @Test
    void developmentDefaultsAreWithinHardCaps() {
        EffectsLimits l = EffectsLimits.developmentDefaults();
        assertTrue(l.maxShaderSourceChars() > 0);
        assertTrue(l.maxUniformCount() > 0);
        assertTrue(l.maxRenderWidth() <= 8192);
        assertTrue(l.maxRenderHeight() <= 8192);
        assertTrue(l.maxCurveStops() > 0);
        assertTrue(l.maxGradientStops() > 0);
        assertTrue(l.maxDefinitionNodes() > 0);
        assertTrue(l.maxParticles() > 0);
        assertTrue(l.maxFramebufferPixels() > 0);
    }

    @Test
    void rejectsNonPositiveLimits() {
        assertThrows(IllegalArgumentException.class, () ->
            new EffectsLimits(0, 64, 16, 4096 * 4096, 16 * 1024, 2048, 2048, 32));
    }

    @Test
    void rejectsNonPositiveGeneralEffectLimits() {
        assertThrows(IllegalArgumentException.class, () -> new EffectsLimits(
                1024, 8, 4, 1024, 1024, 64, 64, 4,
                0, 4, 16, 4, 16, 16, 16, 4, 16, 1024));
    }
}
