package io.github.teemuki8.libgdx.agent.effects.core;

/** Typed failure with a stable kind; no serialized stack traces across boundaries. */
public final class EffectsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public enum Kind {
        INVALID_EFFECT,
        INVALID_LIFECYCLE,
        INVALID_IMPORT,
        UNSUPPORTED_FEATURE,
        COMPILE_FAILED,
        DIMENSION_MISMATCH,
        LIMIT_EXCEEDED,
        WRONG_THREAD
    }

    private final Kind kind;

    public EffectsException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
