package io.github.teemuki8.libgdx.agent.effects.core;

/** Immutable caller-observed graphics capabilities used for explicit backend selection. */
public record EffectCapabilities(int glMajor, int glMinor,
        int maxTextureSize, boolean floatTextures, Profile profile) {
    public EffectCapabilities {
        java.util.Objects.requireNonNull(profile, "profile");
        if (glMajor < 1 || glMajor > 99 || glMinor < 0 || glMinor > 99
                || maxTextureSize <= 0 || maxTextureSize > 65536) {
            throw new IllegalArgumentException("graphics capabilities are outside hard bounds");
        }
    }

    /** Preserves the original constructor for callers reporting desktop OpenGL. */
    public EffectCapabilities(int glMajor, int glMinor,
            int maxTextureSize, boolean floatTextures) {
        this(glMajor, glMinor, maxTextureSize, floatTextures, Profile.DESKTOP_OPENGL);
    }

    /** Whether the declared context is at least OpenGL 3.0. */
    public boolean supportsGl3() {
        return glMajor >= 3;
    }

    /** Whether desktop GLSL 150 is valid for the reported context. */
    public boolean supportsDesktopGlsl150() {
        return profile == Profile.DESKTOP_OPENGL
                && (glMajor > 3 || glMajor == 3 && glMinor >= 2);
    }

    /** Closed graphics-profile vocabulary independent of libGDX runtime classes. */
    public enum Profile {
        DESKTOP_OPENGL,
        OPENGL_ES,
        WEBGL
    }
}
