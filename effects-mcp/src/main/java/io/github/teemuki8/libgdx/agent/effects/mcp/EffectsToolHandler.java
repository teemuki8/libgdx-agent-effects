package io.github.teemuki8.libgdx.agent.effects.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCapabilities;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalog;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalogQuery;
import io.github.teemuki8.libgdx.agent.effects.core.EffectFamily;
import io.github.teemuki8.libgdx.agent.effects.core.PixelComparisonSpec;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderTargetProfile;
import io.github.teemuki8.libgdx.agent.effects.protocol.EffectsBackend;
import io.github.teemuki8.libgdx.agent.effects.protocol.EffectsImportBackend;
import io.github.teemuki8.libgdx.agent.effects.protocol.EffectsJson;
import io.github.teemuki8.libgdx.agent.effects.protocol.EffectsProtocolService;
import io.github.teemuki8.libgdx.agent.effects.protocol.Requests;
import io.github.teemuki8.libgdx.agent.effects.protocol.Results;
import io.github.teemuki8.libgdx.agent.effects.protocol.ParticleSourceFormat;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Maps each MCP call to the effects protocol service.
 *
 * <p>{@code effect_capabilities} and {@code effect_list} answer from the closed catalog and
 * the declared-effect registry. {@code effect_compile}, {@code effect_preview}, and
 * {@code effect_compare} resolve their named effect(s) through the registry and delegate to
 * the wired {@link EffectsBackend} when one is present; otherwise they answer a typed
 * {@code NOT_AVAILABLE} error (effects-mcp does not depend on libGDX — the backend is an
 * interface in effects-protocol implemented by the render fixture).
 */
public final class EffectsToolHandler implements AutoCloseable {
    private static final ObjectMapper MAPPER = EffectsJson.mapper();
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE =
            new TypeReference<>() {};

    /** The closed {@code effect_compare} schema has no spec arguments; compare whole images at
     * zero tolerance. */
    private static final PixelComparisonSpec DEFAULT_COMPARE_SPEC =
            new PixelComparisonSpec(0, List.of(), List.of());

    private final EffectsProtocolService protocol;
    private final EffectsToolCatalog catalog;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Scheduler scheduler = Schedulers.fromExecutorService(executor);

    /** Creates a handler over one protocol service. */
    public EffectsToolHandler(EffectsProtocolService protocol) {
        this(protocol, new EffectsToolCatalog());
    }

    EffectsToolHandler(EffectsProtocolService protocol, EffectsToolCatalog catalog) {
        this.protocol = Objects.requireNonNull(protocol, "protocol");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    /** Validates and invokes one approved tool asynchronously. */
    public Mono<McpSchema.CallToolResult> handle(McpSchema.CallToolRequest call) {
        Objects.requireNonNull(call, "call");
        return Mono.defer(() -> handleAsynchronously(call))
                .subscribeOn(scheduler)
                .onErrorResume(failure -> Mono.just(failure(failure)));
    }

    private Mono<McpSchema.CallToolResult> handleAsynchronously(
            McpSchema.CallToolRequest call) {
        Map<String, Object> arguments = call.arguments() == null ? Map.of() : call.arguments();
        McpSchema.Tool tool;
        try {
            tool = catalog.tool(call.name());
        } catch (IllegalArgumentException failure) {
            return Mono.just(error("INVALID_QUERY", "unknown effects tool"));
        }
        var validation =
                McpJsonDefaults.getSchemaValidator().validate(tool.inputSchema(), arguments);
        if (!validation.valid()) {
            return Mono.just(error("INVALID_QUERY",
                    "arguments do not match the closed tool schema"));
        }
        try {
            validateDecodedArguments(call.name(), arguments);
        } catch (ClassCastException | IllegalArgumentException failure) {
            return Mono.just(error("INVALID_QUERY", "arguments could not be decoded"));
        }
        return call(call.name(), arguments);
    }

    private static void validateDecodedArguments(
            String toolName, Map<String, Object> arguments) {
        switch (toolName) {
            case "effect_compile", "effect_preview", "effect_describe",
                    "effect_snapshot_summary" -> string(arguments, "effectName");
            case "effect_compare" -> {
                string(arguments, "referenceName");
                string(arguments, "actualName");
            }
            case "effect_import_godot_canvas" -> {
                string(arguments, "name");
                string(arguments, "source");
                targetProfiles(arguments);
            }
            case "effect_import_particle" -> {
                string(arguments, "schemaVersion");
                ParticleSourceFormat.valueOf(string(arguments, "format"));
                string(arguments, "name");
                string(arguments, "source");
                string(arguments, "anchorName");
                string(arguments, "materialName");
                assetMappings(arguments);
            }
            case "effect_catalog_search" -> catalogSearchRequest(arguments);
            case "effect_catalog_get" -> catalogLookupRequest(arguments);
            default -> {
                // Tools without arguments have nothing further to decode.
            }
        }
    }

    private Mono<McpSchema.CallToolResult> call(
            String toolName, Map<String, Object> arguments) {
        return switch (toolName) {
            case "effect_capabilities" -> resultAsync(new Results.CapabilitiesResult(
                    catalog.tools().stream().map(McpSchema.Tool::name).toList()));
            case "effect_list" -> resultAsync(new Results.ListResult(protocol.effectNames()));
            case "effect_compile" -> compile(arguments);
            case "effect_preview" -> preview(arguments);
            case "effect_compare" -> compare(arguments);
            case "effect_describe" -> describe(arguments);
            case "effect_snapshot_summary" -> snapshotSummary(arguments);
            case "effect_import_godot_canvas" -> importGodotCanvas(arguments);
            case "effect_import_particle" -> importParticle(arguments);
            case "effect_catalog_search" -> catalogSearch(arguments);
            case "effect_catalog_get" -> catalogGet(arguments);
            default -> throw new IllegalArgumentException("unknown effects tool");
        };
    }

    private Mono<McpSchema.CallToolResult> compile(Map<String, Object> arguments) {
        String name = string(arguments, "effectName");
        if (!protocol.isDeclared(name)) {
            return Mono.just(error("UNKNOWN_EFFECT", "effect is not declared: " + name));
        }
        if (protocol.effect(name) == null) {
            return Mono.just(error("UNSUPPORTED_FEATURE",
                    "effect_compile currently accepts declared legacy shaders only"));
        }
        EffectsBackend backend = protocol.backend();
        if (backend == null) {
            return Mono.just(error("NOT_AVAILABLE",
                    "effect_compile needs the render fixture, not wired in v0.1"));
        }
        return Mono.fromCompletionStage(backend.compile(name))
                .publishOn(scheduler)
                .flatMap(this::resultAsync);
    }

    private Mono<McpSchema.CallToolResult> preview(Map<String, Object> arguments) {
        String name = string(arguments, "effectName");
        if (!protocol.isDeclared(name)) {
            return Mono.just(error("UNKNOWN_EFFECT", "effect is not declared: " + name));
        }
        if (protocol.effect(name) == null) {
            return Mono.just(error("UNSUPPORTED_FEATURE",
                    "effect_preview currently accepts declared legacy shaders only"));
        }
        EffectsBackend backend = protocol.backend();
        if (backend == null) {
            return Mono.just(error("NOT_AVAILABLE",
                    "effect_preview needs the render fixture, not wired in v0.1"));
        }
        return Mono.fromCompletionStage(backend.preview(name))
                .publishOn(scheduler)
                .flatMap(this::resultAsync);
    }

    private Mono<McpSchema.CallToolResult> compare(Map<String, Object> arguments) {
        String reference = string(arguments, "referenceName");
        String actual = string(arguments, "actualName");
        if (!protocol.isDeclared(reference)) {
            return Mono.just(error("UNKNOWN_EFFECT", "effect is not declared: " + reference));
        }
        if (!protocol.isDeclared(actual)) {
            return Mono.just(error("UNKNOWN_EFFECT", "effect is not declared: " + actual));
        }
        if (protocol.effect(reference) == null || protocol.effect(actual) == null) {
            return Mono.just(error("UNSUPPORTED_FEATURE",
                    "effect_compare currently accepts declared legacy shaders only"));
        }
        EffectsBackend backend = protocol.backend();
        if (backend == null) {
            return Mono.just(error("NOT_AVAILABLE",
                    "effect_compare needs the render fixture, not wired in v0.1"));
        }
        return Mono.fromCompletionStage(
                backend.compare(reference, actual, DEFAULT_COMPARE_SPEC))
                .publishOn(scheduler)
                .flatMap(this::resultAsync);
    }

    private Mono<McpSchema.CallToolResult> importGodotCanvas(Map<String, Object> arguments) {
        EffectsImportBackend backend = protocol.importBackend();
        if (backend == null) {
            return Mono.just(error("NOT_AVAILABLE",
                    "effect_import_godot_canvas needs an import backend"));
        }
        Requests.ImportGodotCanvasRequest request = new Requests.ImportGodotCanvasRequest(
                string(arguments, "name"), string(arguments, "source"),
                targetProfiles(arguments));
        return Mono.fromCompletionStage(backend.importGodotCanvas(request))
                .publishOn(scheduler)
                .flatMap(this::resultAsync);
    }

    private Mono<McpSchema.CallToolResult> describe(Map<String, Object> arguments) {
        String name = string(arguments, "effectName");
        Results.EffectSummaryResult summary = protocol.effectSummary(name);
        if (summary == null) {
            return Mono.just(error("UNKNOWN_EFFECT", "effect is not declared: " + name));
        }
        return resultAsync(summary);
    }

    private Mono<McpSchema.CallToolResult> snapshotSummary(Map<String, Object> arguments) {
        String name = string(arguments, "effectName");
        if (!protocol.isDeclared(name)) {
            return Mono.just(error("UNKNOWN_EFFECT", "effect is not declared: " + name));
        }
        EffectsBackend backend = protocol.backend();
        if (backend == null) {
            return Mono.just(error("NOT_AVAILABLE",
                    "effect_snapshot_summary needs an application runtime backend"));
        }
        return Mono.fromCompletionStage(backend.snapshotSummary(name))
                .publishOn(scheduler)
                .flatMap(this::resultAsync);
    }

    private Mono<McpSchema.CallToolResult> importParticle(Map<String, Object> arguments) {
        EffectsImportBackend backend = protocol.importBackend();
        if (backend == null) {
            return Mono.just(error("NOT_AVAILABLE",
                    "effect_import_particle needs an import backend"));
        }
        Requests.ImportParticleRequest request = new Requests.ImportParticleRequest(
                string(arguments, "schemaVersion"),
                ParticleSourceFormat.valueOf(string(arguments, "format")),
                string(arguments, "name"), string(arguments, "source"),
                string(arguments, "anchorName"), string(arguments, "materialName"),
                assetMappings(arguments));
        return Mono.fromCompletionStage(backend.importParticle(request))
                .publishOn(scheduler)
                .flatMap(this::resultAsync);
    }

    private Mono<McpSchema.CallToolResult> catalogSearch(Map<String, Object> arguments) {
        EffectCatalog effectCatalog = protocol.catalog();
        if (effectCatalog == null) {
            return Mono.just(error("NOT_AVAILABLE",
                    "effect_catalog_search needs an application-registered catalog"));
        }
        Requests.CatalogSearchRequest request = catalogSearchRequest(arguments);
        var search = effectCatalog.search(new EffectCatalogQuery(request.target(),
                request.family(), request.tags(), request.limit()));
        return resultAsync(new Results.CatalogSearchResult(
                search.matches(), search.truncated()));
    }

    private Mono<McpSchema.CallToolResult> catalogGet(Map<String, Object> arguments) {
        EffectCatalog effectCatalog = protocol.catalog();
        if (effectCatalog == null) {
            return Mono.just(error("NOT_AVAILABLE",
                    "effect_catalog_get needs an application-registered catalog"));
        }
        Requests.CatalogLookupRequest request = catalogLookupRequest(arguments);
        return effectCatalog.find(request.id(), request.target())
                .<Mono<McpSchema.CallToolResult>>map(match -> resultAsync(
                        new Results.CatalogLookupResult(match)))
                .orElseGet(() -> Mono.just(error("UNKNOWN_EFFECT",
                        "effect is unavailable for the requested target")));
    }

    private Mono<McpSchema.CallToolResult> resultAsync(Object value) {
        return Mono.fromCallable(() -> result(value));
    }

    private static McpSchema.CallToolResult result(Object value) throws JsonProcessingException {
        LinkedHashMap<String, Object> content = MAPPER.convertValue(value, MAP_TYPE);
        return McpSchema.CallToolResult.builder()
                .structuredContent(Map.copyOf(content))
                .addTextContent(MAPPER.writeValueAsString(content))
                .isError(false)
                .build();
    }

    private static McpSchema.CallToolResult error(String code, String message) {
        Map<String, Object> content =
                Map.of("kind", "error", "code", code, "message", message);
        return McpSchema.CallToolResult.builder()
                .structuredContent(content)
                .addTextContent(code + ": " + message)
                .isError(true)
                .build();
    }

    private static McpSchema.CallToolResult failure(Throwable failure) {
        Throwable cause = unwrap(failure);
        if (cause instanceof EffectsException effectsFailure) {
            return error(effectsFailure.kind().name(), effectsFailure.getMessage());
        }
        if (cause instanceof JsonProcessingException) {
            return error("INTERNAL_ERROR", "result could not be encoded");
        }
        return error("INTERNAL_ERROR", "backend operation failed");
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static String string(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? null : (String) value;
    }

    private static List<ShaderTargetProfile> targetProfiles(Map<String, Object> values) {
        Object value = values.get("targetProfiles");
        if (!(value instanceof List<?> items)) {
            throw new IllegalArgumentException("targetProfiles must be an array");
        }
        return items.stream().map(item -> ShaderTargetProfile.valueOf((String) item)).toList();
    }

    private static Map<String, String> assetMappings(Map<String, Object> values) {
        Object value = values.get("assetMappings");
        if (!(value instanceof Map<?, ?> items)) {
            throw new IllegalArgumentException("assetMappings must be an object");
        }
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        items.forEach((key, item) -> result.put((String) key, (String) item));
        return Map.copyOf(result);
    }

    private static Requests.CatalogSearchRequest catalogSearchRequest(
            Map<String, Object> values) {
        String familyName = string(values, "family");
        EffectFamily family = familyName == null ? null : EffectFamily.valueOf(familyName);
        return new Requests.CatalogSearchRequest(capabilities(values), family,
                stringList(values, "tags"), integer(values, "limit"));
    }

    private static Requests.CatalogLookupRequest catalogLookupRequest(
            Map<String, Object> values) {
        return new Requests.CatalogLookupRequest(string(values, "id"), capabilities(values));
    }

    private static EffectCapabilities capabilities(Map<String, Object> values) {
        return new EffectCapabilities(integer(values, "glMajor"), integer(values, "glMinor"),
                integer(values, "maxTextureSize"), bool(values, "floatTextures"),
                EffectCapabilities.Profile.valueOf(string(values, "profile")));
    }

    private static int integer(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        return number.intValue();
    }

    private static boolean bool(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof Boolean result)) {
            throw new IllegalArgumentException(key + " must be a boolean");
        }
        return result;
    }

    private static List<String> stringList(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> items)) {
            throw new IllegalArgumentException(key + " must be an array");
        }
        return items.stream().map(item -> (String) item).toList();
    }

    /** Stops owned request dispatch. */
    @Override public void close() {
        scheduler.dispose();
        executor.shutdownNow();
    }
}
