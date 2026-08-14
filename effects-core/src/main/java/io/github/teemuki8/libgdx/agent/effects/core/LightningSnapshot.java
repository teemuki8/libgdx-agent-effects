package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.List;
import java.util.Objects;

/** Immutable ordered seeded-lightning segment state. */
public record LightningSnapshot(String name, List<Segment> segments,
        long generation, float ageSeconds) {
    public LightningSnapshot {
        Objects.requireNonNull(name, "name");
        segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
        if (name.isBlank() || generation < 0L
                || !Float.isFinite(ageSeconds) || ageSeconds < 0f) {
            throw new IllegalArgumentException("invalid lightning snapshot");
        }
    }

    /** One finite colored primary or branch segment. */
    public record Segment(float startX, float startY, float startZ,
            float endX, float endY, float endZ, float width,
            float r, float g, float b, float a, boolean branch) {
        public Segment {
            if (!finite(startX, startY, startZ, endX, endY, endZ, width, r, g, b, a)
                    || width < 0f) {
                throw new IllegalArgumentException("lightning segment must be finite");
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
