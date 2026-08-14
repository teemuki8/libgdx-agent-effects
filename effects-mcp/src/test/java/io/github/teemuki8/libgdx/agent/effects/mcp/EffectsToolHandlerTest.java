package io.github.teemuki8.libgdx.agent.effects.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.teemuki8.libgdx.agent.effects.core.EffectDescription;
import io.github.teemuki8.libgdx.agent.effects.core.BlendMode;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCapabilities;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalog;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalogEntry;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalogMatch;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalogQuery;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalogSearchResult;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalogVariant;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.EffectFamily;
import io.github.teemuki8.libgdx.agent.effects.core.PixelComparisonSpec;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderDiagnostic;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderSource;
import io.github.teemuki8.libgdx.agent.effects.core.Material2dDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.FidelityClassification;
import io.github.teemuki8.libgdx.agent.effects.protocol.EffectsBackend;
import io.github.teemuki8.libgdx.agent.effects.protocol.EffectsImportBackend;
import io.github.teemuki8.libgdx.agent.effects.protocol.EffectsProtocolService;
import io.github.teemuki8.libgdx.agent.effects.protocol.Requests;
import io.github.teemuki8.libgdx.agent.effects.protocol.Results;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    @Test
    void describesApplicationDeclaredGeneralEffectWithoutExposingExecutableObjects() {
        Material2dDefinition material = new Material2dDefinition("ship-material",
                new ShaderSource("void main(){}", "void main(){}"), BlendMode.ADDITIVE,
                List.of(), List.of());
        EffectsProtocolService protocol = new EffectsProtocolService()
                .declareDefinition(material);
        try (EffectsToolHandler handler = new EffectsToolHandler(protocol)) {
            McpSchema.CallToolResult result = handler.handle(request(
                    "effect_describe", Map.of("effectName", "ship-material"))).block();
            assertTrue(!result.isError());
            @SuppressWarnings("unchecked")
            Map<String, Object> structured = (Map<String, Object>) result.structuredContent();
            org.junit.jupiter.api.Assertions.assertEquals("MATERIAL_2D",
                    structured.get("family"));
        }
    }

    @Test
    void summarizesRegisteredRuntimeSnapshotThroughBackend() {
        EffectsBackend backend = new CompileBackend() {
            @Override public CompletionStage<Results.CompileResult> compile(String effectName) {
                throw new UnsupportedOperationException();
            }

            @Override public CompletionStage<Results.SnapshotSummaryResult> snapshotSummary(
                    String effectName) {
                return CompletableFuture.completedFuture(new Results.SnapshotSummaryResult(
                        effectName, EffectFamily.MATERIAL_2D, 4L, 2, 0L,
                        List.of("anchors=2")));
            }
        };
        Material2dDefinition material = material();
        EffectsProtocolService protocol = new EffectsProtocolService()
                .declareDefinition(material).backend(backend);
        try (EffectsToolHandler handler = new EffectsToolHandler(protocol)) {
            McpSchema.CallToolResult result = handler.handle(request(
                    "effect_snapshot_summary", Map.of("effectName", material.name()))).block();
            assertTrue(!result.isError());
            org.junit.jupiter.api.Assertions.assertEquals(4L,
                    ((Map<?, ?>) result.structuredContent()).get("stepOrGeneration"));
        }
    }

    @Test
    void importsParticleSourceThroughClosedImportBackend() {
        EffectsImportBackend backend = new EffectsImportBackend() {
            @Override public CompletionStage<Results.ImportShaderResult> importGodotCanvas(
                    Requests.ImportGodotCanvasRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override public CompletionStage<Results.ImportParticleResult> importParticle(
                    Requests.ImportParticleRequest request) {
                return CompletableFuture.completedFuture(new Results.ImportParticleResult(
                        request.name(), FidelityClassification.APPROXIMATED, 8, List.of()));
            }
        };
        EffectsProtocolService protocol = new EffectsProtocolService().importBackend(backend);
        try (EffectsToolHandler handler = new EffectsToolHandler(protocol)) {
            McpSchema.CallToolResult result = handler.handle(request("effect_import_particle",
                    Map.of("schemaVersion", "1", "format", "LIBGDX_2D", "name", "sparks",
                            "source", "- Duration -\nlowMin: 1", "anchorName", "ship",
                            "materialName", "ship-material", "assetMappings", Map.of()))).block();
            assertTrue(!result.isError());
            org.junit.jupiter.api.Assertions.assertEquals("APPROXIMATED",
                    ((Map<?, ?>) result.structuredContent()).get("fidelity"));
        }
    }

    @Test
    void generalDefinitionsDoNotFallThroughLegacyCompileBackend() {
        EffectsBackend backend = new CompileBackend() {
            @Override public CompletionStage<Results.CompileResult> compile(String effectName) {
                throw new AssertionError("general definition reached legacy compiler");
            }
        };
        EffectsProtocolService protocol = new EffectsProtocolService()
                .declareDefinition(material()).backend(backend);
        try (EffectsToolHandler handler = new EffectsToolHandler(protocol)) {
            assertErrorCode(handler.handle(request("effect_compile",
                    Map.of("effectName", "ship-material"))).block(), "UNSUPPORTED_FEATURE");
        }
    }

    @Test
    void catalogToolsRequireApplicationRegistration() {
        try (EffectsToolHandler handler = new EffectsToolHandler(new EffectsProtocolService())) {
            assertErrorCode(handler.handle(request("effect_catalog_search",
                    catalogSearchArguments("DESKTOP_OPENGL"))).block(), "NOT_AVAILABLE");
            assertErrorCode(handler.handle(request("effect_catalog_get",
                    catalogLookupArguments("ship-trail", "DESKTOP_OPENGL"))).block(),
                    "NOT_AVAILABLE");
        }
    }

    @Test
    void catalogSearchDelegatesNormalizedTargetAwareQueryAndRejectsUnknownFields() {
        RecordingCatalog catalog = new RecordingCatalog();
        EffectsProtocolService protocol = new EffectsProtocolService().catalog(catalog);
        try (EffectsToolHandler handler = new EffectsToolHandler(protocol)) {
            McpSchema.CallToolResult result = handler.handle(request("effect_catalog_search",
                    catalogSearchArguments("DESKTOP_OPENGL"))).block();
            assertTrue(!result.isError());
            assertEquals(List.of("space", "trail"), catalog.lastQuery.tags());
            assertEquals(EffectCapabilities.Profile.DESKTOP_OPENGL,
                    catalog.lastQuery.target().profile());
            List<?> matches = (List<?>) ((Map<?, ?>) result.structuredContent()).get("matches");
            Map<?, ?> match = (Map<?, ?>) matches.getFirst();
            assertEquals("ship-trail", ((Map<?, ?>) match.get("entry")).get("id"));

            McpSchema.CallToolResult filtered = handler.handle(request("effect_catalog_search",
                    catalogSearchArguments("OPENGL_ES"))).block();
            assertEquals(List.of(), ((Map<?, ?>) filtered.structuredContent()).get("matches"));

            Map<String, Object> withoutTags = new java.util.LinkedHashMap<>(
                    catalogSearchArguments("DESKTOP_OPENGL"));
            withoutTags.remove("tags");
            handler.handle(request("effect_catalog_search", withoutTags)).block();
            assertEquals(List.of(), catalog.lastQuery.tags());

            Map<String, Object> invalid = new java.util.LinkedHashMap<>(
                    catalogSearchArguments("DESKTOP_OPENGL"));
            invalid.put("path", "/tmp/catalog");
            assertErrorCode(handler.handle(request("effect_catalog_search", invalid)).block(),
                    "INVALID_QUERY");
        }
    }

    @Test
    void catalogLookupHidesMissingAndIncompatibleEntries() {
        RecordingCatalog catalog = new RecordingCatalog();
        EffectsProtocolService protocol = new EffectsProtocolService().catalog(catalog);
        try (EffectsToolHandler handler = new EffectsToolHandler(protocol)) {
            McpSchema.CallToolResult found = handler.handle(request("effect_catalog_get",
                    catalogLookupArguments("ship-trail", "DESKTOP_OPENGL"))).block();
            assertTrue(!found.isError());
            Map<?, ?> match = (Map<?, ?>) ((Map<?, ?>) found.structuredContent()).get("match");
            assertEquals("ship-trail", ((Map<?, ?>) match.get("entry")).get("id"));

            assertErrorCode(handler.handle(request("effect_catalog_get",
                    catalogLookupArguments("ship-trail", "OPENGL_ES"))).block(),
                    "UNKNOWN_EFFECT");
            assertErrorCode(handler.handle(request("effect_catalog_get",
                    catalogLookupArguments("missing", "DESKTOP_OPENGL"))).block(),
                    "UNKNOWN_EFFECT");
        }
    }

    private static Map<String, Object> catalogSearchArguments(String profile) {
        return Map.of("glMajor", 4, "glMinor", 6, "maxTextureSize", 16384,
                "floatTextures", true, "profile", profile, "family", "MATERIAL_2D",
                "tags", List.of("trail", "space"), "limit", 8);
    }

    private static Map<String, Object> catalogLookupArguments(String id, String profile) {
        return Map.of("id", id, "glMajor", 4, "glMinor", 6,
                "maxTextureSize", 16384, "floatTextures", true, "profile", profile);
    }

    private static final class RecordingCatalog implements EffectCatalog {
        private static final EffectCapabilities QUALIFIED = new EffectCapabilities(
                3, 2, 4096, false, EffectCapabilities.Profile.DESKTOP_OPENGL);
        private static final Material2dDefinition MATERIAL = new Material2dDefinition(
                "ship-trail", new ShaderSource("void main(){}", "void main(){}"),
                BlendMode.ADDITIVE, List.of(), List.of());
        private static final EffectCatalogVariant VARIANT = new EffectCatalogVariant(
                "desktop", 0, MATERIAL, List.of(QUALIFIED));
        private static final EffectCatalogEntry ENTRY = new EffectCatalogEntry(
                "ship-trail", "1.0.0", "Ship Trail", "A bright ship trail",
                EffectFamily.MATERIAL_2D, List.of("space", "trail"), "Apache-2.0",
                "original", null, List.of(), List.of(VARIANT));
        private static final EffectCatalogMatch MATCH = new EffectCatalogMatch(ENTRY, VARIANT);
        private EffectCatalogQuery lastQuery;

        @Override public EffectCatalogSearchResult search(EffectCatalogQuery query) {
            lastQuery = query;
            boolean compatible = VARIANT.supports(query.target())
                    && query.family() == EffectFamily.MATERIAL_2D
                    && ENTRY.tags().containsAll(query.tags());
            return new EffectCatalogSearchResult(compatible ? List.of(MATCH) : List.of(), false);
        }

        @Override public Optional<EffectCatalogMatch> find(
                String id, EffectCapabilities target) {
            return ENTRY.id().equals(id) && VARIANT.supports(target)
                    ? Optional.of(MATCH) : Optional.empty();
        }
    }

    private static Material2dDefinition material() {
        return new Material2dDefinition("ship-material",
                new ShaderSource("void main(){}", "void main(){}"), BlendMode.ADDITIVE,
                List.of(), List.of());
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
