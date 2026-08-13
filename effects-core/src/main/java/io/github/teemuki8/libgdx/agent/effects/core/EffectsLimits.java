package io.github.teemuki8.libgdx.agent.effects.core;

/** Bounded limits for effects, shaders, diagnostics, images, and comparisons. */
public record EffectsLimits(
        int maxShaderSourceChars,
        int maxUniformCount,
        int maxPassCount,
        int maxTexturePixels,
        int maxDiagnosticChars,
        int maxRenderWidth,
        int maxRenderHeight,
        int maxRegionCount) {

    private static final int HARD_RENDER_CAP = 8192;

    public EffectsLimits {
        requirePositive(maxShaderSourceChars, "maxShaderSourceChars");
        requirePositive(maxUniformCount, "maxUniformCount");
        requirePositive(maxPassCount, "maxPassCount");
        requirePositive(maxTexturePixels, "maxTexturePixels");
        requirePositive(maxDiagnosticChars, "maxDiagnosticChars");
        requirePositive(maxRenderWidth, "maxRenderWidth");
        requirePositive(maxRenderHeight, "maxRenderHeight");
        requirePositive(maxRegionCount, "maxRegionCount");
        if (maxRenderWidth > HARD_RENDER_CAP || maxRenderHeight > HARD_RENDER_CAP) {
            throw new IllegalArgumentException("render dimensions exceed hard cap");
        }
    }

    public static EffectsLimits developmentDefaults() {
        return new EffectsLimits(
                64 * 1024, 64, 16, 4096 * 4096, 16 * 1024, 2048, 2048, 32);
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
