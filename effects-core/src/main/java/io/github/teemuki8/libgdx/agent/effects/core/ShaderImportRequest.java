package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Bounded-by-validation source request for one shader import. */
public record ShaderImportRequest(
        String name,
        String source,
        List<ShaderTargetProfile> targets) {

    public ShaderImportRequest {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(source, "source");
        targets = List.copyOf(targets);
        if (name.isBlank() || source.isBlank() || targets.isEmpty()) {
            throw new IllegalArgumentException("name, source, and targets must not be empty");
        }
        if (new HashSet<>(targets).size() != targets.size()) {
            throw new IllegalArgumentException("target profiles must be unique");
        }
    }

    /** Validates source and target counts before parsing. */
    public ShaderImportRequest validate(ImportLimits limits) {
        Objects.requireNonNull(limits, "limits");
        if (source.length() > limits.maxSourceChars()) {
            throw new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED,
                    "import source exceeds limits");
        }
        return this;
    }
}
