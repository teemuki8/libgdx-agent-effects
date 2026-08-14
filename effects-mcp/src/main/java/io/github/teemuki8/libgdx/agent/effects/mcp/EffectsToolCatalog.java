package io.github.teemuki8.libgdx.agent.effects.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import io.github.teemuki8.libgdx.agent.effects.core.EffectFamily;
import io.github.teemuki8.libgdx.agent.effects.protocol.EffectsProtocol;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable catalog of the closed effects tools. */
public final class EffectsToolCatalog {
    private static final List<McpSchema.Tool> TOOLS = List.of(
            tool("effect_capabilities",
                    "Report the closed v0.1 tool catalog; no arguments",
                    object(Map.of(), List.of())),
            tool("effect_list",
                    "List declared effect names; no arguments",
                    object(Map.of(), List.of())),
            tool("effect_compile",
                    "Compile one declared effect by name",
                    object(Map.of("effectName", string()), List.of("effectName"))),
            tool("effect_preview",
                    "Render one declared effect to a preview artifact by name",
                    object(Map.of("effectName", string()), List.of("effectName"))),
            tool("effect_compare",
                    "Compare two declared effects' renders by name",
                    object(Map.of(
                            "referenceName", string(),
                            "actualName", string()),
                            List.of("referenceName", "actualName"))),
            tool("effect_describe",
                    "Describe one application-declared general effect by name",
                    object(Map.of("effectName", string()), List.of("effectName"))),
            tool("effect_snapshot_summary",
                    "Summarize one declared live effect snapshot by name",
                    object(Map.of("effectName", string()), List.of("effectName"))),
            tool("effect_import_godot_canvas",
                    "Translate bounded Godot canvas shader source without persisting it",
                    object(Map.of(
                            "name", string(),
                            "source", shaderSource(),
                            "targetProfiles", targetProfiles()),
                            List.of("name", "source", "targetProfiles"))),
            tool("effect_import_particle",
                    "Translate bounded libGDX or Flame particle source without persisting it",
                    object(Map.of(
                            "schemaVersion", schemaVersion(),
                            "format", particleFormat(),
                            "name", string(),
                            "source", shaderSource(),
                            "anchorName", string(),
                            "materialName", string(),
                            "assetMappings", assetMappings()),
                            List.of("schemaVersion", "format", "name", "source",
                                    "anchorName", "materialName", "assetMappings"))),
            tool("effect_catalog_search",
                    "Search compatible registered catalog effects for explicit capabilities",
                    object(Map.of(
                            "glMajor", integer(1, 99),
                            "glMinor", integer(0, 99),
                            "maxTextureSize", integer(1, 65536),
                            "floatTextures", bool(),
                            "profile", capabilityProfile(),
                            "family", effectFamily(),
                            "tags", catalogTags(),
                            "limit", integer(1, EffectsProtocol.MAX_CATALOG_RESULTS)),
                            List.of("glMajor", "glMinor", "maxTextureSize",
                                    "floatTextures", "profile", "limit"))),
            tool("effect_catalog_get",
                    "Get one compatible registered catalog effect by ID",
                    object(Map.of(
                            "id", string(),
                            "glMajor", integer(1, 99),
                            "glMinor", integer(0, 99),
                            "maxTextureSize", integer(1, 65536),
                            "floatTextures", bool(),
                            "profile", capabilityProfile()),
                            List.of("id", "glMajor", "glMinor", "maxTextureSize",
                                    "floatTextures", "profile"))));
    private static final Map<String, McpSchema.Tool> BY_NAME = index(TOOLS);

    /** Returns the exact closed tool names. */
    public static Set<String> toolNames() {
        return BY_NAME.keySet();
    }

    /** Returns the tools in stable catalog order. */
    public List<McpSchema.Tool> tools() {
        return TOOLS;
    }

    /** Resolves one approved tool. */
    public McpSchema.Tool tool(String name) {
        McpSchema.Tool tool = BY_NAME.get(name);
        if (tool == null) {
            throw new IllegalArgumentException("unknown effects tool");
        }
        return tool;
    }

    private static Map<String, McpSchema.Tool> index(List<McpSchema.Tool> tools) {
        LinkedHashMap<String, McpSchema.Tool> index = new LinkedHashMap<>();
        tools.forEach(tool -> index.put(tool.name(), tool));
        return Map.copyOf(index);
    }

    private static McpSchema.Tool tool(
            String name, String description, Map<String, Object> input) {
        return McpSchema.Tool.builder(name, input).description(description).build();
    }

    private static Map<String, Object> object(
            Map<String, Object> properties, List<String> required) {
        LinkedHashMap<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.copyOf(properties));
        if (!required.isEmpty()) {
            schema.put("required", List.copyOf(required));
        }
        schema.put("additionalProperties", false);
        return Map.copyOf(schema);
    }

    private static Map<String, Object> string() {
        return Map.of("type", "string", "minLength", 1,
                "maxLength", EffectsProtocol.MAX_IDENTIFIER_CHARS);
    }

    private static Map<String, Object> shaderSource() {
        return Map.of("type", "string", "minLength", 1,
                "maxLength", EffectsProtocol.MAX_SHADER_IMPORT_SOURCE_CHARS);
    }

    private static Map<String, Object> targetProfiles() {
        return Map.of(
                "type", "array",
                "minItems", 1,
                "maxItems", 2,
                "uniqueItems", true,
                "items", Map.of("type", "string", "enum",
                        List.of("GLSL_ES_100", "GLSL_ES_300")));
    }

    private static Map<String, Object> schemaVersion() {
        return Map.of("type", "string", "enum", List.of("1"));
    }

    private static Map<String, Object> particleFormat() {
        return Map.of("type", "string", "enum", List.of("LIBGDX_2D", "FLAME"));
    }

    private static Map<String, Object> assetMappings() {
        return Map.of(
                "type", "object",
                "maxProperties", 256,
                "additionalProperties", string());
    }

    private static Map<String, Object> integer(int minimum, int maximum) {
        return Map.of("type", "integer", "minimum", minimum, "maximum", maximum);
    }

    private static Map<String, Object> bool() {
        return Map.of("type", "boolean");
    }

    private static Map<String, Object> capabilityProfile() {
        return Map.of("type", "string", "enum",
                List.of("DESKTOP_OPENGL", "OPENGL_ES", "WEBGL"));
    }

    private static Map<String, Object> effectFamily() {
        return Map.of("type", "string", "enum",
                java.util.Arrays.stream(EffectFamily.values()).map(Enum::name).toList());
    }

    private static Map<String, Object> catalogTags() {
        return Map.of("type", "array", "maxItems", EffectsProtocol.MAX_CATALOG_TAGS,
                "uniqueItems", true, "items", string());
    }
}
