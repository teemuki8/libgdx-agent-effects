package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.List;
import java.util.Objects;

/** Immutable ordered straight-beam segment state. */
public record BeamSnapshot(String name, List<Segment> segments, float ageSeconds) {
    public BeamSnapshot {
        Objects.requireNonNull(name, "name");
        segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
        if (name.isBlank() || !Float.isFinite(ageSeconds) || ageSeconds < 0f) {
            throw new IllegalArgumentException("invalid beam snapshot");
        }
    }

    /** One finite colored beam segment. */
    public record Segment(float startX, float startY, float startZ,
            float endX, float endY, float endZ, float width,
            float r, float g, float b, float a) {
        public Segment {
            if (!finite(startX, startY, startZ, endX, endY, endZ, width, r, g, b, a)
                    || width < 0f) {
                throw new IllegalArgumentException("beam segment must be finite");
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
