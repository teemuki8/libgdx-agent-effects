package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.List;
import java.util.Objects;

/** Immutable selected particle backend and approximation/bound evidence. */
public record ParticleBackendEvidence(Backend backend, boolean approximate,
        List<String> diagnostics, int stateTexturePixels) {
    public ParticleBackendEvidence {
        Objects.requireNonNull(backend, "backend");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        if (diagnostics.size() > 64 || stateTexturePixels < 0) {
            throw new IllegalArgumentException("particle backend evidence is outside bounds");
        }
    }

    /** Closed runtime backend choice. */
    public enum Backend {
        CPU,
        GPU_GL3
    }
}
