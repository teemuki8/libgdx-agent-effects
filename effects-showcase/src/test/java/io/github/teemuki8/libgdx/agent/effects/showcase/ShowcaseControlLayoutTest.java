package io.github.teemuki8.libgdx.agent.effects.showcase;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShowcaseControlLayoutTest {

    @Test
    void timeValueDoesNotCollideWithIntensityLabel() {
        ShowcaseControlLayout layout = ShowcaseControlLayout.at(246f);

        assertTrue(layout.timeValueX() + 48f <= layout.intensityLabelX());
        assertTrue(layout.intensityLabelX() + 74f <= layout.intensitySliderX());
    }
}
