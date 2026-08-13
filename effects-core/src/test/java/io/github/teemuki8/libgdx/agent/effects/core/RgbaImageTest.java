package io.github.teemuki8.libgdx.agent.effects.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RgbaImageTest {
    @Test
    void storesAndCopiesDefensively() {
        int[] px = new int[4];
        RgbaImage img = new RgbaImage(2, 2, px);
        px[0] = 0x12345678; // mutate the caller array after construction
        assertEquals(0, img.getPixel(0, 0));
        int[] out = img.pixels();
        out[0] = 99;
        assertEquals(0, img.getPixel(0, 0));
    }

    @Test
    void rejectsWrongPixelCount() {
        assertThrows(IllegalArgumentException.class, () ->
            new RgbaImage(2, 2, new int[3]));
    }
}
