package io.github.teemuki8.libgdx.agent.effects.importer.godot;

import io.github.teemuki8.libgdx.agent.effects.core.ImportLimits;
import io.github.teemuki8.libgdx.agent.effects.core.SourceSpan;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Single-pass bounded lexer for Godot shader source. */
final class GodotLexer {
    private static final Map<String, GodotTokenKind> KEYWORDS = Map.ofEntries(
            Map.entry("shader_type", GodotTokenKind.SHADER_TYPE),
            Map.entry("render_mode", GodotTokenKind.RENDER_MODE),
            Map.entry("uniform", GodotTokenKind.UNIFORM),
            Map.entry("varying", GodotTokenKind.VARYING),
            Map.entry("const", GodotTokenKind.CONST),
            Map.entry("struct", GodotTokenKind.STRUCT),
            Map.entry("void", GodotTokenKind.VOID),
            Map.entry("if", GodotTokenKind.IF),
            Map.entry("else", GodotTokenKind.ELSE),
            Map.entry("for", GodotTokenKind.FOR),
            Map.entry("while", GodotTokenKind.WHILE),
            Map.entry("do", GodotTokenKind.DO),
            Map.entry("switch", GodotTokenKind.SWITCH),
            Map.entry("case", GodotTokenKind.CASE),
            Map.entry("default", GodotTokenKind.DEFAULT),
            Map.entry("break", GodotTokenKind.BREAK),
            Map.entry("continue", GodotTokenKind.CONTINUE),
            Map.entry("return", GodotTokenKind.RETURN),
            Map.entry("discard", GodotTokenKind.DISCARD),
            Map.entry("in", GodotTokenKind.IN),
            Map.entry("out", GodotTokenKind.OUT),
            Map.entry("inout", GodotTokenKind.INOUT),
            Map.entry("true", GodotTokenKind.BOOLEAN_LITERAL),
            Map.entry("false", GodotTokenKind.BOOLEAN_LITERAL));

    private final String source;
    private final ImportLimits limits;
    private final List<GodotToken> tokens = new ArrayList<>();
    private int offset;
    private int line = 1;
    private int column = 1;

    GodotLexer(String source, ImportLimits limits) {
        this.source = Objects.requireNonNull(source, "source");
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    List<GodotToken> lex() {
        if (source.length() > limits.maxSourceChars()) {
            throw failure("SOURCE_LIMIT_EXCEEDED", "shader source exceeds import limits",
                    positionSpan());
        }
        while (!atEnd()) {
            skipTrivia();
            if (atEnd()) {
                break;
            }
            scanToken();
        }
        add(GodotTokenKind.EOF, offset, line, column);
        return List.copyOf(tokens);
    }

    private void scanToken() {
        int start = offset;
        int startLine = line;
        int startColumn = column;
        char current = advance();
        if (isIdentifierStart(current)) {
            identifier(start, startLine, startColumn);
            return;
        }
        if (isDigit(current) || (current == '.' && isDigit(peek()))) {
            number(start, startLine, startColumn, current);
            return;
        }
        switch (current) {
            case '(' -> add(GodotTokenKind.LEFT_PAREN, start, startLine, startColumn);
            case ')' -> add(GodotTokenKind.RIGHT_PAREN, start, startLine, startColumn);
            case '{' -> add(GodotTokenKind.LEFT_BRACE, start, startLine, startColumn);
            case '}' -> add(GodotTokenKind.RIGHT_BRACE, start, startLine, startColumn);
            case '[' -> add(GodotTokenKind.LEFT_BRACKET, start, startLine, startColumn);
            case ']' -> add(GodotTokenKind.RIGHT_BRACKET, start, startLine, startColumn);
            case ',' -> add(GodotTokenKind.COMMA, start, startLine, startColumn);
            case '.' -> add(GodotTokenKind.DOT, start, startLine, startColumn);
            case ';' -> add(GodotTokenKind.SEMICOLON, start, startLine, startColumn);
            case ':' -> add(GodotTokenKind.COLON, start, startLine, startColumn);
            case '?' -> add(GodotTokenKind.QUESTION, start, startLine, startColumn);
            case '~' -> add(GodotTokenKind.TILDE, start, startLine, startColumn);
            case '+' -> add(match('+') ? GodotTokenKind.PLUS_PLUS
                    : match('=') ? GodotTokenKind.PLUS_EQUAL : GodotTokenKind.PLUS,
                    start, startLine, startColumn);
            case '-' -> add(match('-') ? GodotTokenKind.MINUS_MINUS
                    : match('=') ? GodotTokenKind.MINUS_EQUAL : GodotTokenKind.MINUS,
                    start, startLine, startColumn);
            case '*' -> add(match('=') ? GodotTokenKind.STAR_EQUAL : GodotTokenKind.STAR,
                    start, startLine, startColumn);
            case '/' -> add(match('=') ? GodotTokenKind.SLASH_EQUAL : GodotTokenKind.SLASH,
                    start, startLine, startColumn);
            case '%' -> add(match('=') ? GodotTokenKind.PERCENT_EQUAL : GodotTokenKind.PERCENT,
                    start, startLine, startColumn);
            case '!' -> add(match('=') ? GodotTokenKind.BANG_EQUAL : GodotTokenKind.BANG,
                    start, startLine, startColumn);
            case '=' -> add(match('=') ? GodotTokenKind.EQUAL_EQUAL : GodotTokenKind.EQUAL,
                    start, startLine, startColumn);
            case '&' -> add(match('&') ? GodotTokenKind.AND_AND
                    : match('=') ? GodotTokenKind.AND_EQUAL : GodotTokenKind.AND,
                    start, startLine, startColumn);
            case '|' -> add(match('|') ? GodotTokenKind.OR_OR
                    : match('=') ? GodotTokenKind.OR_EQUAL : GodotTokenKind.OR,
                    start, startLine, startColumn);
            case '^' -> add(match('=') ? GodotTokenKind.XOR_EQUAL : GodotTokenKind.XOR,
                    start, startLine, startColumn);
            case '<' -> scanAngle(true, start, startLine, startColumn);
            case '>' -> scanAngle(false, start, startLine, startColumn);
            case '#' -> preprocessor(start, startLine, startColumn);
            default -> throw failure("UNEXPECTED_CHARACTER",
                    "unexpected character in shader source",
                    span(start, startLine, startColumn));
        }
    }

    private void scanAngle(boolean left, int start, int startLine, int startColumn) {
        if (match(left ? '<' : '>')) {
            GodotTokenKind kind;
            if (match('=')) {
                kind = left ? GodotTokenKind.SHIFT_LEFT_EQUAL
                        : GodotTokenKind.SHIFT_RIGHT_EQUAL;
            } else {
                kind = left ? GodotTokenKind.SHIFT_LEFT : GodotTokenKind.SHIFT_RIGHT;
            }
            add(kind, start, startLine, startColumn);
            return;
        }
        add(match('=')
                ? (left ? GodotTokenKind.LESS_EQUAL : GodotTokenKind.GREATER_EQUAL)
                : (left ? GodotTokenKind.LESS : GodotTokenKind.GREATER),
                start, startLine, startColumn);
    }

    private void identifier(int start, int startLine, int startColumn) {
        while (isIdentifierPart(peek())) {
            advance();
        }
        String lexeme = source.substring(start, offset);
        add(KEYWORDS.getOrDefault(lexeme, GodotTokenKind.IDENTIFIER),
                start, startLine, startColumn);
    }

    private void number(int start, int startLine, int startColumn, char first) {
        boolean floating = first == '.';
        if (first == '0' && (peek() == 'x' || peek() == 'X')) {
            advance();
            int digitsStart = offset;
            while (isHexDigit(peek())) {
                advance();
            }
            if (digitsStart == offset || isIdentifierPart(peek())) {
                throw failure("INVALID_NUMBER", "invalid numeric literal",
                        span(start, startLine, startColumn));
            }
            add(GodotTokenKind.INTEGER_LITERAL, start, startLine, startColumn);
            return;
        }
        while (isDigit(peek())) {
            advance();
        }
        if (peek() == '.' && peekNext() != '.') {
            floating = true;
            advance();
            while (isDigit(peek())) {
                advance();
            }
        }
        if (peek() == 'e' || peek() == 'E') {
            floating = true;
            advance();
            if (peek() == '+' || peek() == '-') {
                advance();
            }
            int exponentStart = offset;
            while (isDigit(peek())) {
                advance();
            }
            if (exponentStart == offset) {
                throw failure("INVALID_NUMBER", "invalid numeric literal",
                        span(start, startLine, startColumn));
            }
        }
        if (isIdentifierStart(peek())) {
            throw failure("INVALID_NUMBER", "invalid numeric literal",
                    span(start, startLine, startColumn));
        }
        add(floating ? GodotTokenKind.FLOAT_LITERAL : GodotTokenKind.INTEGER_LITERAL,
                start, startLine, startColumn);
    }

    private void preprocessor(int start, int startLine, int startColumn) {
        while (peek() == ' ' || peek() == '\t') {
            advance();
        }
        int directiveStart = offset;
        while (isIdentifierPart(peek())) {
            advance();
        }
        String directive = source.substring(directiveStart, offset);
        if (directive.equals("include")) {
            throw failure("UNRESOLVED_INCLUDE", "shader includes are not supported",
                    span(start, startLine, startColumn));
        }
        throw failure("UNSUPPORTED_PREPROCESSOR", "preprocessor directive is not supported",
                span(start, startLine, startColumn));
    }

    private void skipTrivia() {
        boolean skipped;
        do {
            skipped = false;
            while (Character.isWhitespace(peek())) {
                advance();
                skipped = true;
            }
            if (peek() == '/' && peekNext() == '/') {
                while (!atEnd() && peek() != '\n') {
                    advance();
                }
                skipped = true;
            } else if (peek() == '/' && peekNext() == '*') {
                int start = offset;
                int startLine = line;
                int startColumn = column;
                advance();
                advance();
                while (!atEnd() && !(peek() == '*' && peekNext() == '/')) {
                    advance();
                }
                if (atEnd()) {
                    throw failure("UNTERMINATED_COMMENT", "unterminated block comment",
                            span(start, startLine, startColumn));
                }
                advance();
                advance();
                skipped = true;
            }
        } while (skipped);
    }

    private void add(GodotTokenKind kind, int start, int startLine, int startColumn) {
        if (tokens.size() >= limits.maxTokens()) {
            throw failure("TOKEN_LIMIT_EXCEEDED", "shader token count exceeds import limits",
                    span(start, startLine, startColumn));
        }
        tokens.add(new GodotToken(kind, source.substring(start, offset),
                span(start, startLine, startColumn)));
    }

    private SourceSpan span(int start, int startLine, int startColumn) {
        return new SourceSpan(startLine, startColumn, line, column, start, offset);
    }

    private SourceSpan positionSpan() {
        return new SourceSpan(line, column, line, column, offset, offset);
    }

    private GodotImportException failure(String code, String message, SourceSpan span) {
        return new GodotImportException(code, message, span);
    }

    private char advance() {
        char value = source.charAt(offset++);
        if (value == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }
        return value;
    }

    private boolean match(char expected) {
        if (peek() != expected) {
            return false;
        }
        advance();
        return true;
    }

    private char peek() {
        return atEnd() ? '\0' : source.charAt(offset);
    }

    private char peekNext() {
        return offset + 1 >= source.length() ? '\0' : source.charAt(offset + 1);
    }

    private boolean atEnd() {
        return offset >= source.length();
    }

    private static boolean isIdentifierStart(char value) {
        return Character.isLetter(value) || value == '_';
    }

    private static boolean isIdentifierPart(char value) {
        return isIdentifierStart(value) || isDigit(value);
    }

    private static boolean isDigit(char value) {
        return value >= '0' && value <= '9';
    }

    private static boolean isHexDigit(char value) {
        return isDigit(value) || value >= 'a' && value <= 'f'
                || value >= 'A' && value <= 'F';
    }
}
