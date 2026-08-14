package io.github.teemuki8.libgdx.agent.effects.runtime;

import java.util.Objects;

/** Sequenced scalar visual input submitted explicitly by application code. */
public record EffectEvent(String name, long sequence, float value) {
    public EffectEvent {
        Objects.requireNonNull(name, "name");
        if (!name.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}")) {
            throw new IllegalArgumentException("invalid event name");
        }
        if (sequence < 0 || !Float.isFinite(value)) {
            throw new IllegalArgumentException("invalid effect event");
        }
    }
}
