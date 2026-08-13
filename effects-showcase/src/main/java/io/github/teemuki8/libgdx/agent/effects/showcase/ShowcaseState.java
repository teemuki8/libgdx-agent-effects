package io.github.teemuki8.libgdx.agent.effects.showcase;

import java.util.List;
import java.util.Objects;

/** Application-owned bounded selection, animation, and intensity state. */
public final class ShowcaseState {

    public static final float TIME_WRAP_SECONDS = 12f;

    private final List<ShowcasePreset> presets;
    private int selectedIndex;
    private float timeSeconds;
    private float intensity;
    private boolean paused;

    public ShowcaseState(List<ShowcasePreset> presets) {
        this.presets = List.copyOf(Objects.requireNonNull(presets, "presets"));
        if (this.presets.isEmpty()) {
            throw new IllegalArgumentException("presets must not be empty");
        }
        reset();
    }

    public ShowcasePreset selectedPreset() {
        return presets.get(selectedIndex);
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    public float timeSeconds() {
        return timeSeconds;
    }

    public float intensity() {
        return intensity;
    }

    public boolean paused() {
        return paused;
    }

    public void select(int index) {
        if (index < 0 || index >= presets.size()) {
            throw new IllegalArgumentException("preset index out of range");
        }
        selectedIndex = index;
        paused = false;
        reset();
    }

    public void setIntensity(float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("intensity must be finite");
        }
        intensity = Math.max(0f, Math.min(1f, value));
    }

    public void setTimeSeconds(float value) {
        if (!Float.isFinite(value) || value < 0f) {
            throw new IllegalArgumentException("time must be finite and non-negative");
        }
        timeSeconds = value % TIME_WRAP_SECONDS;
    }

    public void advance(float deltaSeconds) {
        if (!Float.isFinite(deltaSeconds) || deltaSeconds < 0f) {
            throw new IllegalArgumentException("deltaSeconds must be finite and non-negative");
        }
        if (!paused) {
            timeSeconds = (timeSeconds + deltaSeconds) % TIME_WRAP_SECONDS;
        }
    }

    public void togglePaused() {
        paused = !paused;
    }

    public void reset() {
        timeSeconds = 0f;
        intensity = selectedPreset().defaultIntensity();
    }
}
