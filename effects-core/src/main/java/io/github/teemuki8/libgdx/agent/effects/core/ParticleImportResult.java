package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.List;
import java.util.Objects;

/** Immutable neutral particle import result with bounded mapping and fidelity evidence. */
public record ParticleImportResult(ParticleDefinition definition,
        FidelityClassification fidelity, List<FeatureMapping> featureMappings,
        List<ImportDiagnostic> diagnostics) {
    public ParticleImportResult {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(fidelity, "fidelity");
        featureMappings = List.copyOf(Objects.requireNonNull(featureMappings, "featureMappings"));
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        if (featureMappings.size() > 4096 || diagnostics.size() > 4096) {
            throw new IllegalArgumentException("particle import evidence exceeds hard bounds");
        }
    }
}
