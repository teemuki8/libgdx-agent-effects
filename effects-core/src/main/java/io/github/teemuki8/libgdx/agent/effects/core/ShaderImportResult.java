package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.List;
import java.util.Objects;

/** Immutable result of parsing and translating one imported shader. */
public record ShaderImportResult(
        String name,
        Material2dDefinition material,
        List<GeneratedShader> generatedShaders,
        List<ShaderSemantic> requiredSemantics,
        List<FeatureMapping> featureMappings,
        List<ImportDiagnostic> diagnostics,
        FidelityClassification fidelity) {

    public ShaderImportResult {
        Objects.requireNonNull(name, "name");
        generatedShaders = List.copyOf(generatedShaders);
        requiredSemantics = List.copyOf(requiredSemantics);
        featureMappings = List.copyOf(featureMappings);
        diagnostics = List.copyOf(diagnostics);
        Objects.requireNonNull(fidelity, "fidelity");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (fidelity != FidelityClassification.UNSUPPORTED && material == null) {
            throw new IllegalArgumentException("successful import requires a material");
        }
    }

    /** Validates generated text and result collections against configured limits. */
    public ShaderImportResult validate(ImportLimits limits, EffectsLimits effectsLimits) {
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(effectsLimits, "effectsLimits");
        long generatedCharacters = 0;
        for (GeneratedShader generated : generatedShaders) {
            generatedCharacters += generated.shader().vertex().length();
            generatedCharacters += generated.shader().fragment().length();
        }
        if (generatedCharacters > limits.maxGeneratedChars()
                || diagnostics.size() > limits.maxDiagnostics()
                || featureMappings.size() > limits.maxFeatureMappings()) {
            throw new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED,
                    "import result exceeds limits");
        }
        if (material != null) {
            material.validate(effectsLimits);
        }
        return this;
    }
}
