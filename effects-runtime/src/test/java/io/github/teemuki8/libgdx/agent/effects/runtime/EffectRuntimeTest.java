package io.github.teemuki8.libgdx.agent.effects.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.teemuki8.libgdx.agent.effects.core.BlendMode;
import io.github.teemuki8.libgdx.agent.effects.core.EffectSnapshot;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.Material2dDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.RuntimeLimits;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderSource;
import java.util.List;
import org.junit.jupiter.api.Test;

class EffectRuntimeTest {

    @Test
    void identicalSeedsAndInputsProduceIdenticalFixedStepSnapshots() {
        RuntimeLimits limits = limits();
        try (EffectRuntime first = new EffectRuntime(limits);
                EffectRuntime second = new EffectRuntime(limits)) {
            EffectInstance a = first.create(material(), 42L, List.of("source"));
            EffectInstance b = second.create(material(), 42L, List.of("source"));
            EffectAnchor anchor = new EffectAnchor("source", 2f, 3f, 4f);
            EffectEvent event = new EffectEvent("burst", 7L, 3f);

            a.setAnchor(anchor);
            b.setAnchor(anchor);
            a.submit(event);
            b.submit(event);
            a.advance(1f / 120f);
            b.advance(1f / 120f);
            assertEquals(0, a.snapshot().stepIndex());
            a.advance(1f / 120f);
            b.advance(1f / 120f);

            EffectSnapshot expected = a.snapshot();
            assertEquals(expected, b.snapshot());
            assertEquals(1, expected.stepIndex());
            assertEquals(List.of(new EffectSnapshot.Anchor("source", 2f, 3f, 4f)),
                    expected.anchors());
            assertEquals(List.of(new EffectSnapshot.Event("burst", 7L, 3f)),
                    expected.events());
        }
    }

    @Test
    void rejectsInvalidDeltaUnknownAnchorAndExcessEvents() {
        try (EffectRuntime runtime = new EffectRuntime(limits())) {
            EffectInstance instance = runtime.create(material(), 1L, List.of("source"));
            assertKind(EffectsException.Kind.INVALID_EFFECT,
                    () -> instance.advance(-0.1f));
            assertKind(EffectsException.Kind.INVALID_EFFECT,
                    () -> instance.advance(Float.NaN));
            assertKind(EffectsException.Kind.INVALID_EFFECT,
                    () -> instance.setAnchor(new EffectAnchor("target", 0, 0, 0)));
            instance.submit(new EffectEvent("one", 1, 0));
            instance.submit(new EffectEvent("two", 2, 0));
            assertKind(EffectsException.Kind.LIMIT_EXCEEDED,
                    () -> instance.submit(new EffectEvent("three", 3, 0)));
        }
    }

    @Test
    void enforcesInstanceAndAnchorCapacityAndReleasesOnClose() {
        try (EffectRuntime runtime = new EffectRuntime(limits())) {
            assertKind(EffectsException.Kind.LIMIT_EXCEEDED,
                    () -> runtime.create(material(), 1L, List.of("a", "b", "c")));
            EffectInstance first = runtime.create(material(), 1L, List.of("source"));
            runtime.create(material(), 2L, List.of("source"));
            assertKind(EffectsException.Kind.LIMIT_EXCEEDED,
                    () -> runtime.create(material(), 3L, List.of("source")));
            first.close();
            runtime.create(material(), 3L, List.of("source"));
        }
    }

    @Test
    void rejectsLifecycleCallsAfterCloseAndExcessCatchUp() {
        EffectRuntime runtime = new EffectRuntime(limits());
        EffectInstance instance = runtime.create(material(), 1L, List.of("source"));
        instance.close();
        assertKind(EffectsException.Kind.INVALID_LIFECYCLE, instance::snapshot);
        assertKind(EffectsException.Kind.INVALID_LIFECYCLE,
                () -> instance.submit(new EffectEvent("burst", 1, 1)));

        EffectInstance active = runtime.create(material(), 2L, List.of("source"));
        assertKind(EffectsException.Kind.LIMIT_EXCEEDED, () -> active.advance(1f));
        runtime.close();
        assertKind(EffectsException.Kind.INVALID_LIFECYCLE,
                () -> runtime.create(material(), 3L, List.of()));
        assertKind(EffectsException.Kind.INVALID_LIFECYCLE, active::snapshot);
    }

    private static RuntimeLimits limits() {
        return new RuntimeLimits(2, 2, 2, 2, 4, 1f / 60f);
    }

    private static Material2dDefinition material() {
        return new Material2dDefinition("runtime-material",
                new ShaderSource("void main(){}", "void main(){}"),
                BlendMode.NORMAL, List.of(), List.of());
    }

    private static void assertKind(EffectsException.Kind kind,
            org.junit.jupiter.api.function.Executable executable) {
        EffectsException failure = assertThrows(EffectsException.class, executable);
        assertEquals(kind, failure.kind());
    }
}
