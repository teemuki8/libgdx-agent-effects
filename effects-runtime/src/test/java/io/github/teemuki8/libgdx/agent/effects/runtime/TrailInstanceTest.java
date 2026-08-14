package io.github.teemuki8.libgdx.agent.effects.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.teemuki8.libgdx.agent.effects.core.BlendMode;
import io.github.teemuki8.libgdx.agent.effects.core.ColorGradient;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.FloatCurve;
import io.github.teemuki8.libgdx.agent.effects.core.Material2dDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.RuntimeLimits;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderSource;
import io.github.teemuki8.libgdx.agent.effects.core.TrailCap;
import io.github.teemuki8.libgdx.agent.effects.core.TrailDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.TrailJoin;
import io.github.teemuki8.libgdx.agent.effects.core.TrailSnapshot;
import io.github.teemuki8.libgdx.agent.effects.core.TrailUvMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrailInstanceTest {

    @Test
    void samplesRightAngleInStableOldestFirstOrder() {
        TrailInstance trail = new TrailInstance(definition(4, 10f), limits(4));
        trail.setAnchor(new EffectAnchor("ship", 0f, 0f, 0f));
        trail.advance(0.25f);
        trail.setAnchor(new EffectAnchor("ship", 1f, 0f, 0f));
        trail.advance(0.25f);
        trail.setAnchor(new EffectAnchor("ship", 1f, 1f, 0f));
        trail.advance(0.25f);

        TrailSnapshot snapshot = trail.snapshot();
        assertEquals(3, snapshot.points().size());
        assertEquals(0f, snapshot.points().get(0).x());
        assertEquals(1f, snapshot.points().get(1).x());
        assertEquals(0f, snapshot.points().get(1).y());
        assertEquals(1f, snapshot.points().get(2).y());
        assertEquals(0f, snapshot.points().get(0).u());
        assertEquals(1f, snapshot.points().get(2).u());
    }

    @Test
    void evictsOldestAndReportsCapacityPressure() {
        TrailInstance trail = new TrailInstance(definition(2, 10f), limits(2));
        for (int x = 0; x < 3; x++) {
            trail.setAnchor(new EffectAnchor("ship", x, 0f, 0f));
            trail.advance(0.25f);
        }

        TrailSnapshot snapshot = trail.snapshot();
        assertEquals(List.of(1f, 2f), snapshot.points().stream()
                .map(TrailSnapshot.Point::x).toList());
        assertEquals(1L, snapshot.evictedPoints());
    }

    @Test
    void ignoresCoincidentSamplesAndExpiresAllPoints() {
        TrailInstance trail = new TrailInstance(definition(4, 0.5f), limits(4));
        trail.setAnchor(new EffectAnchor("ship", 2f, 3f, 0f));
        trail.advance(0.25f);
        trail.advance(0.25f);
        assertEquals(1, trail.snapshot().points().size());

        trail.advance(0.75f);
        assertTrue(trail.snapshot().points().isEmpty());
    }

    @Test
    void snapshotsContainOnlyFiniteBoundedGeometryInputs() {
        TrailInstance trail = new TrailInstance(definition(4, 10f), limits(4));
        trail.setAnchor(new EffectAnchor("ship", 0f, 0f, 0f));
        trail.advance(0.25f);
        trail.setAnchor(new EffectAnchor("ship", 1f, 0f, 0f));
        trail.advance(0.25f);
        trail.setAnchor(new EffectAnchor("ship", 1f, 0.01f, 0f));
        trail.advance(0.25f);

        for (TrailSnapshot.Point point : trail.snapshot().points()) {
            assertTrue(Float.isFinite(point.width()));
            assertTrue(point.width() >= 0f && point.width() <= 2f);
            assertTrue(Float.isFinite(point.u()));
            assertFalse(Float.isNaN(point.r()));
        }
    }

    @Test
    void rejectsPointLimitsAboveRuntimeCapacityAndInvalidLifecycle() {
        assertKind(EffectsException.Kind.LIMIT_EXCEEDED,
                () -> new TrailInstance(definition(4, 10f), limits(2)));
        TrailInstance trail = new TrailInstance(definition(2, 10f), limits(2));
        trail.close();
        assertKind(EffectsException.Kind.INVALID_LIFECYCLE, trail::snapshot);
    }

    @Test
    void rejectsSamplingBeyondConfiguredCatchUpCapacity() {
        TrailInstance trail = new TrailInstance(definition(4, 10f), limits(4));
        trail.setAnchor(new EffectAnchor("ship", 0f, 0f, 0f));

        assertKind(EffectsException.Kind.LIMIT_EXCEEDED, () -> trail.advance(2.25f));
        assertTrue(trail.snapshot().points().isEmpty());
    }

    private static TrailDefinition definition(int points, float lifetime) {
        return new TrailDefinition("ship-trail", "ship", material(),
                new FloatCurve(List.of(new FloatCurve.Stop(0f, 2f),
                        new FloatCurve.Stop(1f, 0f))),
                new ColorGradient(List.of(
                        new ColorGradient.Stop(0f, 1f, 1f, 1f, 1f),
                        new ColorGradient.Stop(1f, 0f, 0.2f, 1f, 0f))),
                0.25f, 0.01f, points, lifetime, TrailJoin.MITER,
                TrailCap.BUTT, TrailUvMode.STRETCH, 2f);
    }

    private static Material2dDefinition material() {
        return new Material2dDefinition("trail-material",
                new ShaderSource("void main(){}", "void main(){}"),
                BlendMode.ADDITIVE, List.of(), List.of());
    }

    private static RuntimeLimits limits(int maxTrailPoints) {
        return new RuntimeLimits(4, 2, 4, 4, 8, maxTrailPoints, 8, 4, 16, 8,
                1f / 60f);
    }

    private static void assertKind(EffectsException.Kind kind,
            org.junit.jupiter.api.function.Executable executable) {
        EffectsException failure = assertThrows(EffectsException.class, executable);
        assertEquals(kind, failure.kind());
    }
}
