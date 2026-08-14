package io.github.teemuki8.libgdx.agent.effects.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.teemuki8.libgdx.agent.effects.core.BlendMode;
import io.github.teemuki8.libgdx.agent.effects.core.DecalDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.DecalPlacement;
import io.github.teemuki8.libgdx.agent.effects.core.DecalSnapshot;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.Material2dDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.RuntimeLimits;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderSource;
import java.util.List;
import org.junit.jupiter.api.Test;

class DecalInstanceTest {

    @Test
    void snapshotsAreOrderedByDeclaredOrderThenStableSpawnId() {
        DecalInstance decals = new DecalInstance(definition(), limits(3));
        decals.spawn(placement(5, 1f));
        decals.spawn(placement(1, 2f));
        decals.spawn(placement(5, 3f));
        assertEquals(List.of(1L, 0L, 2L), decals.snapshot().decals().stream()
                .map(DecalSnapshot.Decal::spawnId).toList());
        assertEquals(List.of(2f, 1f, 3f), decals.snapshot().decals().stream()
                .map(DecalSnapshot.Decal::x).toList());
    }

    @Test
    void appliesFadeAndExpiresAtDeclaredLifetime() {
        DecalInstance decals = new DecalInstance(definition(), limits(3));
        decals.spawn(placement(0, 0f));
        decals.advance(0.75f);
        assertEquals(0.5f, decals.snapshot().decals().getFirst().a(), 0.0001f);
        decals.advance(0.25f);
        assertTrue(decals.snapshot().decals().isEmpty());
    }

    @Test
    void reportsCapacityDropsAndRejectsNonfiniteTransforms() {
        DecalInstance decals = new DecalInstance(definition(), limits(3));
        for (int index = 0; index < 5; index++) {
            decals.spawn(placement(index, index));
        }
        assertEquals(3, decals.snapshot().decals().size());
        assertEquals(2L, decals.snapshot().droppedDecals());
        assertThrows(IllegalArgumentException.class,
                () -> new DecalPlacement(0, Float.NaN, 0f, 0f,
                        0f, 0f, 1f, 0f, 1f, 1f, 1f, 1f));
    }

    @Test
    void enforcesConfiguredCapacityAndClosedLifecycle() {
        assertKind(EffectsException.Kind.LIMIT_EXCEEDED,
                () -> new DecalInstance(definition(), limits(2)));
        DecalInstance decals = new DecalInstance(definition(), limits(3));
        decals.close();
        assertKind(EffectsException.Kind.INVALID_LIFECYCLE, decals::snapshot);
    }

    private static DecalDefinition definition() {
        Material2dDefinition material = new Material2dDefinition("decal-material",
                new ShaderSource("void main(){}", "void main(){}"),
                BlendMode.NORMAL, List.of(), List.of());
        return new DecalDefinition("scorch", material, 3, 1f, 0.5f, 0.5f, 0.25f);
    }

    private static DecalPlacement placement(long order, float x) {
        return new DecalPlacement(order, x, 0f, 0f, 0f, 0f, 1f,
                0f, 1f, 1f, 1f, 1f);
    }

    private static RuntimeLimits limits(int decals) {
        return new RuntimeLimits(4, 4, 4, 4, 64, 16, 8, 4, 16, decals, 0.25f);
    }

    private static void assertKind(EffectsException.Kind kind,
            org.junit.jupiter.api.function.Executable executable) {
        EffectsException failure = assertThrows(EffectsException.class, executable);
        assertEquals(kind, failure.kind());
    }
}
