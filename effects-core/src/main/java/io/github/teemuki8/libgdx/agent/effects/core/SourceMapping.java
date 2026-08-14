package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.Objects;

/** Mapping from a Godot source range to its generated GLSL range. */
public record SourceMapping(SourceSpan source, SourceSpan generated) {
    public SourceMapping {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(generated, "generated");
    }
}
