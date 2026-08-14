package io.github.teemuki8.libgdx.agent.effects.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.teemuki8.libgdx.agent.effects.core.CatalogLimits;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalogEntry;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalogMatch;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalogQuery;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalogSearchResult;
import io.github.teemuki8.libgdx.agent.effects.core.EffectFamily;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryEffectCatalogTest {

    @Test
    void searchFiltersTargetFamilyAndTagsBeforeStableTruncation() {
        EffectCatalogSearchResult result = LibraryTestFixtures.mixed().search(
                new EffectCatalogQuery(LibraryTestFixtures.desktopGl2(),
                        EffectFamily.TRAIL, List.of("space"), 1));

        assertEquals(List.of("ship-trail"), ids(result));
        assertEquals("alpha", result.matches().get(0).variant().id());
        assertTrue(result.truncated());
    }

    @Test
    void searchUsesStableEntryOrderAndExcludesIncompatibleEntries() {
        EffectCatalogSearchResult result = LibraryTestFixtures.mixed().search(
                new EffectCatalogQuery(LibraryTestFixtures.desktopGl2(),
                        null, List.of(), 8));

        assertEquals(List.of("ship-trail", "wide-trail"), ids(result));
        assertFalse(result.truncated());
    }

    @Test
    void exactLookupUsesTheSameVariantSelectorAndHidesIncompatibleEntries() {
        InMemoryEffectCatalog catalog = LibraryTestFixtures.mixed();

        assertEquals("alpha", catalog.find("ship-trail", LibraryTestFixtures.desktopGl2())
                .orElseThrow().variant().id());
        assertTrue(catalog.find("gpu-sparks", LibraryTestFixtures.desktopGl2()).isEmpty());
        assertTrue(catalog.find("missing", LibraryTestFixtures.desktopGl2()).isEmpty());
    }

    @Test
    void effectiveLimitIsTheMinimumAndTruncationIsTruthful() {
        CatalogLimits oneResult = new CatalogLimits(16, 8, 8, 1, 1024);
        InMemoryEffectCatalog capped = new InMemoryEffectCatalog(
                List.of(LibraryTestFixtures.wideTrail(), LibraryTestFixtures.shipTrail()),
                oneResult, LibraryTestFixtures.EFFECTS_LIMITS);
        EffectCatalogSearchResult cappedResult = capped.search(new EffectCatalogQuery(
                LibraryTestFixtures.desktopGl2(), EffectFamily.TRAIL, List.of(), 8));
        assertEquals(List.of("ship-trail"), ids(cappedResult));
        assertTrue(cappedResult.truncated());

        InMemoryEffectCatalog single = new InMemoryEffectCatalog(
                List.of(LibraryTestFixtures.shipTrail()),
                LibraryTestFixtures.CATALOG_LIMITS, LibraryTestFixtures.EFFECTS_LIMITS);
        assertFalse(single.search(new EffectCatalogQuery(
                LibraryTestFixtures.desktopGl2(), null, List.of(), 1)).truncated());
    }

    @Test
    void constructorRejectsDuplicateEntryIdsAndConfiguredEntryOverflow() {
        EffectCatalogEntry entry = LibraryTestFixtures.shipTrail();
        assertThrows(IllegalArgumentException.class, () -> new InMemoryEffectCatalog(
                List.of(entry, entry), LibraryTestFixtures.CATALOG_LIMITS,
                LibraryTestFixtures.EFFECTS_LIMITS));
        assertThrows(IllegalArgumentException.class, () -> new InMemoryEffectCatalog(
                List.of(entry, LibraryTestFixtures.wideTrail()),
                new CatalogLimits(1, 8, 8, 8, 1024),
                LibraryTestFixtures.EFFECTS_LIMITS));
        assertThrows(EffectsException.class, () -> new InMemoryEffectCatalog(
                List.of(entry), new CatalogLimits(1, 8, 1, 8, 1024),
                LibraryTestFixtures.EFFECTS_LIMITS));
    }

    @Test
    void constructorDefensivelyCopiesEntries() {
        List<EffectCatalogEntry> source = new ArrayList<>(
                List.of(LibraryTestFixtures.shipTrail()));
        InMemoryEffectCatalog catalog = new InMemoryEffectCatalog(source,
                LibraryTestFixtures.CATALOG_LIMITS, LibraryTestFixtures.EFFECTS_LIMITS);
        source.clear();

        assertTrue(catalog.find("ship-trail", LibraryTestFixtures.desktopGl2()).isPresent());
    }

    private static List<String> ids(EffectCatalogSearchResult result) {
        return result.matches().stream().map(EffectCatalogMatch::entry)
                .map(EffectCatalogEntry::id).toList();
    }
}
