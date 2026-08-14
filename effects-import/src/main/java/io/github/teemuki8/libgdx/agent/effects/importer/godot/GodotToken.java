package io.github.teemuki8.libgdx.agent.effects.importer.godot;

import io.github.teemuki8.libgdx.agent.effects.core.SourceSpan;
import java.util.Objects;

/** One immutable Godot shader token with an exact source span. */
record GodotToken(GodotTokenKind kind, String lexeme, SourceSpan span) {
    GodotToken {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(lexeme, "lexeme");
        Objects.requireNonNull(span, "span");
    }
}
