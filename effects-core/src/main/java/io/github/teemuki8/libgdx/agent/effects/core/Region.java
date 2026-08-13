package io.github.teemuki8.libgdx.agent.effects.core;

/** An axis-aligned pixel region. */
public record Region(int x, int y, int width, int height) {
    public Region {
        if (x < 0 || y < 0) {
            throw new IllegalArgumentException("region origin must be non-negative");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("region size must be positive");
        }
    }
}
