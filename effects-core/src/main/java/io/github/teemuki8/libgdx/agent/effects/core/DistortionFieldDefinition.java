package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.List;
import java.util.Objects;

/** Immutable one-pass scene/distortion-vector composition definition. */
public record DistortionFieldDefinition(String name, Material2dDefinition material,
        String sceneInput, String vectorInput, String output) implements EffectDefinition {
    public DistortionFieldDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(sceneInput, "sceneInput");
        Objects.requireNonNull(vectorInput, "vectorInput");
        Objects.requireNonNull(output, "output");
        if (name.isBlank() || sceneInput.isBlank() || vectorInput.isBlank() || output.isBlank()
                || sceneInput.equals(vectorInput)
                || !material.textures().stream().map(AssetKey::value).toList()
                        .equals(List.of(sceneInput, vectorInput))) {
            throw new IllegalArgumentException("invalid distortion field declaration");
        }
    }

    @Override public DistortionFieldDefinition validate(EffectsLimits limits) {
        Objects.requireNonNull(limits, "limits");
        material.validate(limits);
        return this;
    }

    /** Converts the field to one bounded pass graph. */
    public PostProcessGraphDefinition asGraph() {
        RenderPassDefinition pass = new RenderPassDefinition(name, material,
                List.of(sceneInput, vectorInput), output);
        return new PostProcessGraphDefinition(name, List.of(sceneInput, vectorInput),
                List.of(pass), output, 1);
    }
}
