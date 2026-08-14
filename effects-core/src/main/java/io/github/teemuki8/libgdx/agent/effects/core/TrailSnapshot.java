package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.List;
import java.util.Objects;

/** Immutable oldest-to-newest sampled trail state. */
public record TrailSnapshot(String name, List<Point> points, long evictedPoints) {
    public TrailSnapshot {
        Objects.requireNonNull(name, "name");
        points = List.copyOf(Objects.requireNonNull(points, "points"));
        if (name.isBlank() || evictedPoints < 0L) {
            throw new IllegalArgumentException("invalid trail snapshot evidence");
        }
    }

    /** One finite sampled trail point with evaluated material inputs. */
    public record Point(float x, float y, float z, float ageSeconds, float width,
            float r, float g, float b, float a, float u) {
        public Point {
            if (!allFinite(x, y, z, ageSeconds, width, r, g, b, a, u)
                    || ageSeconds < 0f || width < 0f) {
                throw new IllegalArgumentException("trail point values must be finite and nonnegative");
            }
        }

        private static boolean allFinite(float... values) {
            for (float value : values) {
                if (!Float.isFinite(value)) {
                    return false;
                }
            }
            return true;
        }
    }
}
