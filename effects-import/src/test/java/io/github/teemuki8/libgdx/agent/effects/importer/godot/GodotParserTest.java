package io.github.teemuki8.libgdx.agent.effects.importer.godot;

import static io.github.teemuki8.libgdx.agent.effects.importer.godot.GodotAst.BinaryExpression;
import static io.github.teemuki8.libgdx.agent.effects.importer.godot.GodotAst.GodotShaderAst;
import static io.github.teemuki8.libgdx.agent.effects.importer.godot.GodotAst.ReturnStatement;
import static io.github.teemuki8.libgdx.agent.effects.importer.godot.GodotAst.UniformDeclaration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.teemuki8.libgdx.agent.effects.core.ImportLimits;
import java.util.List;
import org.junit.jupiter.api.Test;

class GodotParserTest {

    @Test
    void parsesTypedCanvasShaderWithStableOrderAndPrecedence() {
        String source = """
                shader_type canvas_item;
                render_mode blend_add, unshaded;
                uniform float strength : hint_range(0.0, 1.0) = 0.5;
                struct Pair { vec2 first; vec2 second; };
                float scale_value(float value) { return value * 2.0 + 1.0; }
                void vertex() { VERTEX.x += strength; }
                void fragment() {
                    vec4 sampled = texture(TEXTURE, UV);
                    if (strength > 0.0) {
                        COLOR = sampled * scale_value(strength);
                    } else {
                        COLOR = sampled;
                    }
                }
                """;

        GodotShaderAst shader = parse(source, limits(32));

        assertEquals("canvas_item", shader.shaderType());
        assertEquals(List.of("blend_add", "unshaded"), shader.renderModes());
        assertEquals(2, shader.declarations().size());
        UniformDeclaration uniform = assertInstanceOf(
                UniformDeclaration.class, shader.declarations().get(0));
        assertEquals("float", uniform.type().name());
        assertEquals("strength", uniform.name());
        assertEquals("hint_range", uniform.hint().name());
        assertEquals(3, shader.functions().size());
        assertEquals("scale_value", shader.functions().get(0).name());
        ReturnStatement returned = assertInstanceOf(ReturnStatement.class,
                shader.functions().get(0).body().statements().get(0));
        BinaryExpression addition = assertInstanceOf(
                BinaryExpression.class, returned.value());
        assertEquals("+", addition.operator());
        assertInstanceOf(BinaryExpression.class, addition.left());
        assertEquals("vertex", shader.functions().get(1).name());
        assertEquals("fragment", shader.functions().get(2).name());
        assertEquals(1, shader.span().startLine());
    }

    @Test
    void rejectsUnsupportedTypeDuplicateProcessorAndMalformedSyntax() {
        assertEquals("UNSUPPORTED_SHADER_TYPE", failureCode(
                "shader_type spatial; void fragment() {}", limits(16)));
        assertEquals("DUPLICATE_PROCESSOR", failureCode(
                "shader_type canvas_item; void fragment() {} void fragment() {}",
                limits(16)));
        assertEquals("EXPECTED_TOKEN", failureCode(
                "shader_type canvas_item; uniform float missing",
                limits(16)));
    }

    @Test
    void rejectsAstNestingBeyondConfiguredLimit() {
        ImportLimits shallow = new ImportLimits(
                4096, 4096, 256, 2, 32, 32, 16, 128, 256, 32, 64);

        assertEquals("AST_DEPTH_EXCEEDED", failureCode(
                "shader_type canvas_item; void fragment(){ if(true){ if(true){} } }",
                shallow));
    }

    private static GodotShaderAst parse(String source, ImportLimits limits) {
        return new GodotParser(new GodotLexer(source, limits).lex(), limits).parse();
    }

    private static String failureCode(String source, ImportLimits limits) {
        return assertThrows(GodotImportException.class,
                () -> parse(source, limits)).code();
    }

    private static ImportLimits limits(int maxDepth) {
        return new ImportLimits(
                16 * 1024, 16 * 1024, 4096, maxDepth, 128, 64, 32,
                1024, 4096, 64, 128);
    }
}
