package io.github.teemuki8.libgdx.agent.effects.library;

import io.github.teemuki8.libgdx.agent.effects.core.BeamDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.ColorGradient;
import io.github.teemuki8.libgdx.agent.effects.core.FloatCurve;
import io.github.teemuki8.libgdx.agent.effects.core.LightningDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleCapacityPolicy;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.TrailCap;
import io.github.teemuki8.libgdx.agent.effects.core.TrailDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.TrailJoin;
import io.github.teemuki8.libgdx.agent.effects.core.TrailUvMode;
import java.util.List;

/** Original reusable trail, beam, lightning, and particle definitions. */
public final class BuiltInGeneralEffects {

    /** Warm ship exhaust ribbon used by the showcase and catalog fixtures. */
    public static TrailDefinition shipTrail() {
        return new TrailDefinition("ship-trail", "ship",
                BuiltInMaterials.coloredGeometry("ship-trail-material"),
                curve(0.3f), gradient(), 0.1f, 0f, 4, 1f,
                TrailJoin.MITER, TrailCap.BUTT, TrailUvMode.STRETCH, 2f);
    }

    /** Stable two-segment combat beam style. */
    public static BeamDefinition energyBeam() {
        return new BeamDefinition("energy-beam", "start", "end",
                BuiltInMaterials.coloredGeometry("energy-beam-material"),
                curve(0.2f), gradient(), 2, 1f);
    }

    /** Seed-ready branched lightning style. */
    public static LightningDefinition arcLightning() {
        return new LightningDefinition("arc-lightning", "start", "end",
                BuiltInMaterials.coloredGeometry("arc-lightning-material"),
                curve(0.12f), gradient(), 2, 1, 0.2f, 0.5f, 1f);
    }

    /** Small additive spark emitter suitable for deterministic CPU rendering. */
    public static ParticleDefinition sparks() {
        return new ParticleDefinition("sparks", "emitter",
                BuiltInMaterials.coloredGeometry("sparks-material"),
                8, 0f, 1f, 0f, curve(0.25f), gradient(), List.of(),
                ParticleCapacityPolicy.DROP_NEWEST);
    }

    private static FloatCurve curve(float value) {
        return new FloatCurve(List.of(new FloatCurve.Stop(0f, value)));
    }

    private static ColorGradient gradient() {
        return new ColorGradient(List.of(
                new ColorGradient.Stop(0f, 1f, 0.6f, 0.1f, 1f)));
    }

    private BuiltInGeneralEffects() {}
}
