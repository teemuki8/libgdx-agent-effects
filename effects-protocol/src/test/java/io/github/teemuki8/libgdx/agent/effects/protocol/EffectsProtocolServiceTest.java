package io.github.teemuki8.libgdx.agent.effects.protocol;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.teemuki8.libgdx.agent.effects.core.EffectCapabilities;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalog;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalogMatch;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalogQuery;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalogSearchResult;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EffectsProtocolServiceTest {
    @Test
    void applicationCanRegisterAnOptionalCoreCatalog() {
        EffectsProtocolService service = new EffectsProtocolService();
        EffectCatalog catalog = new EmptyCatalog();

        assertSame(service, service.catalog(catalog));
        assertSame(catalog, service.catalog());
        assertThrows(NullPointerException.class, () -> service.catalog(null));
    }

    private static final class EmptyCatalog implements EffectCatalog {
        @Override
        public EffectCatalogSearchResult search(EffectCatalogQuery query) {
            return new EffectCatalogSearchResult(List.of(), false);
        }

        @Override
        public Optional<EffectCatalogMatch> find(String id, EffectCapabilities target) {
            return Optional.empty();
        }
    }
}
