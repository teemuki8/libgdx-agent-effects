package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.List;

final class CatalogTestFixtures {
    private CatalogTestFixtures() {}

    static TrailDefinition trail(String name) {
        return new TrailDefinition(name, "anchor", material(name + "-material"),
                new FloatCurve(List.of(new FloatCurve.Stop(0f, 1f))),
                new ColorGradient(List.of(
                        new ColorGradient.Stop(0f, 1f, 1f, 1f, 1f))),
                0.1f, 0f, 2, 1f, TrailJoin.MITER, TrailCap.BUTT,
                TrailUvMode.STRETCH, 2f);
    }

    static PostProcessGraphDefinition postProcessGraph(String name) {
        Material2dDefinition material = new Material2dDefinition(name + "-material",
                new ShaderSource("void main(){}", "void main(){}"),
                BlendMode.NORMAL, List.of(), List.of(new AssetKey("scene")));
        RenderPassDefinition pass = new RenderPassDefinition("copy", material,
                List.of("scene"), "output");
        return new PostProcessGraphDefinition(name, List.of("scene"),
                List.of(pass), "output", 1);
    }

    static EffectCatalogVariant variant(EffectDefinition definition,
            EffectCapabilities... qualifiedTargets) {
        return new EffectCatalogVariant("portable", 0, definition,
                List.of(qualifiedTargets));
    }

    static EffectCatalogEntry entry(String id, List<String> tags,
            EffectCatalogVariant... variants) {
        return new EffectCatalogEntry(id, "1.0.0", "Ship trail",
                "A reusable ship trail.", EffectFamily.from(variants[0].definition()),
                tags, "Apache-2.0", "Original project content", null,
                List.of(), List.of(variants));
    }

    static EffectCapabilities desktopGl2() {
        return new EffectCapabilities(2, 0, 2048, false,
                EffectCapabilities.Profile.DESKTOP_OPENGL);
    }

    private static Material2dDefinition material(String name) {
        return new Material2dDefinition(name,
                new ShaderSource("void main(){}", "void main(){}"),
                BlendMode.NORMAL, List.of(), List.of());
    }
}
