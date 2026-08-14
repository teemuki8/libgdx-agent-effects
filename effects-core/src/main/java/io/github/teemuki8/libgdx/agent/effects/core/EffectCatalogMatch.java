package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.Objects;

/** One compatible catalog entry paired with its selected variant. */
public record EffectCatalogMatch(EffectCatalogEntry entry, EffectCatalogVariant variant) {
    public EffectCatalogMatch {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(variant, "variant");
        if (!entry.variants().contains(variant)) {
            throw new IllegalArgumentException("selected variant does not belong to entry");
        }
    }
}
