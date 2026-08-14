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
        int maxRegionCount,
        int maxCurveStops,
        int maxGradientStops,
        int maxDefinitionNodes,
        int maxRuntimeInstances,
        int maxParticles,
        int maxTrailPoints,
        int maxBeamSegments,
        int maxLightningBranches,
        int maxDecals,
        int maxFramebufferPixels) {

    private static final int HARD_RENDER_CAP = 8192;
    private static final int HARD_COUNT_CAP = 1024 * 1024;
    private static final int HARD_PIXEL_CAP = HARD_RENDER_CAP * HARD_RENDER_CAP;

    public EffectsLimits {
        requirePositive(maxShaderSourceChars, "maxShaderSourceChars");
        requirePositive(maxUniformCount, "maxUniformCount");
        requirePositive(maxPassCount, "maxPassCount");
        requirePositive(maxTexturePixels, "maxTexturePixels");
        requirePositive(maxDiagnosticChars, "maxDiagnosticChars");
        requirePositive(maxRenderWidth, "maxRenderWidth");
        requirePositive(maxRenderHeight, "maxRenderHeight");
        requirePositive(maxRegionCount, "maxRegionCount");
        requireCount(maxCurveStops, "maxCurveStops");
        requireCount(maxGradientStops, "maxGradientStops");
        requireCount(maxDefinitionNodes, "maxDefinitionNodes");
        requireCount(maxRuntimeInstances, "maxRuntimeInstances");
        requireCount(maxParticles, "maxParticles");
        requireCount(maxTrailPoints, "maxTrailPoints");
        requireCount(maxBeamSegments, "maxBeamSegments");
        requireCount(maxLightningBranches, "maxLightningBranches");
        requireCount(maxDecals, "maxDecals");
        requirePositive(maxFramebufferPixels, "maxFramebufferPixels");
        if (maxRenderWidth > HARD_RENDER_CAP || maxRenderHeight > HARD_RENDER_CAP) {
            throw new IllegalArgumentException("render dimensions exceed hard cap");
        }
        if (maxFramebufferPixels > HARD_PIXEL_CAP
                || maxFramebufferPixels > maxTexturePixels) {
            throw new IllegalArgumentException("framebuffer pixels exceed configured hard bounds");
        }
    }

    /** Preserves the original shader/preview limit constructor with conservative VFX defaults. */
    public EffectsLimits(int maxShaderSourceChars, int maxUniformCount, int maxPassCount,
            int maxTexturePixels, int maxDiagnosticChars, int maxRenderWidth,
            int maxRenderHeight, int maxRegionCount) {
        this(maxShaderSourceChars, maxUniformCount, maxPassCount, maxTexturePixels,
                maxDiagnosticChars, maxRenderWidth, maxRenderHeight, maxRegionCount,
                256, 256, 1024, 1024, 65536, 8192, 8192, 256, 4096,
                Math.min(maxTexturePixels, 4096 * 4096));
    }

    public static EffectsLimits developmentDefaults() {
        return new EffectsLimits(
                64 * 1024, 64, 16, 4096 * 4096, 16 * 1024, 2048, 2048, 32,
                256, 256, 1024, 1024, 65536, 8192, 8192, 256, 4096,
                4096 * 4096);
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireCount(int value, String name) {
        if (value <= 0 || value > HARD_COUNT_CAP) {
            throw new IllegalArgumentException(name + " must be within hard bounds");
        }
    }
}
