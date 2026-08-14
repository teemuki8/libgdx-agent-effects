package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.Objects;

/** Render-thread qualification evidence for one generated shader target. */
public record ShaderQualificationResult(
        ShaderTargetProfile target,
        ShaderDiagnostic diagnostic,
        RgbaImage preview,
        PixelComparisonResult comparison,
        FidelityClassification fidelity) {

    public ShaderQualificationResult {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(diagnostic, "diagnostic");
        Objects.requireNonNull(fidelity, "fidelity");
        if (!diagnostic.compiled() && (preview != null || comparison != null)) {
            throw new IllegalArgumentException("failed compilation cannot have render evidence");
        }
        if (comparison != null && preview == null) {
            throw new IllegalArgumentException("comparison requires a preview");
        }
    }
}
