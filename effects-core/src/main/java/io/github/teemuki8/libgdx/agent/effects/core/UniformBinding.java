package io.github.teemuki8.libgdx.agent.effects.core;

/** A named uniform with a closed value. */
public record UniformBinding(String name, UniformValue value) {
    public UniformBinding {
        java.util.Objects.requireNonNull(name, "name");
        java.util.Objects.requireNonNull(value, "value");
        if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("invalid uniform name: " + name);
        }
    }
}
