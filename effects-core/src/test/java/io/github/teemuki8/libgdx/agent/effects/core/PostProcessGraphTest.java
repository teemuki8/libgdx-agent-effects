package io.github.teemuki8.libgdx.agent.effects.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostProcessGraphTest {

    @Test
    void computesStableTopologicalOrderAndDefensivelyCopiesInputs() {
        List<RenderPassDefinition> source = new ArrayList<>(List.of(
                pass("combine", List.of("blurred", "scene"), "final"),
                pass("blur", List.of("scene"), "blurred")));
        PostProcessGraphDefinition graph = new PostProcessGraphDefinition("graph",
                List.of("scene"), source, "final", 2);
        source.clear();
        assertEquals(List.of("blur", "combine"), graph.executionOrder().stream()
                .map(RenderPassDefinition::name).toList());
        assertEquals(2, graph.passes().size());
    }

    @Test
    void rejectsCyclesUnknownInputsDuplicateOutputsAndMissingFinalOutput() {
        assertThrows(IllegalArgumentException.class, () -> new PostProcessGraphDefinition(
                "cycle", List.of(), List.of(
                        pass("a", List.of("b-out"), "a-out"),
                        pass("b", List.of("a-out"), "b-out")), "a-out", 2));
        assertThrows(IllegalArgumentException.class, () -> new PostProcessGraphDefinition(
                "unknown", List.of("scene"), List.of(
                        pass("a", List.of("missing"), "out")), "out", 1));
        assertThrows(IllegalArgumentException.class, () -> new PostProcessGraphDefinition(
                "duplicate", List.of("scene"), List.of(
                        pass("a", List.of("scene"), "out"),
                        pass("b", List.of("scene"), "out")), "out", 2));
        assertThrows(IllegalArgumentException.class, () -> new PostProcessGraphDefinition(
                "missing-final", List.of("scene"), List.of(
                        pass("a", List.of("scene"), "out")), "other", 1));
    }

    @Test
    void validatesConfiguredPassBound() {
        PostProcessGraphDefinition graph = new PostProcessGraphDefinition("graph",
                List.of("scene"), List.of(pass("copy", List.of("scene"), "out")),
                "out", 1);
        EffectsLimits limits = new EffectsLimits(1024, 8, 1, 1024, 1024, 64, 64, 4);
        assertEquals(graph, graph.validate(limits));
        assertThrows(EffectsException.class, () -> new PostProcessGraphDefinition("too-many",
                List.of("scene"), List.of(
                        pass("a", List.of("scene"), "a-out"),
                        pass("b", List.of("a-out"), "b-out")), "b-out", 2)
                .validate(limits));
    }

    private static RenderPassDefinition pass(String name, List<String> inputs, String output) {
        Material2dDefinition material = new Material2dDefinition(name + "-material",
                new ShaderSource("void main(){}", "void main(){}"),
                BlendMode.NORMAL, List.of(), inputs.stream().map(AssetKey::new).toList());
        return new RenderPassDefinition(name, material, inputs, output);
    }
}
