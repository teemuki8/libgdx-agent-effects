package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.Objects;

/** Source-located explanation of a mapping, approximation, or import failure. */
public record ImportDiagnostic(
        String code,
        ImportDiagnosticSeverity severity,
        SourceSpan span,
        String message,
        String visualImpact,
        String remedy) {

    public ImportDiagnostic {
        requireText(code, "code");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(span, "span");
        requireText(message, "message");
        requireText(visualImpact, "visualImpact");
        requireText(remedy, "remedy");
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
