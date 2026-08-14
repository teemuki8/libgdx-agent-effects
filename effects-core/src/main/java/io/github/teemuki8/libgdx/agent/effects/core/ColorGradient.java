package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.List;
import java.util.Objects;

/** Immutable bounded piecewise-linear RGBA gradient. */
public record ColorGradient(List<Stop> stops) {
    private static final int HARD_STOP_CAP = 4096;

    public ColorGradient {
        stops = List.copyOf(Objects.requireNonNull(stops, "stops"));
        if (stops.isEmpty() || stops.size() > HARD_STOP_CAP) {
            throw new IllegalArgumentException("gradient stop count is outside hard bounds");
        }
        float previous = -Float.MAX_VALUE;
        for (Stop stop : stops) {
            Objects.requireNonNull(stop, "stop");
            if (stop.position() <= previous) {
                throw new IllegalArgumentException("gradient stop positions must increase");
            }
            previous = stop.position();
        }
    }

    /** Samples the gradient, clamping outside its declared stop range. */
    public Color sample(float position) {
        if (!Float.isFinite(position)) {
            throw new IllegalArgumentException("sample position must be finite");
        }
        Stop first = stops.getFirst();
        if (position <= first.position()) {
            return first.color();
        }
        for (int index = 1; index < stops.size(); index++) {
            Stop next = stops.get(index);
            if (position <= next.position()) {
                Stop previous = stops.get(index - 1);
                float alpha = (position - previous.position())
                        / (next.position() - previous.position());
                return new Color(
                        lerp(previous.r(), next.r(), alpha),
                        lerp(previous.g(), next.g(), alpha),
                        lerp(previous.b(), next.b(), alpha),
                        lerp(previous.a(), next.a(), alpha));
            }
        }
        return stops.getLast().color();
    }

    /** Validates this gradient against caller-configured stop limits. */
    public ColorGradient validate(EffectsLimits limits) {
        Objects.requireNonNull(limits, "limits");
        if (stops.size() > limits.maxGradientStops()) {
            throw new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED,
                    "gradient stop count exceeds configured limit");
        }
        return this;
    }

    private static float lerp(float first, float second, float alpha) {
        return first + (second - first) * alpha;
    }

    /** One finite gradient control point. */
    public record Stop(float position, float r, float g, float b, float a) {
        public Stop {
            if (!Float.isFinite(position) || !finiteColor(r, g, b, a)) {
                throw new IllegalArgumentException("gradient stop values must be finite");
            }
        }

        private Color color() {
            return new Color(r, g, b, a);
        }
    }

    /** One immutable finite RGBA sample. */
    public record Color(float r, float g, float b, float a) {
        public Color {
            if (!finiteColor(r, g, b, a)) {
                throw new IllegalArgumentException("color values must be finite");
            }
        }
    }

    private static boolean finiteColor(float r, float g, float b, float a) {
        return Float.isFinite(r) && Float.isFinite(g)
                && Float.isFinite(b) && Float.isFinite(a);
    }
}
