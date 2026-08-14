package io.github.teemuki8.libgdx.agent.effects.core;

/** Finite limits and fixed step for mutable visual-effect instances. */
public record RuntimeLimits(
        int maxInstances,
        int maxAnchorsPerInstance,
        int maxQueuedEvents,
        int maxEventsPerSnapshot,
        int maxCatchUpSteps,
        int maxTrailPoints,
        int maxBeamSegments,
        int maxLightningBranches,
        int maxParticles,
        float fixedStepSeconds) {

    private static final int HARD_COUNT_CAP = 1024 * 1024;

    public RuntimeLimits {
        requireCount(maxInstances, "maxInstances");
        requireCount(maxAnchorsPerInstance, "maxAnchorsPerInstance");
        requireCount(maxQueuedEvents, "maxQueuedEvents");
        requireCount(maxEventsPerSnapshot, "maxEventsPerSnapshot");
        requireCount(maxCatchUpSteps, "maxCatchUpSteps");
        requireCount(maxTrailPoints, "maxTrailPoints");
        requireCount(maxBeamSegments, "maxBeamSegments");
        requireCount(maxLightningBranches, "maxLightningBranches");
        requireCount(maxParticles, "maxParticles");
        if (!Float.isFinite(fixedStepSeconds) || fixedStepSeconds <= 0f
                || fixedStepSeconds > 1f) {
            throw new IllegalArgumentException("fixedStepSeconds must be finite and within (0, 1]");
        }
    }

    /** Conservative defaults for application-owned 60 Hz visual simulation. */
    public static RuntimeLimits developmentDefaults() {
        return new RuntimeLimits(1024, 16, 1024, 1024, 8, 8192, 8192, 256, 65536,
                1f / 60f);
    }

    private static void requireCount(int value, String name) {
        if (value <= 0 || value > HARD_COUNT_CAP) {
            throw new IllegalArgumentException(name + " must be within hard bounds");
        }
    }
}
