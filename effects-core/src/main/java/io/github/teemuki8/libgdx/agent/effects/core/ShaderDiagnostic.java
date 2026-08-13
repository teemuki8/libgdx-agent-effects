package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.List;

/** Structured, bounded result of compiling one shader. */
public record ShaderDiagnostic(boolean compiled, List<DiagnosticMessage> messages,
        List<ActiveUniform> uniforms, List<ActiveAttribute> attributes, String infoLog) {
    public ShaderDiagnostic {
        java.util.Objects.requireNonNull(infoLog, "infoLog");
        messages = List.copyOf(messages);
        uniforms = List.copyOf(uniforms);
        attributes = List.copyOf(attributes);
    }
}
