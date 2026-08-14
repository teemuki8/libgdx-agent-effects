package io.github.teemuki8.libgdx.agent.effects.mcp;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class EffectsToolHandlerTest {
    @Test
    void leavesBackendOwnerThreadBeforeEncodingAndDelivery() throws Exception {
        ExecutorService renderExecutor = Executors.newSingleThreadExecutor();
        AtomicReference<Thread> invoked = new AtomicReference<>();
        AtomicReference<Thread> response = new AtomicReference<>();
        AtomicReference<CompletableFuture<Results.CompileResult>> backendResult =
            new AtomicReference<>();
        CountDownLatch requested = new CountDownLatch(1);
        EffectsBackend backend = new EffectsBackend() {
            @Override public CompletionStage<Results.CompileResult> compile(String effectName) {
                CompletableFuture<Results.CompileResult> result = new CompletableFuture<>();
                backendResult.set(result);
                requested.countDown();
                return result;
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
        try (renderExecutor; EffectsToolHandler handler = new EffectsToolHandler(protocol)) {
            CompletableFuture<McpSchema.CallToolResult> responseResult = handler.handle(
                request("effect_compile", Map.of("effectName", "red")))
                .doOnNext(ignored -> response.set(Thread.currentThread()))
                .toFuture();
            assertTrue(requested.await(10, TimeUnit.SECONDS), "backend was not invoked");
            Thread owner = CompletableFuture.supplyAsync(() -> {
                invoked.set(Thread.currentThread());
                backendResult.get().complete(compileResult("red"));
                return Thread.currentThread();
            }, renderExecutor).join();
            responseResult.join();
            assertSame(owner, invoked.get(),
                "render backend must complete on the thread that owns its resources");
            assertNotSame(owner, response.get(),
                "result encoding and downstream delivery must leave the render thread");
            assertTrue(response.get().isVirtual(),
                "response must resume on the handler's virtual-thread scheduler");
        }
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
    void synchronousBackendArgumentFailureIsInternal() {
        EffectsBackend backend = new CompileBackend() {
            @Override public CompletionStage<Results.CompileResult> compile(String effectName) {
                throw new IllegalArgumentException("backend rejected its own state");
            }
        };
        EffectsProtocolService protocol = protocol(backend);
        try (EffectsToolHandler handler = new EffectsToolHandler(protocol)) {
            McpSchema.CallToolResult result = handler.handle(
                request("effect_compile", Map.of("effectName", "red"))).block();
            assertErrorCode(result, "INTERNAL_ERROR");
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

    @Test
    void importWithoutBackendIsNotAvailableAndUnknownFieldsAreRejected() {
        EffectsProtocolService protocol = protocol(null);
        try (EffectsToolHandler handler = new EffectsToolHandler(protocol)) {
            Map<String, Object> valid = Map.of(
                    "name", "glow",
                    "source", "shader_type canvas_item; void fragment(){}",
                    "targetProfiles", List.of("GLSL_ES_100"));
            assertErrorCode(handler.handle(request(
                    "effect_import_godot_canvas", valid)).block(), "NOT_AVAILABLE");
            Map<String, Object> invalid = new java.util.LinkedHashMap<>(valid);
            invalid.put("path", "/tmp/shader");
            assertErrorCode(handler.handle(request(
                    "effect_import_godot_canvas", invalid)).block(), "INVALID_QUERY");
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
