package io.github.teemuki8.libgdx.agent.effects.importer.godot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.teemuki8.libgdx.agent.effects.core.ImportLimits;
import java.util.List;
import org.junit.jupiter.api.Test;

class GodotLexerTest {

    @Test
    void lexesCanvasShaderWithExactKindsAndSpans() {
        String source = """
                shader_type canvas_item;
                render_mode blend_add;
                // one-line comment
                uniform float strength : hint_range(0.0, 1.0) = 5e-1;
                const int mask = 0x10;
                /* block
                   comment */
                void fragment() {
                    COLOR.rgb *= vec3(strength);
                }
                """;

        List<GodotToken> tokens = new GodotLexer(source, limits(256)).lex();

        assertEquals(GodotTokenKind.SHADER_TYPE, tokens.get(0).kind());
        assertEquals("shader_type", tokens.get(0).lexeme());
        assertEquals(1, tokens.get(0).span().startLine());
        assertEquals(1, tokens.get(0).span().startColumn());
        assertEquals(GodotTokenKind.RENDER_MODE, tokens.get(3).kind());
        assertEquals(GodotTokenKind.FLOAT_LITERAL,
                token(tokens, "5e-1").kind());
        assertEquals(GodotTokenKind.INTEGER_LITERAL,
                token(tokens, "0x10").kind());
        assertEquals(4, token(tokens, "strength").span().startLine());
        assertEquals(GodotTokenKind.STAR_EQUAL, token(tokens, "*=").kind());
        assertEquals(GodotTokenKind.EOF, tokens.getLast().kind());
        assertEquals(11, tokens.getLast().span().startLine());
    }

    @Test
    void rejectsUnresolvedIncludesWithoutEchoingThePath() {
        GodotImportException failure = assertThrows(GodotImportException.class,
                () -> new GodotLexer("#include \"private/path.gdshaderinc\"",
                        limits(32)).lex());

        assertEquals("UNRESOLVED_INCLUDE", failure.code());
        assertEquals("shader includes are not supported", failure.getMessage());
        assertEquals(1, failure.span().startLine());
    }

    @Test
    void rejectsMalformedInputAndConfiguredBounds() {
        assertEquals("UNTERMINATED_COMMENT", failureCode("/* never closed", limits(16)));
        assertEquals("INVALID_NUMBER", failureCode("1e+", limits(16)));
        assertEquals("UNEXPECTED_CHARACTER", failureCode("@", limits(16)));
        assertEquals("SOURCE_LIMIT_EXCEEDED", failureCode("abcd", new ImportLimits(
                3, 32, 16, 8, 8, 8, 8, 8, 8, 8, 8)));
        assertEquals("TOKEN_LIMIT_EXCEEDED", failureCode("a b c", limits(2)));
    }

    private static GodotToken token(List<GodotToken> tokens, String lexeme) {
        return tokens.stream().filter(token -> token.lexeme().equals(lexeme)).findFirst()
                .orElseThrow();
    }

    private static String failureCode(String source, ImportLimits limits) {
        return assertThrows(GodotImportException.class,
                () -> new GodotLexer(source, limits).lex()).code();
    }

    private static ImportLimits limits(int maxTokens) {
        return new ImportLimits(
                4096, 4096, maxTokens, 16, 16, 16, 16, 64, 128, 16, 32);
    }
}
