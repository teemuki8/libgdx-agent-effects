package io.github.teemuki8.libgdx.agent.effects.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.teemuki8.libgdx.agent.effects.core.BlendMode;
import io.github.teemuki8.libgdx.agent.effects.core.ColorGradient;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.FloatCurve;
import io.github.teemuki8.libgdx.agent.effects.core.Material2dDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleCapacityPolicy;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleModifier;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleSnapshot;
import io.github.teemuki8.libgdx.agent.effects.core.RuntimeLimits;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderSource;
import java.util.List;
import org.junit.jupiter.api.Test;

class CpuParticleInstanceTest {

    @Test
    void identicalSeedsBurstsAndFixedStepsProduceIdenticalSpawnOrder() {
        CpuParticleInstance first = instance(7L, ParticleCapacityPolicy.DROP_NEWEST);
        CpuParticleInstance second = instance(7L, ParticleCapacityPolicy.DROP_NEWEST);
        first.setAnchor(new EffectAnchor("emitter", 2f, 3f, 0f));
        second.setAnchor(new EffectAnchor("emitter", 2f, 3f, 0f));
        first.burst(2);
        second.burst(2);
        first.advance(0.25f);
        second.advance(0.25f);

        ParticleSnapshot snapshot = first.snapshot();
        assertEquals(snapshot, second.snapshot());
        assertEquals(List.of(0L, 1L, 2L), snapshot.particles().stream()
                .map(ParticleSnapshot.Particle::spawnId).toList());
        assertTrue(snapshot.particles().stream().allMatch(particle -> particle.y() < 3.5f));
    }

    @Test
    void differentSeedsProduceDifferentVelocities() {
        CpuParticleInstance first = instance(7L, ParticleCapacityPolicy.DROP_NEWEST);
        CpuParticleInstance second = instance(8L, ParticleCapacityPolicy.DROP_NEWEST);
        EffectAnchor anchor = new EffectAnchor("emitter", 0f, 0f, 0f);
        first.setAnchor(anchor);
        second.setAnchor(anchor);
        first.burst(1);
        second.burst(1);
        assertNotEquals(first.snapshot(), second.snapshot());
    }

    @Test
    void reportsDropNewestAndEvictOldestCapacityPressure() {
        CpuParticleInstance dropping = instance(1L, ParticleCapacityPolicy.DROP_NEWEST);
        dropping.setAnchor(new EffectAnchor("emitter", 0f, 0f, 0f));
        dropping.burst(5);
        assertEquals(3, dropping.snapshot().particles().size());
        assertEquals(2L, dropping.snapshot().droppedParticles());

        CpuParticleInstance evicting = instance(1L, ParticleCapacityPolicy.EVICT_OLDEST);
        evicting.setAnchor(new EffectAnchor("emitter", 0f, 0f, 0f));
        evicting.burst(5);
        assertEquals(List.of(2L, 3L, 4L), evicting.snapshot().particles().stream()
                .map(ParticleSnapshot.Particle::spawnId).toList());
        assertEquals(2L, evicting.snapshot().evictedParticles());
    }

    @Test
    void expiresParticlesAndRejectsInvalidBoundsAndLifecycle() {
        CpuParticleInstance particles = new CpuParticleInstance(
                definition(3, ParticleCapacityPolicy.DROP_NEWEST, 0f), limits(3), 1L);
        particles.setAnchor(new EffectAnchor("emitter", 0f, 0f, 0f));
        particles.burst(1);
        particles.advance(1f);
        assertTrue(particles.snapshot().particles().isEmpty());
        particles.close();
        assertKind(EffectsException.Kind.INVALID_LIFECYCLE, particles::snapshot);
        assertKind(EffectsException.Kind.LIMIT_EXCEEDED,
                () -> new CpuParticleInstance(definition(4, ParticleCapacityPolicy.DROP_NEWEST),
                        limits(3), 1L));
    }

    private static CpuParticleInstance instance(long seed, ParticleCapacityPolicy policy) {
        return new CpuParticleInstance(definition(3, policy), limits(3), seed);
    }

    private static ParticleDefinition definition(int capacity, ParticleCapacityPolicy policy) {
        return definition(capacity, policy, 4f);
    }

    private static ParticleDefinition definition(int capacity, ParticleCapacityPolicy policy,
            float emissionRate) {
        return new ParticleDefinition("sparks", "emitter", material(), capacity, emissionRate,
                0.75f, 1f, new FloatCurve(List.of(new FloatCurve.Stop(0f, 0.1f))),
                new ColorGradient(List.of(new ColorGradient.Stop(0f, 1f, 0.5f, 0f, 1f))),
                List.of(new ParticleModifier.Gravity(0f, -1f, 0f),
                        new ParticleModifier.Drag(0.1f)), policy);
    }

    private static Material2dDefinition material() {
        return new Material2dDefinition("particle-material",
                new ShaderSource("void main(){}", "void main(){}"),
                BlendMode.ADDITIVE, List.of(), List.of());
    }

    private static RuntimeLimits limits(int particles) {
        return new RuntimeLimits(4, 4, 4, 4, 64, 16, 8, 4, particles, 0.25f);
    }

    private static void assertKind(EffectsException.Kind kind,
            org.junit.jupiter.api.function.Executable executable) {
        EffectsException failure = assertThrows(EffectsException.class, executable);
        assertEquals(kind, failure.kind());
    }
}
