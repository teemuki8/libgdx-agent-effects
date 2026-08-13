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
    }

    @Test
    void rejectsNonPositiveLimits() {
        assertThrows(IllegalArgumentException.class, () ->
            new EffectsLimits(0, 64, 16, 4096 * 4096, 16 * 1024, 2048, 2048, 32));
    }
}
