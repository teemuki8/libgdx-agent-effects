package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable target-aware catalog search filters. */
public record EffectCatalogQuery(EffectCapabilities target, EffectFamily family,
        List<String> tags, int limit) {
    private static final int HARD_LIMIT = 1024 * 1024;

    public EffectCatalogQuery {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(tags, "tags");
        if (target.profile() == EffectCapabilities.Profile.UNKNOWN
                || limit <= 0 || limit > HARD_LIMIT) {
            throw new IllegalArgumentException("catalog query target or limit is invalid");
        }
        if (tags.size() > HARD_LIMIT) {
            throw new IllegalArgumentException("catalog query tags are outside hard bounds");
        }
        Set<String> unique = new HashSet<>();
        String previous = null;
        for (String tag : tags) {
            if (tag == null) {
                throw new IllegalArgumentException("query tags must be unique and sorted");
            }
            if (tag.length() > HARD_LIMIT) {
                throw new IllegalArgumentException("catalog query text is outside hard bounds");
            }
            if (tag.isBlank() || !unique.add(tag)
                    || previous != null && previous.compareTo(tag) >= 0) {
                throw new IllegalArgumentException("query tags must be unique and sorted");
            }
            previous = tag;
        }
        tags = List.copyOf(tags);
    }
}
