package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.List;
import java.util.Objects;

/** Immutable post-process execution, pool, eviction, and missing-input evidence. */
public record PostProcessRenderEvidence(List<String> executionOrder,
        int allocatedFramebuffers, long evictedFramebuffers, List<String> missingInputs) {
    public PostProcessRenderEvidence {
        executionOrder = List.copyOf(Objects.requireNonNull(executionOrder, "executionOrder"));
        missingInputs = List.copyOf(Objects.requireNonNull(missingInputs, "missingInputs"));
        if (executionOrder.size() > 4096 || missingInputs.size() > 4096
                || allocatedFramebuffers < 0 || evictedFramebuffers < 0L) {
            throw new IllegalArgumentException("post-process evidence is outside hard bounds");
        }
    }
}
