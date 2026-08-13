package io.github.teemuki8.libgdx.agent.effects.core;

/** One active uniform reported by the driver. */
public record ActiveUniform(String name, String type, int size) {
    public ActiveUniform {
        java.util.Objects.requireNonNull(name, "name");
        java.util.Objects.requireNonNull(type, "type");
    }
}
