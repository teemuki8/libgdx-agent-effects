package io.github.teemuki8.libgdx.agent.effects.importer.godot;

import io.github.teemuki8.libgdx.agent.effects.core.SourceSpan;
import java.io.Serial;
import java.util.Objects;

/** Internal bounded parse failure converted to public import diagnostics at the importer boundary. */
final class GodotImportException extends RuntimeException {
    @Serial private static final long serialVersionUID = 1L;

    private final String code;
    private final transient SourceSpan span;

    GodotImportException(String code, String message, SourceSpan span) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
        this.span = Objects.requireNonNull(span, "span");
    }

    String code() {
        return code;
    }

    SourceSpan span() {
        return span;
    }
}
