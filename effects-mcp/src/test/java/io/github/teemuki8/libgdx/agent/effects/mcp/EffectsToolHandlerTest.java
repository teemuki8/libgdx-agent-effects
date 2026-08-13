package io.github.teemuki8.libgdx.agent.effects.mcp;

import static org.junit.jupiter.api.Assertions.assertSame;

import io.github.teemuki8.libgdx.agent.effects.core.EffectDescription;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.PixelComparisonSpec;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderDiagnostic;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderSource;
import io.github.teemuki8.libgdx.agent.effects.protocol.EffectsBackend;
import io.github.teemuki8.libgdx.agent.effects.protocol.EffectsProtocolService;
import io.github.teemuki8.libgdx.agent.effects.protocol.Results;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class EffectsToolHandlerTest {
    @Test
    void invokesWiredBackendOnItsOwnerThread() {
        ExecutorService renderExecutor = Executors.newSingleThreadExecutor();
        AtomicReference<Thread> invoked = new AtomicReference<>();
        EffectsBackend backend = new EffectsBackend() {
            @Override public CompletionStage<Results.CompileResult> compile(String effectName) {
                return CompletableFuture.supplyAsync(() -> {
                    invoked.set(Thread.currentThread());
                    return compileResult(effectName);
                }, renderExecutor);
            }

            @Override public CompletionStage<Results.PreviewResult> preview(String effectName) {
                throw new UnsupportedOperationException();
            }

            @Override public CompletionStage<Results.CompareResult> compare(String referenceName,
                    String actualName, PixelComparisonSpec spec) {
                throw new UnsupportedOperationException();
            }
        };
        EffectDescription effect = new EffectDescription("red",
            new ShaderSource("void main(){}", "void main(){}"), List.of(), 1, 1, 0f);
        EffectsProtocolService protocol = new EffectsProtocolService()
            .declare(effect).backend(backend);
        Thread owner = CompletableFuture.supplyAsync(Thread::currentThread, renderExecutor).join();
        try (renderExecutor; EffectsToolHandler handler = new EffectsToolHandler(protocol)) {
            handler.handle(request("effect_compile", Map.of("effectName", "red"))).block();
        }
        assertSame(owner, invoked.get(),
            "render backend must run on the thread that owns its resources");
    }

    @Test
    void preservesTypedBackendFailure() {
        EffectsBackend backend = new CompileBackend() {
            @Override public CompletionStage<Results.CompileResult> compile(String effectName) {
                return CompletableFuture.failedFuture(new EffectsException(
                    EffectsException.Kind.WRONG_THREAD, "render owner unavailable"));
            }
        };
        EffectsProtocolService protocol = protocol(backend);
        try (EffectsToolHandler handler = new EffectsToolHandler(protocol)) {
            McpSchema.CallToolResult result = handler.handle(
                request("effect_compile", Map.of("effectName", "red"))).block();
            assertErrorCode(result, "WRONG_THREAD");
        }
    }

    @Test
    void declaredEffectWithoutBackendRemainsNotAvailable() {
        EffectsProtocolService protocol = protocol(null);
        try (EffectsToolHandler handler = new EffectsToolHandler(protocol)) {
            McpSchema.CallToolResult result = handler.handle(
                request("effect_compile", Map.of("effectName", "red"))).block();
            assertErrorCode(result, "NOT_AVAILABLE");
        }
    }

    @Test
    void unknownEffectRemainsUnknown() {
        EffectsProtocolService protocol = protocol(null);
        try (EffectsToolHandler handler = new EffectsToolHandler(protocol)) {
            McpSchema.CallToolResult result = handler.handle(
                request("effect_compile", Map.of("effectName", "missing"))).block();
            assertErrorCode(result, "UNKNOWN_EFFECT");
        }
    }

    private static EffectsProtocolService protocol(EffectsBackend backend) {
        EffectDescription effect = new EffectDescription("red",
            new ShaderSource("void main(){}", "void main(){}"), List.of(), 1, 1, 0f);
        EffectsProtocolService protocol = new EffectsProtocolService().declare(effect);
        return backend == null ? protocol : protocol.backend(backend);
    }

    private static Results.CompileResult compileResult(String effectName) {
        return new Results.CompileResult(effectName,
            new ShaderDiagnostic(true, List.of(), List.of(), List.of(), ""));
    }

    private static McpSchema.CallToolRequest request(String name, Map<String, Object> arguments) {
        return new McpSchema.CallToolRequest(name, arguments, null);
    }

    @SuppressWarnings("unchecked")
    private static void assertErrorCode(McpSchema.CallToolResult result, String code) {
        Map<String, Object> structured = (Map<String, Object>) result.structuredContent();
        org.junit.jupiter.api.Assertions.assertEquals(code, structured.get("code"));
    }

    private abstract static class CompileBackend implements EffectsBackend {
        @Override public CompletionStage<Results.PreviewResult> preview(String effectName) {
            throw new UnsupportedOperationException();
        }

        @Override public CompletionStage<Results.CompareResult> compare(String referenceName,
                String actualName, PixelComparisonSpec spec) {
            throw new UnsupportedOperationException();
        }
    }
}
