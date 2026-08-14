package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.Objects;

/** Render-thread qualification evidence bound to one generated shader and observed GL target. */
public record ShaderQualificationResult(
        ShaderTargetProfile target,
        ShaderSource shader,
        EffectCapabilities capabilities,
        ShaderDiagnostic diagnostic,
        RgbaImage preview,
        PixelComparisonResult comparison,
        FidelityClassification fidelity) {

    public ShaderQualificationResult {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(shader, "shader");
        Objects.requireNonNull(capabilities, "capabilities");
        Objects.requireNonNull(diagnostic, "diagnostic");
        Objects.requireNonNull(fidelity, "fidelity");
        if (capabilities.profile() == EffectCapabilities.Profile.UNKNOWN) {
            throw new IllegalArgumentException("qualified capabilities must be explicit");
        }
        if (!diagnostic.compiled() && (preview != null || comparison != null)) {
            throw new IllegalArgumentException("failed compilation cannot have render evidence");
        }
        if (comparison != null && preview == null) {
            throw new IllegalArgumentException("comparison requires a preview");
        }
    }
}
