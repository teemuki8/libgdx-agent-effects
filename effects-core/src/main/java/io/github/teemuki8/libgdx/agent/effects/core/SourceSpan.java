package io.github.teemuki8.libgdx.agent.effects.core;

/** Exact half-open source range with one-based lines/columns and zero-based offsets. */
public record SourceSpan(
        int startLine,
        int startColumn,
        int endLine,
        int endColumn,
        int startOffset,
        int endOffset) {

    public SourceSpan {
        if (startLine < 1 || startColumn < 1 || endLine < 1 || endColumn < 1
                || startOffset < 0 || endOffset < startOffset
                || endLine < startLine
                || (endLine == startLine && endColumn < startColumn)) {
            throw new IllegalArgumentException("invalid source span");
        }
    }
}
