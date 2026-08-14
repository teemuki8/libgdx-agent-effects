package io.github.teemuki8.libgdx.agent.effects.library;

import io.github.teemuki8.libgdx.agent.effects.core.BlendMode;
import io.github.teemuki8.libgdx.agent.effects.core.CatalogLimits;
import io.github.teemuki8.libgdx.agent.effects.core.ColorGradient;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCapabilities;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalogEntry;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalogVariant;
import io.github.teemuki8.libgdx.agent.effects.core.EffectFamily;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.FloatCurve;
import io.github.teemuki8.libgdx.agent.effects.core.Material2dDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleCapacityPolicy;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderSource;
import io.github.teemuki8.libgdx.agent.effects.core.TrailCap;
import io.github.teemuki8.libgdx.agent.effects.core.TrailDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.TrailJoin;
import io.github.teemuki8.libgdx.agent.effects.core.TrailUvMode;
import java.util.List;

final class LibraryTestFixtures {
    static final CatalogLimits CATALOG_LIMITS = new CatalogLimits(16, 8, 8, 8, 1024);
    static final EffectsLimits EFFECTS_LIMITS = EffectsLimits.developmentDefaults();

    private LibraryTestFixtures() {}

    static InMemoryEffectCatalog mixed() {
        return new InMemoryEffectCatalog(
                List.of(gpuSparks(), wideTrail(), shipTrail()),
                CATALOG_LIMITS, EFFECTS_LIMITS);
    }

    static EffectCatalogEntry shipTrail() {
        TrailDefinition definition = trail("ship-trail");
        return entry("ship-trail", EffectFamily.TRAIL, List.of("space", "trail"),
                List.of(
                        variant("portable", 10, definition, desktopGl2()),
                        variant("alpha", 0, definition, desktopGl2()),
                        variant("beta", 0, definition, desktopGl2()),
                        variant("enhanced", 0, definition, desktopGl4())));
    }

    static EffectCatalogEntry wideTrail() {
        TrailDefinition definition = trail("wide-trail");
        return entry("wide-trail", EffectFamily.TRAIL, List.of("space", "trail"),
                List.of(variant("portable", 0, definition, desktopGl2())));
    }

    static EffectCatalogEntry gpuSparks() {
        Material2dDefinition material = material("gpu-sparks-material");
        ParticleDefinition definition = new ParticleDefinition("gpu-sparks", "emitter",
                material, 16, 4f, 1f, 1f, curve(), gradient(), List.of(),
                ParticleCapacityPolicy.DROP_NEWEST);
        EffectCapabilities gles3 = new EffectCapabilities(3, 0, 4096, false,
                EffectCapabilities.Profile.OPENGL_ES);
        return entry("gpu-sparks", EffectFamily.PARTICLE, List.of("particle", "space"),
                List.of(variant("gpu", 0, definition, gles3)));
    }

    static EffectCapabilities desktopGl2() {
        return new EffectCapabilities(2, 0, 2048, false,
                EffectCapabilities.Profile.DESKTOP_OPENGL);
    }

    private static EffectCapabilities desktopGl4() {
        return new EffectCapabilities(4, 1, 8192, true,
                EffectCapabilities.Profile.DESKTOP_OPENGL);
    }

    private static TrailDefinition trail(String name) {
        return new TrailDefinition(name, "anchor", material(name + "-material"),
                curve(), gradient(), 0.1f, 0f, 8, 1f, TrailJoin.MITER,
                TrailCap.BUTT, TrailUvMode.STRETCH, 2f);
    }

    private static Material2dDefinition material(String name) {
        return new Material2dDefinition(name,
                new ShaderSource("void main(){}", "void main(){}"),
                BlendMode.NORMAL, List.of(), List.of());
    }

    private static FloatCurve curve() {
        return new FloatCurve(List.of(new FloatCurve.Stop(0f, 1f)));
    }

    private static ColorGradient gradient() {
        return new ColorGradient(List.of(
                new ColorGradient.Stop(0f, 1f, 1f, 1f, 1f)));
    }

    private static EffectCatalogVariant variant(String id, int preference,
            io.github.teemuki8.libgdx.agent.effects.core.EffectDefinition definition,
            EffectCapabilities target) {
        return new EffectCatalogVariant(id, preference, definition, List.of(target));
    }

    private static EffectCatalogEntry entry(String id, EffectFamily family,
            List<String> tags, List<EffectCatalogVariant> variants) {
        return new EffectCatalogEntry(id, "1.0.0", id, "Test effect " + id,
                family, tags, "Apache-2.0", "Test fixture", null,
                List.of(), variants);
    }
}
