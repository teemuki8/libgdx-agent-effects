package io.github.teemuki8.libgdx.agent.effects.core;

/** Closed allocation-free CPU particle modifier contract. */
public sealed interface ParticleModifier permits ParticleModifier.Gravity, ParticleModifier.Drag {

    /** Constant acceleration applied per fixed step. */
    record Gravity(float x, float y, float z) implements ParticleModifier {
        public Gravity {
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
                throw new IllegalArgumentException("gravity must be finite");
            }
        }
    }

    /** Nonnegative linear velocity damping coefficient. */
    record Drag(float coefficient) implements ParticleModifier {
        public Drag {
            if (!Float.isFinite(coefficient) || coefficient < 0f || coefficient > 1000f) {
                throw new IllegalArgumentException("drag coefficient is outside hard bounds");
            }
        }
    }
}
