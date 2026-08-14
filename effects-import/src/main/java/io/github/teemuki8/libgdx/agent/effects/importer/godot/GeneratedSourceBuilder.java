package io.github.teemuki8.libgdx.agent.effects.importer.godot;

import io.github.teemuki8.libgdx.agent.effects.core.ImportLimits;
import io.github.teemuki8.libgdx.agent.effects.core.SourceMapping;
import io.github.teemuki8.libgdx.agent.effects.core.SourceSpan;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Bounded generated-source accumulator with deterministic source mappings. */
final class GeneratedSourceBuilder {
    private final ImportLimits limits;
    private final StringBuilder text = new StringBuilder();
    private final List<SourceMapping> mappings = new ArrayList<>();
    private int line = 1;
    private int column = 1;

    GeneratedSourceBuilder(ImportLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    void append(String value) {
        append(value, null);
    }

    void append(String value, SourceSpan source) {
        Objects.requireNonNull(value, "value");
        if ((long) text.length() + value.length() > limits.maxGeneratedChars()) {
            throw new GodotImportException("GENERATED_SOURCE_LIMIT_EXCEEDED",
                    "generated shader source exceeds import limits",
                    source == null ? new SourceSpan(1, 1, 1, 1, 0, 0) : source);
        }
        int startLine = line;
        int startColumn = column;
        int startOffset = text.length();
        text.append(value);
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        if (source != null && !value.isEmpty()) {
            mappings.add(new SourceMapping(source, new SourceSpan(
                    startLine, startColumn, line, column, startOffset, text.length())));
        }
    }

    String source() {
        return text.toString();
    }

    List<SourceMapping> mappings() {
        return List.copyOf(mappings);
    }
}
