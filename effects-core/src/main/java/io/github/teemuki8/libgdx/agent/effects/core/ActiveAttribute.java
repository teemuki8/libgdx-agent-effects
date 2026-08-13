package io.github.teemuki8.libgdx.agent.effects.core;

/** One active attribute reported by the driver. */
public record ActiveAttribute(String name, String type) {
    public ActiveAttribute {
        java.util.Objects.requireNonNull(name, "name");
        java.util.Objects.requireNonNull(type, "type");
    }
}
