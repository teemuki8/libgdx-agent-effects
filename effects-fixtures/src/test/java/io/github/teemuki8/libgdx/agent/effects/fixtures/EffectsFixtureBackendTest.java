package io.github.teemuki8.libgdx.agent.effects.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.teemuki8.libgdx.agent.effects.core.EffectDescription;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderSource;
import io.github.teemuki8.libgdx.agent.effects.libgdx.DefaultVertexShader;
import io.github.teemuki8.libgdx.agent.effects.mcp.EffectsToolHandler;
import io.github.teemuki8.libgdx.agent.effects.protocol.EffectsProtocolService;
import io.github.teemuki8.libgdx.agent.effects.protocol.Results;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EffectsFixtureBackendTest {

    private static EffectDescription redEffect() {
        ShaderSource good = new ShaderSource(DefaultVertexShader.SOURCE,
            "void main(){gl_FragColor=vec4(1.0,0.0,0.0,1.0);}");
        return new EffectDescription("red", good, List.of(), 64, 64, 0f);
    }

    @Test
    void backendCompileReportsDeclaredGoodEffect() throws Exception {
        GdxFixtureHost.run(() -> {
            EffectsProtocolService service =
                new EffectsProtocolService().declare(redEffect());
            try (EffectsFixtureBackend backend =
                    new EffectsFixtureBackend(service, EffectsLimits.developmentDefaults())) {
                Results.CompileResult compiled = backend.compile("red").toCompletableFuture().join();
                assertEquals("red", compiled.effectName());
                assertTrue(compiled.diagnostic().compiled(), compiled.diagnostic().infoLog());
            }
        });
    }

    @Test
    void backendPreviewReturnsSha256ArtifactReceipt() throws Exception {
        GdxFixtureHost.run(() -> {
            EffectsProtocolService service =
                new EffectsProtocolService().declare(redEffect());
            try (EffectsFixtureBackend backend =
                    new EffectsFixtureBackend(service, EffectsLimits.developmentDefaults())) {
                Results.PreviewResult preview = backend.preview("red").toCompletableFuture().join();
                assertEquals("red", preview.effectName());
                assertEquals(64, preview.width());
                assertEquals(64, preview.height());
                assertTrue(preview.artifactRef().matches("[0-9a-f]{64}"),
                    "artifact receipt must be the PNG SHA-256 hex digest");
            }
        });
    }

    @Test
    void mcpCompileDispatchesThroughTheRenderBackend() throws Exception {
        GdxFixtureHost.runAsync(() -> {
            EffectsProtocolService service = new EffectsProtocolService().declare(redEffect());
            EffectsFixtureBackend backend =
                new EffectsFixtureBackend(service, EffectsLimits.developmentDefaults());
            service.backend(backend);
            EffectsToolHandler handler = new EffectsToolHandler(service);
            McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "effect_compile", Map.of("effectName", "red"), null);
            return handler.handle(request)
                .doOnNext(result -> assertFalse(result.isError(), result.content().toString()))
                .then()
                .doFinally(signal -> {
                    handler.close();
                    backend.close();
                })
                .toFuture();
        });
    }
}
