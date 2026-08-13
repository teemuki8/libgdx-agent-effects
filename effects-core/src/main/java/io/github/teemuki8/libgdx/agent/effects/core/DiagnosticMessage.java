package io.github.teemuki8.libgdx.agent.effects.core;

/** One severity-tagged diagnostic message with a best-effort line number. */
public record DiagnosticMessage(DiagnosticSeverity severity, int line, String text) {
    public DiagnosticMessage {
        java.util.Objects.requireNonNull(severity, "severity");
        java.util.Objects.requireNonNull(text, "text");
    }
}
