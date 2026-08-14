package io.github.teemuki8.libgdx.agent.effects.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EffectCapabilitiesTest {

    @Test
    void actualCapabilitiesMustMeetProfileVersionTextureAndFloatRequirements() {
        EffectCapabilities actual = new EffectCapabilities(4, 1, 8192, true,
                EffectCapabilities.Profile.DESKTOP_OPENGL);

        assertTrue(actual.satisfies(new EffectCapabilities(3, 2, 4096, true,
                EffectCapabilities.Profile.DESKTOP_OPENGL)));
        assertFalse(actual.satisfies(new EffectCapabilities(3, 0, 4096, false,
                EffectCapabilities.Profile.OPENGL_ES)));
        assertFalse(new EffectCapabilities(4, 1, 8192, true,
                EffectCapabilities.Profile.UNKNOWN).satisfies(actual));
    }
}
