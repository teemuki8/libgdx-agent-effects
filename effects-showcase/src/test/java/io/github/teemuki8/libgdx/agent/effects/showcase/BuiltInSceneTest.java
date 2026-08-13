package io.github.teemuki8.libgdx.agent.effects.showcase;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.github.teemuki8.libgdx.agent.effects.core.RgbaImage;
import org.junit.jupiter.api.Test;

class BuiltInSceneTest {

    @Test
    void sceneIsDeterministicAndVisuallyVaried() {
        RgbaImage first = BuiltInScene.create();
        RgbaImage second = BuiltInScene.create();

        assertEquals(320, first.width());
        assertEquals(240, first.height());
        assertArrayEquals(first.pixels(), second.pixels());
        assertNotEquals(first.getPixel(0, 0), first.getPixel(160, 120));
        assertNotEquals(first.getPixel(160, 120), first.getPixel(160, 220));
    }
}
