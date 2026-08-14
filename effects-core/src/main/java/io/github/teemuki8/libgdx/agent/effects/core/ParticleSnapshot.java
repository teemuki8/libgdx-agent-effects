package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.List;
import java.util.Objects;

/** Immutable alive-particle state ordered by stable spawn identifier. */
public record ParticleSnapshot(String name, List<Particle> particles,
        long droppedParticles, long evictedParticles) {
    public ParticleSnapshot {
        Objects.requireNonNull(name, "name");
        particles = List.copyOf(Objects.requireNonNull(particles, "particles"));
        if (name.isBlank() || droppedParticles < 0L || evictedParticles < 0L) {
            throw new IllegalArgumentException("invalid particle snapshot evidence");
        }
        long previous = -1L;
        for (Particle particle : particles) {
            if (particle.spawnId() <= previous) {
                throw new IllegalArgumentException("particle spawn identifiers must increase");
            }
            previous = particle.spawnId();
        }
    }

    /** One finite evaluated alive particle. */
    public record Particle(long spawnId, float x, float y, float z,
            float velocityX, float velocityY, float velocityZ,
            float ageSeconds, float lifetimeSeconds, float size,
            float r, float g, float b, float a) {
        public Particle {
            if (spawnId < 0L || !finite(x, y, z, velocityX, velocityY, velocityZ,
                    ageSeconds, lifetimeSeconds, size, r, g, b, a)
                    || ageSeconds < 0f || lifetimeSeconds <= 0f || size < 0f) {
                throw new IllegalArgumentException("particle values are outside bounds");
            }
        }

        private static boolean finite(float... values) {
            for (float value : values) {
                if (!Float.isFinite(value)) {
                    return false;
                }
            }
            return true;
        }
    }
}
