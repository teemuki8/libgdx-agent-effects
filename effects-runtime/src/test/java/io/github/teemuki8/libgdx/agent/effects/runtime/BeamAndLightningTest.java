package io.github.teemuki8.libgdx.agent.effects.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.teemuki8.libgdx.agent.effects.core.BeamDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.BeamSnapshot;
import io.github.teemuki8.libgdx.agent.effects.core.BlendMode;
import io.github.teemuki8.libgdx.agent.effects.core.ColorGradient;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.FloatCurve;
import io.github.teemuki8.libgdx.agent.effects.core.LightningDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.LightningSnapshot;
import io.github.teemuki8.libgdx.agent.effects.core.Material2dDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.RuntimeLimits;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderSource;
import java.util.List;
import org.junit.jupiter.api.Test;

class BeamAndLightningTest {

    @Test
    void beamTracksEndpointsWithStableBoundedSegments() {
        BeamInstance beam = new BeamInstance(beamDefinition(), limits());
        beam.setAnchor(new EffectAnchor("muzzle", 0f, 0f, 0f));
        beam.setAnchor(new EffectAnchor("target", 3f, 0f, 0f));
        beam.advance(0.25f);

        BeamSnapshot snapshot = beam.snapshot();
        assertEquals(3, snapshot.segments().size());
        assertEquals(0f, snapshot.segments().getFirst().startX());
        assertEquals(3f, snapshot.segments().getLast().endX());
        assertTrue(snapshot.segments().stream().allMatch(segment -> segment.width() > 0f));
    }

    @Test
    void zeroLengthBeamIsFiniteAndEmpty() {
        BeamInstance beam = new BeamInstance(beamDefinition(), limits());
        beam.setAnchor(new EffectAnchor("muzzle", 1f, 1f, 0f));
        beam.setAnchor(new EffectAnchor("target", 1f, 1f, 0f));
        assertTrue(beam.snapshot().segments().isEmpty());
    }

    @Test
    void lightningIsSeededAndRegeneratesOnlyAtDeclaredInterval() {
        LightningInstance first = lightning(91L);
        LightningInstance second = lightning(91L);
        LightningInstance different = lightning(92L);
        LightningSnapshot initial = first.snapshot();
        assertEquals(initial, second.snapshot());
        assertNotEquals(initial, different.snapshot());
        first.advance(0.49f);
        assertEquals(initial.segments(), first.snapshot().segments());
        assertEquals(initial.generation(), first.snapshot().generation());
        first.advance(0.01f);
        assertNotEquals(initial, first.snapshot());
        assertEquals(6, first.snapshot().segments().size());
        assertEquals(1L, first.snapshot().generation());
    }

    @Test
    void stableGeneratorSequenceIsRepositoryOwned() {
        StableRandom random = new StableRandom(1L);
        assertEquals(0.42320913f, random.nextFloat());
        assertEquals(0.5094074f, random.nextFloat());
        assertEquals(0.64835936f, random.nextFloat());
    }

    @Test
    void rejectsConfiguredSegmentAndBranchCapacity() {
        assertKind(EffectsException.Kind.LIMIT_EXCEEDED,
                () -> new BeamInstance(beamDefinition(), limits(2, 2)));
        assertKind(EffectsException.Kind.LIMIT_EXCEEDED,
                () -> new LightningInstance(lightningDefinition(), limits(8, 1), 1L));
    }

    private static LightningInstance lightning(long seed) {
        LightningInstance result = new LightningInstance(lightningDefinition(), limits(), seed);
        result.setAnchor(new EffectAnchor("muzzle", -1f, 0f, 0f));
        result.setAnchor(new EffectAnchor("target", 1f, 0f, 0f));
        return result;
    }

    private static BeamDefinition beamDefinition() {
        return new BeamDefinition("laser", "muzzle", "target", material(),
                curve(0.1f), gradient(), 3, 2f);
    }

    private static LightningDefinition lightningDefinition() {
        return new LightningDefinition("arc", "muzzle", "target", material(),
                curve(0.05f), gradient(), 4, 2, 0.25f, 0.5f, 2f);
    }

    private static FloatCurve curve(float value) {
        return new FloatCurve(List.of(new FloatCurve.Stop(0f, value)));
    }

    private static ColorGradient gradient() {
        return new ColorGradient(List.of(new ColorGradient.Stop(0f, 0.4f, 0.8f, 1f, 1f)));
    }

    private static Material2dDefinition material() {
        return new Material2dDefinition("segment-material",
                new ShaderSource("void main(){}", "void main(){}"),
                BlendMode.ADDITIVE, List.of(), List.of());
    }

    private static RuntimeLimits limits() {
        return limits(8, 4);
    }

    private static RuntimeLimits limits(int segments, int branches) {
        return new RuntimeLimits(4, 4, 4, 4, 8, 16, segments, branches, 16,
                1f / 60f);
    }

    private static void assertKind(EffectsException.Kind kind,
            org.junit.jupiter.api.function.Executable executable) {
        EffectsException failure = assertThrows(EffectsException.class, executable);
        assertEquals(kind, failure.kind());
    }
}
