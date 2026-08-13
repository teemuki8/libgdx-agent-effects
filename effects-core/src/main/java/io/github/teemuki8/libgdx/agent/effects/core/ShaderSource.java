package io.github.teemuki8.libgdx.agent.effects.core;

/** A vertex + fragment GLSL source pair. */
public record ShaderSource(String vertex, String fragment) {
    public ShaderSource {
        java.util.Objects.requireNonNull(vertex, "vertex");
        java.util.Objects.requireNonNull(fragment, "fragment");
        if (fragment.isBlank()) {
            throw new IllegalArgumentException("fragment must not be blank");
        }
    }
}
