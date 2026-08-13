package io.github.teemuki8.libgdx.agent.effects.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Gdx;
import io.github.teemuki8.libgdx.agent.effects.core.EffectDescription;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderSource;
import io.github.teemuki8.libgdx.agent.effects.libgdx.DefaultVertexShader;
import io.github.teemuki8.libgdx.agent.effects.mcp.EffectsToolHandler;
import io.github.teemuki8.libgdx.agent.effects.protocol.EffectsProtocolService;
import io.github.teemuki8.libgdx.agent.effects.protocol.Results;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

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
                .then(Mono.defer(() -> closeOnRenderThread(backend)))
                .doFinally(signal -> handler.close())
                .toFuture();
        });
    }

    @Test
    void closedBackendRejectsNewRenderWork() throws Exception {
        GdxFixtureHost.run(() -> {
            EffectsProtocolService service = new EffectsProtocolService().declare(redEffect());
            EffectsFixtureBackend backend =
                new EffectsFixtureBackend(service, EffectsLimits.developmentDefaults());
            backend.close();
            backend.close();

            CompletionException failure = assertThrows(CompletionException.class,
                () -> backend.compile("red").toCompletableFuture().join());
            assertTrue(failure.getCause() instanceof IllegalStateException);
        });
    }

    @Test
    void backendCloseRejectsTheWrongThread() throws Exception {
        GdxFixtureHost.run(() -> {
            EffectsProtocolService service = new EffectsProtocolService().declare(redEffect());
            try (EffectsFixtureBackend backend =
                    new EffectsFixtureBackend(service, EffectsLimits.developmentDefaults())) {
                CompletableFuture<Void> close = CompletableFuture.runAsync(backend::close);
                CompletionException failure = assertThrows(CompletionException.class, close::join);
                EffectsException effectsFailure = (EffectsException) failure.getCause();
                assertEquals(EffectsException.Kind.WRONG_THREAD, effectsFailure.kind());
            }
        });
    }

    private static Mono<Void> closeOnRenderThread(EffectsFixtureBackend backend) {
        CompletableFuture<Void> closed = new CompletableFuture<>();
        Gdx.app.postRunnable(() -> {
            try {
                backend.close();
                closed.complete(null);
            } catch (RuntimeException failure) {
                closed.completeExceptionally(failure);
            }
        });
        return Mono.fromFuture(closed);
    }
}
