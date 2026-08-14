package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.Objects;

/** Immutable caller-observed graphics capabilities used for explicit backend selection. */
public record EffectCapabilities(int glMajor, int glMinor,
        int maxTextureSize, boolean floatTextures, Profile profile) {
    public EffectCapabilities {
        Objects.requireNonNull(profile, "profile");
        if (glMajor < 1 || glMajor > 99 || glMinor < 0 || glMinor > 99
                || maxTextureSize <= 0 || maxTextureSize > 65536) {
            throw new IllegalArgumentException("graphics capabilities are outside hard bounds");
        }
    }

    /** Preserves the original constructor conservatively without assuming a graphics profile. */
    public EffectCapabilities(int glMajor, int glMinor,
            int maxTextureSize, boolean floatTextures) {
        this(glMajor, glMinor, maxTextureSize, floatTextures, Profile.UNKNOWN);
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

    /** Whether these actual capabilities meet every declared minimum requirement. */
    public boolean satisfies(EffectCapabilities required) {
        Objects.requireNonNull(required, "required");
        if (profile == Profile.UNKNOWN || required.profile == Profile.UNKNOWN
                || profile != required.profile) {
            return false;
        }
        boolean version = glMajor > required.glMajor
                || glMajor == required.glMajor && glMinor >= required.glMinor;
        return version && maxTextureSize >= required.maxTextureSize
                && (!required.floatTextures || floatTextures);
    }

    /** Closed graphics-profile vocabulary independent of libGDX runtime classes. */
    public enum Profile {
        UNKNOWN,
        DESKTOP_OPENGL,
        OPENGL_ES,
        WEBGL
    }
}
