package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns a raw GLSL driver log into a bounded, typed diagnostic. */
public final class ShaderDiagnosticParser {

    private static final Pattern LINE = Pattern.compile(
        "^(ERROR|WARNING)?\\s*:?\\s*0?:(\\d+)(?:\\(\\d+\\))?\\s*:?\\s*(.*)$");

    /** Hard cap on collected messages; a driver log may contain far more lines than we keep. */
    private static final int MAX_MESSAGES = 256;

    public ShaderDiagnostic parse(boolean compiled, String rawLog,
            List<ActiveUniform> uniforms, List<ActiveAttribute> attributes, EffectsLimits limits) {
        List<DiagnosticMessage> messages = new ArrayList<>();
        String log = rawLog == null ? "" : rawLog;
        for (String line : log.split("\\R")) {
            if (messages.size() >= MAX_MESSAGES) {
                break;
            }
            DiagnosticMessage message = parseLine(line, limits);
            if (message != null) {
                messages.add(message);
            }
        }
        String infoLog = log.length() <= limits.maxDiagnosticChars()
            ? log : log.substring(0, limits.maxDiagnosticChars());
        return new ShaderDiagnostic(compiled, List.copyOf(messages),
            List.copyOf(uniforms), List.copyOf(attributes), infoLog);
    }

    private DiagnosticMessage parseLine(String line, EffectsLimits limits) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        Matcher m = LINE.matcher(trimmed);
        if (!m.matches()) {
            return null;
        }
        String severityGroup = m.group(1);
        DiagnosticSeverity severity = "ERROR".equalsIgnoreCase(severityGroup)
            ? DiagnosticSeverity.ERROR : DiagnosticSeverity.WARNING;
        int lineNumber;
        try {
            lineNumber = Integer.parseInt(m.group(2));
        } catch (NumberFormatException e) {
            lineNumber = -1;
        }
        String text = m.group(3);
        if (text == null || text.isBlank()) {
            text = trimmed;
        }
        int maxChars = limits.maxDiagnosticChars();
        if (text.length() > maxChars) {
            text = text.substring(0, maxChars);
        }
        return new DiagnosticMessage(severity, lineNumber, text);
    }
}
