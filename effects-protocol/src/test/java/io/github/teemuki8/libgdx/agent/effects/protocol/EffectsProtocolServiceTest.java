package io.github.teemuki8.libgdx.agent.effects.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.teemuki8.libgdx.agent.effects.core.EffectCapabilities;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalog;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalogMatch;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalogQuery;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalogSearchResult;
import io.github.teemuki8.libgdx.agent.effects.core.AssetKey;
import io.github.teemuki8.libgdx.agent.effects.core.BlendMode;
import io.github.teemuki8.libgdx.agent.effects.core.EffectFamily;
import io.github.teemuki8.libgdx.agent.effects.core.Material2dDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.PostProcessGraphDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.RenderPassDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderSource;
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

    @Test
    void declaresPostProcessGraphThroughGeneralDefinitionContract() {
        Material2dDefinition material = new Material2dDefinition("copy-material",
                new ShaderSource("void main(){}", "void main(){}"), BlendMode.NORMAL,
                List.of(), List.of(new AssetKey("scene")));
        PostProcessGraphDefinition graph = new PostProcessGraphDefinition("copy-graph",
                List.of("scene"), List.of(new RenderPassDefinition(
                        "copy", material, List.of("scene"), "output")), "output", 1);
        EffectsProtocolService service = new EffectsProtocolService().declareDefinition(graph);

        Results.EffectSummaryResult summary = service.effectSummary("copy-graph");
        assertEquals(EffectFamily.POST_PROCESS_GRAPH, summary.family());
        assertEquals(1, summary.capacity());
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
