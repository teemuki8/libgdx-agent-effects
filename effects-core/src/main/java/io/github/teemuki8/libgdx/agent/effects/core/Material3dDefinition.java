package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.List;
import java.util.Objects;

/** Immutable 3D material with explicit depth and culling policy. */
public record Material3dDefinition(
        String name,
        ShaderSource shader,
        BlendMode blendMode,
        boolean depthTest,
        boolean depthWrite,
        boolean cullBackFaces,
        List<UniformBinding> uniforms,
        List<AssetKey> textures) implements EffectDefinition {

    public Material3dDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(shader, "shader");
        Objects.requireNonNull(blendMode, "blendMode");
        uniforms = List.copyOf(uniforms);
        textures = List.copyOf(textures);
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }

    @Override public Material3dDefinition validate(EffectsLimits limits) {
        Objects.requireNonNull(limits, "limits");
        if (name.length() > limits.maxShaderSourceChars()
                || shader.vertex().length() > limits.maxShaderSourceChars()
                || shader.fragment().length() > limits.maxShaderSourceChars()) {
            throw new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED,
                    "material text exceeds limits");
        }
        if (uniforms.size() > limits.maxUniformCount()
                || textures.size() > limits.maxUniformCount()) {
            throw new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED,
                    "material bindings exceed limits");
        }
        return this;
    }
}
