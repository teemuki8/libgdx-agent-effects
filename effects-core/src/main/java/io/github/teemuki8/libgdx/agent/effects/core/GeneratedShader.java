package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.List;
import java.util.Objects;

/** Generated shader pair and source maps for one target profile. */
public record GeneratedShader(
        ShaderTargetProfile profile,
        ShaderSource shader,
        List<SourceMapping> sourceMappings) {

    public GeneratedShader {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(shader, "shader");
        sourceMappings = List.copyOf(sourceMappings);
    }
}
