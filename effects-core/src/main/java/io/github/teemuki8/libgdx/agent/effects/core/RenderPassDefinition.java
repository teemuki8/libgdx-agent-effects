package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable named material pass with declared inputs and one output. */
public record RenderPassDefinition(String name, Material2dDefinition material,
        List<String> inputs, String output) {
    public RenderPassDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(material, "material");
        inputs = List.copyOf(Objects.requireNonNull(inputs, "inputs"));
        Objects.requireNonNull(output, "output");
        if (name.isBlank() || output.isBlank() || inputs.isEmpty()) {
            throw new IllegalArgumentException("render pass names and inputs must not be blank");
        }
        Set<String> unique = new HashSet<>();
        for (String input : inputs) {
            if (input == null || input.isBlank() || !unique.add(input)) {
                throw new IllegalArgumentException("render pass inputs must be unique names");
            }
        }
        if (!material.textures().stream().map(AssetKey::value).toList().equals(inputs)) {
            throw new IllegalArgumentException("material texture keys must match pass inputs");
        }
    }
}
