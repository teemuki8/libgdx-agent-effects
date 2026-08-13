package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.List;

/** An immutable named fullscreen effect with bounded uniforms and a fixed deterministic time. */
public record EffectDescription(String name, ShaderSource shader, List<UniformBinding> uniforms,
        int renderWidth, int renderHeight, float timeSeconds) {

    public EffectDescription {
        java.util.Objects.requireNonNull(name, "name");
        java.util.Objects.requireNonNull(shader, "shader");
        uniforms = List.copyOf(uniforms);
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (renderWidth <= 0 || renderHeight <= 0) {
            throw new IllegalArgumentException("render size must be positive");
        }
        if (!Float.isFinite(timeSeconds)) {
            throw new IllegalArgumentException("timeSeconds must be finite");
        }
    }

    /** Rejects the effect when any field exceeds the supplied bounds. Returns {@code this}. */
    public EffectDescription validate(EffectsLimits limits) {
        if (name.length() > limits.maxShaderSourceChars()) {
            throw new EffectsException(EffectsException.Kind.INVALID_EFFECT, "name too long");
        }
        if (shader.vertex().length() > limits.maxShaderSourceChars()
                || shader.fragment().length() > limits.maxShaderSourceChars()) {
            throw new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED,
                "shader source too long");
        }
        if (uniforms.size() > limits.maxUniformCount()) {
            throw new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED, "too many uniforms");
        }
        if (renderWidth > limits.maxRenderWidth() || renderHeight > limits.maxRenderHeight()) {
            throw new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED,
                "render size exceeds limits");
        }
        return this;
    }
}
