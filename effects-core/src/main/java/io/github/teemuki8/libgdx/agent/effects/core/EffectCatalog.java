package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.Optional;

/** Target-aware catalog of normalized, qualified effect definitions. */
public interface EffectCatalog {

    /** Searches for compatible entries using stable implementation-defined ordering. */
    EffectCatalogSearchResult search(EffectCatalogQuery query);

    /** Finds a compatible entry and selected variant without exposing incompatible entries. */
    Optional<EffectCatalogMatch> find(String id, EffectCapabilities target);
}
