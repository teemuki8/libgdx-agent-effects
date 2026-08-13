package io.github.teemuki8.libgdx.agent.effects.showcase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShowcaseStateTest {

    @Test
    void stateClampsIntensityWrapsTimeAndResetsOnSelection() {
        ShowcaseState state = new ShowcaseState(ShowcasePresets.all());
        float firstDefault = ShowcasePresets.all().get(0).defaultIntensity();
        assertEquals(firstDefault, state.intensity());

        state.setIntensity(2f);
        assertEquals(1f, state.intensity());
        state.advance(ShowcaseState.TIME_WRAP_SECONDS + 0.5f);
        assertEquals(0.5f, state.timeSeconds(), 0.0001f);

        state.togglePaused();
        assertTrue(state.paused());
        state.advance(1f);
        assertEquals(0.5f, state.timeSeconds(), 0.0001f);

        state.select(1);
        assertFalse(state.paused());
        assertEquals(0f, state.timeSeconds());
        assertEquals(ShowcasePresets.all().get(1).defaultIntensity(), state.intensity());
    }
}
