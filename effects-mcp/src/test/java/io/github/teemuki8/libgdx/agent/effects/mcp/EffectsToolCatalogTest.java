package io.github.teemuki8.libgdx.agent.effects.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import org.junit.jupiter.api.Test;

class EffectsToolCatalogTest {
    @Test
    void exposesExactlyFiveTools() {
        Set<String> tools = EffectsToolCatalog.toolNames();
        assertEquals(Set.of("effect_capabilities", "effect_list", "effect_compile",
            "effect_preview", "effect_compare"), tools);
    }
}
