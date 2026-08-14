package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.List;
import java.util.Objects;

/** Immutable engine-neutral state captured after an explicitly advanced visual-effect step. */
public record EffectSnapshot(
        String definitionName,
        long seed,
        long stepIndex,
        float elapsedSeconds,
        List<Anchor> anchors,
        List<Event> events) {

    public EffectSnapshot {
        Objects.requireNonNull(definitionName, "definitionName");
        anchors = List.copyOf(anchors);
        events = List.copyOf(events);
        if (definitionName.isBlank() || stepIndex < 0
                || !Float.isFinite(elapsedSeconds) || elapsedSeconds < 0f) {
            throw new IllegalArgumentException("invalid effect snapshot");
        }
    }

    /** Immutable position of one application-supplied named anchor. */
    public record Anchor(String name, float x, float y, float z) {
        public Anchor {
            requireName(name);
            requireFinite(x, y, z);
        }
    }

    /** Immutable visual event consumed by the latest fixed step. */
    public record Event(String name, long sequence, float value) {
        public Event {
            requireName(name);
            if (sequence < 0 || !Float.isFinite(value)) {
                throw new IllegalArgumentException("invalid effect event snapshot");
            }
        }
    }

    private static void requireName(String name) {
        Objects.requireNonNull(name, "name");
        if (!name.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}")) {
            throw new IllegalArgumentException("invalid runtime name");
        }
    }

    private static void requireFinite(float x, float y, float z) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
            throw new IllegalArgumentException("anchor coordinates must be finite");
        }
    }
}
