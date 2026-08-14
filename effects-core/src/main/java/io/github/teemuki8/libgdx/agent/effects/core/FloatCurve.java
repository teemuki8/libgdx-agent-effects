package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.List;
import java.util.Objects;

/** Immutable bounded piecewise-linear scalar curve. */
public record FloatCurve(List<Stop> stops) {
    private static final int HARD_STOP_CAP = 4096;

    public FloatCurve {
        stops = List.copyOf(Objects.requireNonNull(stops, "stops"));
        if (stops.isEmpty() || stops.size() > HARD_STOP_CAP) {
            throw new IllegalArgumentException("curve stop count is outside hard bounds");
        }
        float previous = -Float.MAX_VALUE;
        for (Stop stop : stops) {
            Objects.requireNonNull(stop, "stop");
            if (stop.position() <= previous) {
                throw new IllegalArgumentException("curve stop positions must increase");
            }
            previous = stop.position();
        }
    }

    /** Samples the curve, clamping outside its declared stop range. */
    public float sample(float position) {
        if (!Float.isFinite(position)) {
            throw new IllegalArgumentException("sample position must be finite");
        }
        Stop first = stops.getFirst();
        if (position <= first.position()) {
            return first.value();
        }
        for (int index = 1; index < stops.size(); index++) {
            Stop next = stops.get(index);
            if (position <= next.position()) {
                Stop previous = stops.get(index - 1);
                float alpha = (position - previous.position())
                        / (next.position() - previous.position());
                return previous.value() + (next.value() - previous.value()) * alpha;
            }
        }
        return stops.getLast().value();
    }

    /** Validates this curve against caller-configured stop limits. */
    public FloatCurve validate(EffectsLimits limits) {
        Objects.requireNonNull(limits, "limits");
        if (stops.size() > limits.maxCurveStops()) {
            throw new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED,
                    "curve stop count exceeds configured limit");
        }
        return this;
    }

    /** One finite curve control point. */
    public record Stop(float position, float value) {
        public Stop {
            if (!Float.isFinite(position) || !Float.isFinite(value)) {
                throw new IllegalArgumentException("curve stop values must be finite");
            }
        }
    }
}
