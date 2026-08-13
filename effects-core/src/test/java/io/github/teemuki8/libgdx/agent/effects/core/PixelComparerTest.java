package io.github.teemuki8.libgdx.agent.effects.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PixelComparerTest {
    @Test
    void equalImagesPass() {
        RgbaImage a = RgbaImage.solid(4, 4, 0xffff0000);
        RgbaImage b = RgbaImage.solid(4, 4, 0xffff0000);
        PixelComparisonResult r = new PixelComparer().compare(a, b,
            new PixelComparisonSpec(0, List.of(), List.of()), EffectsLimits.developmentDefaults());
        assertTrue(r.pass());
        assertEquals(0, r.differingPixels());
    }

    @Test
    void toleranceAllowsSmallDeviation() {
        RgbaImage a = RgbaImage.solid(2, 2, 0xff102030);
        RgbaImage b = RgbaImage.solid(2, 2, 0xff102031);
        PixelComparer c = new PixelComparer();
        PixelComparisonResult strict = c.compare(a, b,
            new PixelComparisonSpec(0, List.of(), List.of()), EffectsLimits.developmentDefaults());
        assertFalse(strict.pass());
        PixelComparisonResult tolerant = c.compare(a, b,
            new PixelComparisonSpec(1, List.of(), List.of()), EffectsLimits.developmentDefaults());
        assertTrue(tolerant.pass());
    }

    @Test
    void ignoredRegionIsExcluded() {
        RgbaImage a = RgbaImage.solid(2, 2, 0xffff0000);
        RgbaImage b = RgbaImage.solid(2, 2, 0xffff0000);
        int[] bp = b.pixels();
        bp[0] = 0xff00ff00;
        RgbaImage b2 = new RgbaImage(2, 2, bp);
        PixelComparisonResult r = new PixelComparer().compare(a, b2,
            new PixelComparisonSpec(0, List.of(), List.of(new Region(0, 0, 1, 1))),
            EffectsLimits.developmentDefaults());
        assertTrue(r.pass());
    }

    @Test
    void dimensionMismatchFails() {
        RgbaImage a = RgbaImage.solid(2, 2, 0xffff0000);
        RgbaImage b = RgbaImage.solid(3, 2, 0xffff0000);
        assertThrows(EffectsException.class, () -> new PixelComparer().compare(a, b,
            new PixelComparisonSpec(0, List.of(), List.of()), EffectsLimits.developmentDefaults()));
    }
}
