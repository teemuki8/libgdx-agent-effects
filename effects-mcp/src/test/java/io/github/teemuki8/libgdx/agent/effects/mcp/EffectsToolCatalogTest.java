package io.github.teemuki8.libgdx.agent.effects.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EffectsToolCatalogTest {
    @Test
    void exposesClosedGeneralVfxTools() {
        Set<String> tools = EffectsToolCatalog.toolNames();
        assertEquals(Set.of("effect_capabilities", "effect_list", "effect_compile",
            "effect_preview", "effect_compare", "effect_import_godot_canvas",
            "effect_describe", "effect_snapshot_summary", "effect_import_particle",
            "effect_catalog_search", "effect_catalog_get"), tools);
        assertEquals(List.of("effect_capabilities", "effect_list", "effect_compile",
                "effect_preview", "effect_compare", "effect_describe",
                "effect_snapshot_summary", "effect_import_godot_canvas",
                "effect_import_particle", "effect_catalog_search", "effect_catalog_get"),
                new EffectsToolCatalog().tools().stream().map(tool -> tool.name()).toList());
    }

    @Test
    void catalogSchemasAreClosedAndUseFlatCapabilities() {
        EffectsToolCatalog catalog = new EffectsToolCatalog();
        for (String name : List.of("effect_catalog_search", "effect_catalog_get")) {
            Map<?, ?> schema = catalog.tool(name).inputSchema();
            assertEquals(false, schema.get("additionalProperties"));
            Map<?, ?> properties = (Map<?, ?>) schema.get("properties");
            assertFalse(properties.containsKey("target"));
            assertTrue(properties.keySet().containsAll(List.of(
                    "glMajor", "glMinor", "maxTextureSize", "floatTextures", "profile")));
            List<?> required = (List<?>) schema.get("required");
            assertFalse(required.contains("family"));
            assertFalse(required.contains("tags"));
        }
    }
}
