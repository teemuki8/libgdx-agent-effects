package io.github.teemuki8.libgdx.agent.effects.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable catalog of the six closed effects tools. */
public final class EffectsToolCatalog {
    private static final int MAX_IDENTIFIER = 256;

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
            tool("effect_import_godot_canvas",
                    "Translate bounded Godot canvas shader source without persisting it",
                    object(Map.of(
                            "name", string(),
                            "source", shaderSource(),
                            "targetProfiles", targetProfiles()),
                            List.of("name", "source", "targetProfiles"))));
    private static final Map<String, McpSchema.Tool> BY_NAME = index(TOOLS);

    /** Returns the exact six tool names. */
    public static Set<String> toolNames() {
        return BY_NAME.keySet();
    }

    /** Returns the six tools in stable catalog order. */
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
        return Map.of("type", "string", "minLength", 1, "maxLength", MAX_IDENTIFIER);
    }

    private static Map<String, Object> shaderSource() {
        return Map.of("type", "string", "minLength", 1, "maxLength", 64 * 1024);
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
}
