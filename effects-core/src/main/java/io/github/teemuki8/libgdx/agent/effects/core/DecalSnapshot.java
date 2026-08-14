package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.List;
import java.util.Objects;

/** Immutable decals ordered by declared order and then spawn identifier. */
public record DecalSnapshot(String name, List<Decal> decals, long droppedDecals) {
    public DecalSnapshot {
        Objects.requireNonNull(name, "name");
        decals = List.copyOf(Objects.requireNonNull(decals, "decals"));
        if (name.isBlank() || droppedDecals < 0L) {
            throw new IllegalArgumentException("invalid decal snapshot evidence");
        }
    }

    /** One finite evaluated decal. */
    public record Decal(long spawnId, long order, float x, float y, float z,
            float normalX, float normalY, float normalZ, float rotationDegrees,
            float width, float height, float ageSeconds,
            float r, float g, float b, float a) {
        public Decal {
            if (spawnId < 0L || !finite(x, y, z, normalX, normalY, normalZ,
                    rotationDegrees, width, height, ageSeconds, r, g, b, a)
                    || width <= 0f || height <= 0f || ageSeconds < 0f) {
                throw new IllegalArgumentException("decal values are outside bounds");
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
