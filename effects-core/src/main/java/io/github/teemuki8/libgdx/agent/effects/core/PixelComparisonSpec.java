package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.List;

/** Tolerance and region masks for one pixel comparison. */
public record PixelComparisonSpec(int tolerancePerChannel, List<Region> includeRegions,
        List<Region> ignoreRegions) {
    public PixelComparisonSpec {
        if (tolerancePerChannel < 0 || tolerancePerChannel > 255) {
            throw new IllegalArgumentException("tolerancePerChannel must be in [0,255]");
        }
        includeRegions = List.copyOf(includeRegions);
        ignoreRegions = List.copyOf(ignoreRegions);
    }
}
