package io.github.teemuki8.libgdx.agent.effects.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class GeneralEffectModelTest {
    private static final EffectsLimits LIMITS = new EffectsLimits(
            1024, 8, 4, 1024, 1024, 64, 64, 4,
            1, 1, 8, 4, 2, 2, 2, 1, 2, 16);

    @Test
    void rejectsCurveAndGradientStopsBeyondConfiguredLimits() {
        FloatCurve curve = new FloatCurve(List.of(
                new FloatCurve.Stop(0f, 1f), new FloatCurve.Stop(1f, 0f)));
        ColorGradient gradient = new ColorGradient(List.of(
                new ColorGradient.Stop(0f, 1f, 1f, 1f, 1f),
                new ColorGradient.Stop(1f, 1f, 1f, 1f, 0f)));

        assertLimit(() -> curve.validate(LIMITS));
        assertLimit(() -> gradient.validate(LIMITS));
    }

    @Test
    void rejectsEveryAllocatedFamilyBeyondConfiguredCapacity() {
        assertLimit(() -> particle(3).validate(LIMITS));
        assertLimit(() -> trail(3).validate(LIMITS));
        assertLimit(() -> beam(3).validate(LIMITS));
        assertLimit(() -> lightning(2).validate(LIMITS));
        assertLimit(() -> decal(3).validate(LIMITS));
    }

    private static ParticleDefinition particle(int capacity) {
        return new ParticleDefinition("particles", "anchor", material(), capacity, 1f, 1f, 1f,
                curve(), gradient(), List.of(), ParticleCapacityPolicy.DROP_NEWEST);
    }

    private static TrailDefinition trail(int points) {
        return new TrailDefinition("trail", "anchor", material(), curve(), gradient(),
                0.1f, 0f, points, 1f, TrailJoin.MITER, TrailCap.BUTT,
                TrailUvMode.STRETCH, 2f);
    }

    private static BeamDefinition beam(int segments) {
        return new BeamDefinition("beam", "start", "end", material(), curve(), gradient(),
                segments, 1f);
    }

    private static LightningDefinition lightning(int branches) {
        return new LightningDefinition("lightning", "start", "end", material(), curve(),
                gradient(), 2, branches, 0.1f, 0.1f, 1f);
    }

    private static DecalDefinition decal(int capacity) {
        return new DecalDefinition("decal", material(), capacity, 1f, 0.2f, 1f, 1f);
    }

    private static Material2dDefinition material() {
        return new Material2dDefinition("material",
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

    private static void assertLimit(org.junit.jupiter.api.function.Executable executable) {
        EffectsException failure = assertThrows(EffectsException.class, executable);
        assertEquals(EffectsException.Kind.LIMIT_EXCEEDED, failure.kind());
    }
}
