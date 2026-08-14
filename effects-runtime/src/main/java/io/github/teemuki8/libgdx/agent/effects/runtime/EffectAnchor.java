package io.github.teemuki8.libgdx.agent.effects.runtime;

import java.util.Objects;

/** Explicit application-supplied named position; the runtime never reads a game object. */
public record EffectAnchor(String name, float x, float y, float z) {
    public EffectAnchor {
        Objects.requireNonNull(name, "name");
        if (!name.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}")) {
            throw new IllegalArgumentException("invalid anchor name");
        }
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
            throw new IllegalArgumentException("anchor coordinates must be finite");
        }
    }
}
