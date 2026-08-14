package io.github.teemuki8.libgdx.agent.effects.core;

/** Explicit particle behavior when all preallocated slots are occupied. */
public enum ParticleCapacityPolicy {
    DROP_NEWEST,
    EVICT_OLDEST
}
