package io.github.teemuki8.libgdx.agent.effects.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EffectsToolCatalogTest {
    @Test
    void exposesClosedGeneralVfxTools() {
        Set<String> tools = EffectsToolCatalog.toolNames();
        assertEquals(Set.of("effect_capabilities", "effect_list", "effect_compile",
            "effect_preview", "effect_compare", "effect_import_godot_canvas",
            "effect_describe", "effect_snapshot_summary", "effect_import_particle"), tools);
        assertEquals(List.of("effect_capabilities", "effect_list", "effect_compile",
                "effect_preview", "effect_compare", "effect_describe",
                "effect_snapshot_summary", "effect_import_godot_canvas",
                "effect_import_particle"),
                new EffectsToolCatalog().tools().stream().map(tool -> tool.name()).toList());
    }
}
