package io.github.teemuki8.libgdx.agent.effects.library;

import io.github.teemuki8.libgdx.agent.effects.core.CatalogLimits;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCapabilities;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalog;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalogEntry;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalogMatch;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalogQuery;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalogSearchResult;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalogVariant;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable deterministic catalog backed by one bounded, sorted entry list. */
public final class InMemoryEffectCatalog implements EffectCatalog {
    private static final Comparator<EffectCatalogVariant> VARIANT_ORDER =
            Comparator.comparingInt(EffectCatalogVariant::preference)
                    .thenComparing(EffectCatalogVariant::id);

    private final List<EffectCatalogEntry> entries;
    private final CatalogLimits catalogLimits;

    /** Validates, defensively copies, and sorts the supplied catalog entries. */
    public InMemoryEffectCatalog(List<EffectCatalogEntry> entries,
            CatalogLimits catalogLimits, EffectsLimits effectsLimits) {
        Objects.requireNonNull(entries, "entries");
        this.catalogLimits = Objects.requireNonNull(catalogLimits, "catalogLimits");
        Objects.requireNonNull(effectsLimits, "effectsLimits");
        if (entries.size() > catalogLimits.maxEntries()) {
            throw new IllegalArgumentException("catalog entry count exceeds configured limit");
        }
        List<EffectCatalogEntry> sorted = new ArrayList<>(entries.size());
        Set<String> ids = new HashSet<>();
        for (EffectCatalogEntry entry : entries) {
            Objects.requireNonNull(entry, "entry").validate(catalogLimits, effectsLimits);
            if (!ids.add(entry.id())) {
                throw new IllegalArgumentException("catalog entry IDs must be unique");
            }
            sorted.add(entry);
        }
        sorted.sort(Comparator.comparing(EffectCatalogEntry::id));
        this.entries = List.copyOf(sorted);
    }

    @Override public EffectCatalogSearchResult search(EffectCatalogQuery query) {
        Objects.requireNonNull(query, "query");
        int limit = Math.min(query.limit(), catalogLimits.maxResults());
        List<EffectCatalogMatch> matches = new ArrayList<>(limit);
        boolean truncated = false;
        for (EffectCatalogEntry entry : entries) {
            if (query.family() != null && entry.family() != query.family()
                    || !entry.tags().containsAll(query.tags())) {
                continue;
            }
            Optional<EffectCatalogVariant> variant = selectVariant(entry, query.target());
            if (variant.isEmpty()) {
                continue;
            }
            if (matches.size() == limit) {
                truncated = true;
                break;
            }
            matches.add(new EffectCatalogMatch(entry, variant.orElseThrow()));
        }
        return new EffectCatalogSearchResult(matches, truncated);
    }

    @Override public Optional<EffectCatalogMatch> find(
            String id, EffectCapabilities target) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(target, "target");
        for (EffectCatalogEntry entry : entries) {
            if (entry.id().equals(id)) {
                return selectVariant(entry, target)
                        .map(variant -> new EffectCatalogMatch(entry, variant));
            }
        }
        return Optional.empty();
    }

    private static Optional<EffectCatalogVariant> selectVariant(
            EffectCatalogEntry entry, EffectCapabilities target) {
        return entry.variants().stream()
                .filter(variant -> variant.supports(target))
                .min(VARIANT_ORDER);
    }
}
