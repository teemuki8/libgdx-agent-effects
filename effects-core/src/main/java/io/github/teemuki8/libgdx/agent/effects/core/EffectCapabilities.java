package io.github.teemuki8.libgdx.agent.effects.core;

/** Immutable caller-observed graphics capabilities used for explicit backend selection. */
public record EffectCapabilities(int glMajor, int glMinor,
        int maxTextureSize, boolean floatTextures) {
    public EffectCapabilities {
        if (glMajor < 1 || glMajor > 99 || glMinor < 0 || glMinor > 99
                || maxTextureSize <= 0 || maxTextureSize > 65536) {
            throw new IllegalArgumentException("graphics capabilities are outside hard bounds");
        }
    }

    /** Whether the declared context is at least OpenGL 3.0. */
    public boolean supportsGl3() {
        return glMajor >= 3;
    }
}
