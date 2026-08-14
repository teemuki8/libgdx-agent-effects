package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.List;
import java.util.Objects;

/** Immutable bounded catalog matches with explicit truncation evidence. */
public record EffectCatalogSearchResult(List<EffectCatalogMatch> matches, boolean truncated) {
    private static final int HARD_MATCH_CAP = 1024 * 1024;

    public EffectCatalogSearchResult {
        Objects.requireNonNull(matches, "matches");
        if (matches.size() > HARD_MATCH_CAP) {
            throw new IllegalArgumentException(
                    "catalog search result is outside hard bounds");
        }
        matches = List.copyOf(matches);
    }
}
