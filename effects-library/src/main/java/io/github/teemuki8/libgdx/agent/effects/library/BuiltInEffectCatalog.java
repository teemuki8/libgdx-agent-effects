package io.github.teemuki8.libgdx.agent.effects.library;

import io.github.teemuki8.libgdx.agent.effects.core.AssetKey;
import io.github.teemuki8.libgdx.agent.effects.core.CatalogLimits;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCapabilities;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalog;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalogEntry;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalogVariant;
import io.github.teemuki8.libgdx.agent.effects.core.EffectDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.EffectFamily;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import java.util.List;

/** Creates the small original catalog qualified by the repository native fixture. */
public final class BuiltInEffectCatalog {
    private static final EffectCapabilities DESKTOP_GL2 = new EffectCapabilities(
            2, 0, 2048, false, EffectCapabilities.Profile.DESKTOP_OPENGL);
    private static final String LICENSE = "Apache-2.0";
    private static final String PROVENANCE = "Original libgdx-agent-effects content";

    /** Creates a validated immutable catalog containing the six bundled effects. */
    public static EffectCatalog create(CatalogLimits catalogLimits, EffectsLimits effectsLimits) {
        return new InMemoryEffectCatalog(List.of(
                entry("damage-pulse", "Damage Pulse", "Animated combat damage overlay",
                        List.of("combat", "post-process"), BuiltInMaterials.damagePulse(),
                        List.of(new AssetKey("source"))),
                entry("neon-edges", "Neon Edges", "Aqua edge extraction material",
                        List.of("edges", "post-process"), BuiltInMaterials.neonEdges(),
                        List.of(new AssetKey("source"))),
                entry("ship-trail", "Ship Trail", "Warm ship exhaust ribbon",
                        List.of("space", "trail"), BuiltInGeneralEffects.shipTrail(), List.of()),
                entry("energy-beam", "Energy Beam", "Segmented combat energy beam",
                        List.of("beam", "combat"), BuiltInGeneralEffects.energyBeam(), List.of()),
                entry("arc-lightning", "Arc Lightning", "Branched electric arc",
                        List.of("combat", "lightning"), BuiltInGeneralEffects.arcLightning(),
                        List.of()),
                entry("sparks", "Sparks", "Small additive spark emitter",
                        List.of("particle", "space"), BuiltInGeneralEffects.sparks(), List.of())),
                catalogLimits, effectsLimits);
    }

    private static EffectCatalogEntry entry(String id, String displayName,
            String description, List<String> tags, EffectDefinition definition,
            List<AssetKey> assets) {
        EffectCatalogVariant variant = new EffectCatalogVariant("desktop-gl2", 0,
                definition, List.of(DESKTOP_GL2));
        return new EffectCatalogEntry(id, "1.0.0", displayName, description,
                EffectFamily.from(definition), tags, LICENSE, PROVENANCE, null,
                assets, List.of(variant));
    }

    private BuiltInEffectCatalog() {}
}
