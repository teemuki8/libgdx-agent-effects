package io.github.teemuki8.libgdx.agent.effects.runtime;

import io.github.teemuki8.libgdx.agent.effects.core.EffectSnapshot;

/** Explicitly stepped bounded visual state owned and disposed by the application. */
public interface EffectInstance extends AutoCloseable {

    /** Updates one declared named anchor. */
    void setAnchor(EffectAnchor anchor);

    /** Queues one bounded visual input for the next fixed step. */
    void submit(EffectEvent event);

    /** Accumulates application-supplied time and executes zero or more fixed steps. */
    void advance(float deltaSeconds);

    /** Returns a deeply immutable snapshot of the latest completed state. */
    EffectSnapshot snapshot();

    /** Releases this instance's runtime capacity; repeated close calls are harmless. */
    @Override void close();
}
