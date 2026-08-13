package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.List;

/** Deterministic tolerance- and region-mask-aware pixel comparison over packed RGBA. */
public final class PixelComparer {

    public PixelComparisonResult compare(RgbaImage expected, RgbaImage actual,
            PixelComparisonSpec spec, EffectsLimits limits) {
        if (expected.width() != actual.width() || expected.height() != actual.height()) {
            throw new EffectsException(EffectsException.Kind.DIMENSION_MISMATCH,
                "image dimensions differ");
        }
        if (spec.includeRegions().size() > limits.maxRegionCount()
                || spec.ignoreRegions().size() > limits.maxRegionCount()) {
            throw new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED, "too many regions");
        }
        long compared = 0;
        long differing = 0;
        long overThreshold = 0;
        long errorSum = 0;
        int maxError = 0;
        int w = expected.width();
        int h = expected.height();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!included(x, y, spec)) {
                    continue;
                }
                compared++;
                int a = expected.getPixel(x, y);
                int b = actual.getPixel(x, y);
                int e = channelError(a, b);
                errorSum += e;
                if (e > maxError) {
                    maxError = e;
                }
                if (e > spec.tolerancePerChannel()) {
                    differing++;
                    overThreshold++;
                }
            }
        }
        double meanError = compared == 0 ? 0.0 : (double) errorSum / compared;
        double percent = compared == 0 ? 0.0 : 100.0 * overThreshold / compared;
        return new PixelComparisonResult(differing == 0, differing, maxError,
            meanError, percent, compared);
    }

    private static boolean included(int x, int y, PixelComparisonSpec spec) {
        boolean inInclude = spec.includeRegions().isEmpty()
            || spec.includeRegions().stream().anyMatch(r -> contains(r, x, y));
        if (!inInclude) {
            return false;
        }
        return spec.ignoreRegions().stream().noneMatch(r -> contains(r, x, y));
    }

    private static boolean contains(Region r, int x, int y) {
        return x >= r.x() && x < r.x() + r.width() && y >= r.y() && y < r.y() + r.height();
    }

    private static int channelError(int a, int b) {
        int max = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int da = Math.abs(((a >>> shift) & 0xff) - ((b >>> shift) & 0xff));
            if (da > max) {
                max = da;
            }
        }
        return max;
    }
}
