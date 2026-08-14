package io.github.teemuki8.libgdx.agent.effects.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.Test;

class EffectCatalogModelTest {
    private static final int OVER_HARD_CAP = 1024 * 1024 + 1;

    @Test
    void catalogContractExposesTargetAwareSearchAndFind() {
        EffectCatalog catalog = new EffectCatalog() {
            @Override public EffectCatalogSearchResult search(EffectCatalogQuery query) {
                return new EffectCatalogSearchResult(List.of(), false);
            }

            @Override public Optional<EffectCatalogMatch> find(
                    String id, EffectCapabilities target) {
                return Optional.empty();
            }
        };

        assertTrue(catalog.find("ship-trail", CatalogTestFixtures.desktopGl2()).isEmpty());
    }

    @Test
    void catalogLimitsArePositiveAndHardBounded() {
        assertThrows(IllegalArgumentException.class,
                () -> new CatalogLimits(0, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new CatalogLimits(1, 1, 1, 1, 1_048_577));
        assertEquals(1024, CatalogLimits.developmentDefaults().maxEntries());
    }

    @Test
    void variantRejectsUnknownOrDuplicateQualificationTargets() {
        EffectDefinition effect = CatalogTestFixtures.trail("ship-trail");
        EffectCapabilities desktop = CatalogTestFixtures.desktopGl2();

        assertThrows(IllegalArgumentException.class, () -> new EffectCatalogVariant(
                "portable", 0, effect, List.of(desktop, desktop)));
        assertThrows(IllegalArgumentException.class, () -> new EffectCatalogVariant(
                "unknown", 0, effect, List.of(new EffectCapabilities(
                        3, 0, 4096, false, EffectCapabilities.Profile.UNKNOWN))));
        assertThrows(IllegalArgumentException.class, () -> new EffectCatalogVariant(
                "portable", 0, effect, List.of()));
    }

    @Test
    void variantDefensivelyCopiesTargetsAndMatchesActualCapabilities() {
        List<EffectCapabilities> targets = new ArrayList<>(
                List.of(CatalogTestFixtures.desktopGl2()));
        EffectCatalogVariant variant = new EffectCatalogVariant("portable", 0,
                CatalogTestFixtures.trail("ship-trail"), targets);
        targets.clear();

        assertEquals(1, variant.qualifiedTargets().size());
        assertTrue(variant.supports(new EffectCapabilities(4, 1, 8192, true,
                EffectCapabilities.Profile.DESKTOP_OPENGL)));
        assertFalse(variant.supports(new EffectCapabilities(3, 0, 4096, false,
                EffectCapabilities.Profile.OPENGL_ES)));
    }

    @Test
    void entryRequiresOneFamilyAcrossVariantsAndDefensivelyCopies() {
        List<String> tags = new ArrayList<>(List.of("space", "trail"));
        EffectCatalogEntry entry = CatalogTestFixtures.entry("ship-trail", tags,
                CatalogTestFixtures.variant(
                        CatalogTestFixtures.trail("ship-trail"),
                        CatalogTestFixtures.desktopGl2()));
        tags.clear();

        assertEquals(List.of("space", "trail"), entry.tags());
        assertEquals(EffectFamily.TRAIL, entry.family());
        assertThrows(UnsupportedOperationException.class,
                () -> entry.variants().clear());
    }

    @Test
    void entryRejectsInvalidIdentityMetadataOrderingAndFamily() {
        EffectCatalogVariant trail = CatalogTestFixtures.variant(
                CatalogTestFixtures.trail("ship-trail"),
                CatalogTestFixtures.desktopGl2());

        assertThrows(IllegalArgumentException.class,
                () -> entry("Ship Trail", "1.0.0", EffectFamily.TRAIL,
                        List.of("space"), "Apache-2.0", "Original", List.of(trail)));
        assertThrows(IllegalArgumentException.class,
                () -> entry("ship-trail", "1.0", EffectFamily.TRAIL,
                        List.of("space"), "Apache-2.0", "Original", List.of(trail)));
        assertThrows(IllegalArgumentException.class,
                () -> entry("ship-trail", "1.0.0", EffectFamily.TRAIL,
                        List.of("trail", "space"), "Apache-2.0", "Original", List.of(trail)));
        assertThrows(IllegalArgumentException.class,
                () -> entry("ship-trail", "1.0.0", EffectFamily.BEAM,
                        List.of("space"), "Apache-2.0", "Original", List.of(trail)));
        assertThrows(IllegalArgumentException.class,
                () -> entry("ship-trail", "1.0.0", EffectFamily.TRAIL,
                        List.of("space"), " ", "Original", List.of(trail)));
    }

    @Test
    void entryRejectsDuplicateVariantsAndUnresolvedLogicalAssets() {
        EffectCatalogVariant trail = CatalogTestFixtures.variant(
                CatalogTestFixtures.trail("ship-trail"),
                CatalogTestFixtures.desktopGl2());
        assertThrows(IllegalArgumentException.class,
                () -> entry("ship-trail", "1.0.0", EffectFamily.TRAIL,
                        List.of("space"), "Apache-2.0", "Original",
                        List.of(trail, trail)));

        Material2dDefinition textured = new Material2dDefinition("textured",
                new ShaderSource("void main(){}", "void main(){}"), BlendMode.NORMAL,
                List.of(), List.of(new AssetKey("source")));
        EffectCatalogVariant material = new EffectCatalogVariant("portable", 0,
                textured, List.of(CatalogTestFixtures.desktopGl2()));
        assertThrows(IllegalArgumentException.class, () -> new EffectCatalogEntry(
                "textured", "1.0.0", "Textured", "Textured material",
                EffectFamily.MATERIAL_2D, List.of("material"), "Apache-2.0",
                "Original", null, List.of(), List.of(material)));
    }

    @Test
    void configuredValidationBoundsTextCollectionsAndDefinitions() {
        EffectCatalogEntry entry = CatalogTestFixtures.entry("ship-trail",
                List.of("space", "trail"), CatalogTestFixtures.variant(
                        CatalogTestFixtures.trail("ship-trail"),
                        CatalogTestFixtures.desktopGl2()));

        assertThrows(EffectsException.class, () -> entry.validate(
                new CatalogLimits(1, 1, 1, 1, 1024),
                EffectsLimits.developmentDefaults()));
        assertThrows(EffectsException.class, () -> entry.validate(
                new CatalogLimits(1, 1, 2, 1, 4),
                EffectsLimits.developmentDefaults()));

        EffectsLimits oneTrailPoint = new EffectsLimits(
                1024, 8, 4, 1024, 1024, 64, 64, 4,
                8, 8, 32, 8, 8, 1, 8, 8, 8, 1024);
        assertThrows(EffectsException.class, () -> entry.validate(
                CatalogLimits.developmentDefaults(), oneTrailPoint));
    }

    @Test
    void queryAndResultsAreClosedImmutableValues() {
        List<String> tags = new ArrayList<>(List.of("space", "trail"));
        EffectCatalogQuery query = new EffectCatalogQuery(
                CatalogTestFixtures.desktopGl2(), EffectFamily.TRAIL, tags, 8);
        tags.clear();
        assertEquals(List.of("space", "trail"), query.tags());
        assertThrows(IllegalArgumentException.class, () -> new EffectCatalogQuery(
                CatalogTestFixtures.desktopGl2(), null, List.of(), 0));

        EffectCatalogEntry entry = CatalogTestFixtures.entry("ship-trail",
                List.of("space", "trail"), CatalogTestFixtures.variant(
                        CatalogTestFixtures.trail("ship-trail"),
                        CatalogTestFixtures.desktopGl2()));
        EffectCatalogMatch match = new EffectCatalogMatch(entry, entry.variants().get(0));
        List<EffectCatalogMatch> matches = new ArrayList<>(List.of(match));
        EffectCatalogSearchResult result = new EffectCatalogSearchResult(matches, false);
        matches.clear();
        assertEquals(List.of(match), result.matches());
    }

    @Test
    void entryRejectsHardOversizedTextBeforeConfiguredValidation() {
        EffectCatalogVariant variant = trailVariant();
        assertHardLimit("catalog entry text is outside hard bounds",
                () -> new EffectCatalogEntry("ship-trail", "1.0.0",
                        "x".repeat(OVER_HARD_CAP), "Description", EffectFamily.TRAIL,
                        List.of(), "Apache-2.0", "Original", null,
                        List.of(), List.of(variant)));
    }

    @Test
    void entryRejectsHardOversizedTagsBeforeCopying() {
        EffectCatalogVariant variant = trailVariant();
        assertHardLimit("catalog entry collections are outside hard bounds",
                () -> new EffectCatalogEntry("ship-trail", "1.0.0", "Ship trail",
                        "Description", EffectFamily.TRAIL,
                        Collections.nCopies(OVER_HARD_CAP, "space"),
                        "Apache-2.0", "Original", null, List.of(), List.of(variant)));
    }

    @Test
    void entryRejectsHardOversizedAssetsBeforeCopying() {
        EffectCatalogVariant variant = trailVariant();
        assertHardLimit("catalog entry collections are outside hard bounds",
                () -> new EffectCatalogEntry("ship-trail", "1.0.0", "Ship trail",
                        "Description", EffectFamily.TRAIL, List.of(), "Apache-2.0",
                        "Original", null,
                        Collections.nCopies(OVER_HARD_CAP, new AssetKey("source")),
                        List.of(variant)));
    }

    @Test
    void entryRejectsHardOversizedVariantsBeforeCopying() {
        EffectCatalogVariant variant = trailVariant();
        assertHardLimit("catalog entry collections are outside hard bounds",
                () -> new EffectCatalogEntry("ship-trail", "1.0.0", "Ship trail",
                        "Description", EffectFamily.TRAIL, List.of(), "Apache-2.0",
                        "Original", null, List.of(),
                        Collections.nCopies(OVER_HARD_CAP, variant)));
    }

    @Test
    void queryRejectsHardOversizedTagsBeforeCopying() {
        assertHardLimit("catalog query tags are outside hard bounds",
                () -> new EffectCatalogQuery(CatalogTestFixtures.desktopGl2(), null,
                        Collections.nCopies(OVER_HARD_CAP, "space"), 8));
    }

    @Test
    void queryRejectsHardOversizedTagText() {
        assertHardLimit("catalog query text is outside hard bounds",
                () -> new EffectCatalogQuery(CatalogTestFixtures.desktopGl2(), null,
                        List.of("x".repeat(OVER_HARD_CAP)), 8));
    }

    @Test
    void searchResultRejectsHardOversizedMatchesBeforeCopying() {
        EffectCatalogEntry entry = CatalogTestFixtures.entry("ship-trail", List.of(),
                trailVariant());
        EffectCatalogMatch match = new EffectCatalogMatch(entry, entry.variants().get(0));
        assertHardLimit("catalog search result is outside hard bounds",
                () -> new EffectCatalogSearchResult(
                        Collections.nCopies(OVER_HARD_CAP, match), true));
    }

    private static EffectCatalogEntry entry(String id, String version, EffectFamily family,
            List<String> tags, String license, String provenance,
            List<EffectCatalogVariant> variants) {
        return new EffectCatalogEntry(id, version, "Ship trail", "A reusable ship trail.",
                family, tags, license, provenance, null, List.of(), variants);
    }

    private static EffectCatalogVariant trailVariant() {
        return CatalogTestFixtures.variant(CatalogTestFixtures.trail("ship-trail"),
                CatalogTestFixtures.desktopGl2());
    }

    private static void assertHardLimit(String expectedMessage, Executable executable) {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class, executable);
        assertEquals(expectedMessage, failure.getMessage());
    }
}
