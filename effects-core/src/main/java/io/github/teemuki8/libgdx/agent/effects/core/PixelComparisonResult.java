package io.github.teemuki8.libgdx.agent.effects.core;

/** Bounded summary of one pixel comparison. */
public record PixelComparisonResult(boolean pass, long differingPixels, int maxChannelError,
        double meanChannelError, double percentOverThreshold, long comparedPixels) {}
