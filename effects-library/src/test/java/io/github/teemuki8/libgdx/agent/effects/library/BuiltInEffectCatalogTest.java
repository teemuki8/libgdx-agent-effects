package io.github.teemuki8.libgdx.agent.effects.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.teemuki8.libgdx.agent.effects.core.CatalogLimits;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCapabilities;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalog;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalogQuery;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalogSearchResult;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import java.util.List;
import org.junit.jupiter.api.Test;

class BuiltInEffectCatalogTest {

    @Test
    void bundledCatalogContainsOnlyQualifiedApacheEntriesInStableOrder() {
        EffectCatalog catalog = BuiltInEffectCatalog.create(
                CatalogLimits.developmentDefaults(), EffectsLimits.developmentDefaults());
        EffectCatalogSearchResult result = catalog.search(new EffectCatalogQuery(
                desktopGl2(), null, List.of(), 32));

        assertEquals(List.of("arc-lightning", "damage-pulse", "energy-beam",
                "neon-edges", "ship-trail", "sparks"), result.matches().stream()
                .map(match -> match.entry().id()).toList());
        assertTrue(result.matches().stream()
                .allMatch(match -> match.entry().license().equals("Apache-2.0")));
        assertTrue(result.matches().stream()
                .allMatch(match -> match.entry().provenance().equals(
                        "Original libgdx-agent-effects content")));
        assertFalse(result.truncated());
    }

    @Test
    void bundledCatalogDoesNotAdvertiseUnqualifiedProfiles() {
        EffectCatalog catalog = BuiltInEffectCatalog.create(
                CatalogLimits.developmentDefaults(), EffectsLimits.developmentDefaults());
        EffectCapabilities gles = new EffectCapabilities(3, 0, 4096, false,
                EffectCapabilities.Profile.OPENGL_ES);

        assertTrue(catalog.search(new EffectCatalogQuery(
                gles, null, List.of(), 32)).matches().isEmpty());
    }

    private static EffectCapabilities desktopGl2() {
        return new EffectCapabilities(2, 0, 2048, false,
                EffectCapabilities.Profile.DESKTOP_OPENGL);
    }
}
