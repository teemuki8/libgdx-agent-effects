package io.github.teemuki8.libgdx.agent.effects.importer.godot;

import io.github.teemuki8.libgdx.agent.effects.core.GeneratedShader;
import io.github.teemuki8.libgdx.agent.effects.core.ImportLimits;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderSemantic;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderSource;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderTargetProfile;
import io.github.teemuki8.libgdx.agent.effects.core.SourceMapping;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Deterministic GLSL ES generator for analyzed Godot canvas ASTs. */
final class GodotGlslGenerator {
    private final ImportLimits limits;
    private final GodotAst.GodotShaderAst shader;
    private final GodotSemanticAnalyzer.Analysis analysis;
    private ShaderTargetProfile profile;
    private Stage stage;
    private GeneratedSourceBuilder output;

    GodotGlslGenerator(ImportLimits limits, GodotAst.GodotShaderAst shader,
            GodotSemanticAnalyzer.Analysis analysis) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.shader = Objects.requireNonNull(shader, "shader");
        this.analysis = Objects.requireNonNull(analysis, "analysis");
    }

    GeneratedShader generate(ShaderTargetProfile target) {
        profile = Objects.requireNonNull(target, "target");
        stage = Stage.VERTEX;
        output = new GeneratedSourceBuilder(limits);
        emitHeader();
        emitDeclarations();
        emitHelpers();
        emitVertexMain();
        String vertex = output.source();
        List<SourceMapping> mappings = new ArrayList<>(output.mappings());

        stage = Stage.FRAGMENT;
        output = new GeneratedSourceBuilder(limits);
        emitHeader();
        emitDeclarations();
        emitHelpers();
        emitFragmentMain();
        String fragment = output.source();
        mappings.addAll(output.mappings());
        if ((long) vertex.length() + fragment.length() > limits.maxGeneratedChars()) {
            throw new GodotImportException("GENERATED_SOURCE_LIMIT_EXCEEDED",
                    "generated shader pair exceeds import limits", shader.span());
        }
        return new GeneratedShader(profile, new ShaderSource(vertex, fragment), mappings);
    }

    private void emitHeader() {
        if (profile == ShaderTargetProfile.GLSL_ES_300) {
            output.append("#version 300 es\n");
        }
        output.append("#ifdef GL_ES\nprecision mediump float;\n#endif\n");
        if (stage == Stage.VERTEX) {
            output.append(profile == ShaderTargetProfile.GLSL_ES_100
                    ? "attribute vec2 a_position;\nattribute vec4 a_color;\n"
                    : "in vec2 a_position;\nin vec4 a_color;\n");
            output.append(profile == ShaderTargetProfile.GLSL_ES_100
                    ? "varying vec2 v_uv;\nvarying vec4 v_color;\n"
                    : "out vec2 v_uv;\nout vec4 v_color;\n");
        } else {
            output.append(profile == ShaderTargetProfile.GLSL_ES_100
                    ? "varying vec2 v_uv;\nvarying vec4 v_color;\n"
                    : "in vec2 v_uv;\nin vec4 v_color;\nout vec4 godot_fragColor;\n");
        }
        if (has(ShaderSemantic.SOURCE_TEXTURE)) {
            output.append("uniform sampler2D u_source;\n");
        }
        if (has(ShaderSemantic.SOURCE_TEXEL_SIZE)) {
            output.append("uniform vec2 u_sourceTexelSize;\n");
        }
        if (has(ShaderSemantic.TIME)) {
            output.append("uniform float u_time;\n");
        }
        if (has(ShaderSemantic.SCREEN_TEXTURE)) {
            output.append("uniform sampler2D u_screenTexture;\n");
        }
        if (has(ShaderSemantic.SCREEN_UV)) {
            output.append("uniform vec2 u_resolution;\n");
        }
    }

    private void emitDeclarations() {
        for (GodotAst.Declaration declaration : shader.declarations()) {
            if (declaration instanceof GodotAst.UniformDeclaration uniform) {
                if (!analysis.screenTextureUniforms().contains(uniform.name())) {
                    output.append("uniform " + type(uniform.type()) + " " + uniform.name()
                            + ";\n", uniform.span());
                }
            } else if (declaration instanceof GodotAst.ConstDeclaration constant) {
                output.append("const " + type(constant.type()) + " " + constant.name()
                        + " = ", constant.span());
                emitExpression(constant.initializer(), false);
                output.append(";\n");
            } else if (declaration instanceof GodotAst.StructDeclaration structure) {
                output.append("struct " + structure.name() + " {\n", structure.span());
                for (GodotAst.StructMember member : structure.members()) {
                    output.append("    " + type(member.type()) + " " + member.name()
                            + ";\n", member.span());
                }
                output.append("};\n");
            } else if (declaration instanceof GodotAst.VaryingDeclaration varying) {
                output.append(profile == ShaderTargetProfile.GLSL_ES_100 ? "varying "
                        : stage == Stage.VERTEX ? "out " : "in ");
                output.append(type(varying.type()) + " " + varying.name() + ";\n",
                        varying.span());
            }
        }
    }

    private void emitHelpers() {
        for (GodotAst.FunctionDeclaration function : shader.functions()) {
            if (!Set.of("vertex", "fragment", "light").contains(function.name())) {
                output.append(type(function.returnType()) + " " + function.name() + "(",
                        function.span());
                for (int index = 0; index < function.parameters().size(); index++) {
                    if (index > 0) {
                        output.append(", ");
                    }
                    GodotAst.Parameter parameter = function.parameters().get(index);
                    if (!parameter.qualifier().isEmpty()) {
                        output.append(parameter.qualifier() + " ");
                    }
                    output.append(type(parameter.type()) + " " + parameter.name());
                }
                output.append(") ");
                emitStatement(function.body(), 0);
                output.append("\n");
            }
        }
    }

    private void emitVertexMain() {
        output.append("void main() {\n");
        output.append("    vec2 godot_vertex = a_position;\n");
        output.append("    v_uv = a_position * 0.5 + 0.5;\n");
        output.append("    v_color = a_color;\n");
        processor("vertex").ifPresent(function -> emitBlockContents(function.body(), 1));
        output.append("    gl_Position = vec4(godot_vertex, 0.0, 1.0);\n}\n");
    }

    private void emitFragmentMain() {
        output.append("void main() {\n");
        if (has(ShaderSemantic.SCREEN_UV)) {
            output.append("    vec2 godot_screenUv = gl_FragCoord.xy / u_resolution;\n");
        }
        if (processor("fragment").isPresent()) {
            emitBlockContents(processor("fragment").orElseThrow().body(), 1);
        } else {
            output.append("    " + fragmentOutput() + " = v_color;\n");
        }
        output.append("}\n");
    }

    private java.util.Optional<GodotAst.FunctionDeclaration> processor(String name) {
        return shader.functions().stream().filter(function -> function.name().equals(name)).findFirst();
    }

    private void emitBlockContents(GodotAst.BlockStatement block, int indent) {
        for (GodotAst.Statement statement : block.statements()) {
            indent(indent);
            emitStatement(statement, indent);
            output.append("\n");
        }
    }

    private void emitStatement(GodotAst.Statement statement, int indent) {
        if (statement instanceof GodotAst.BlockStatement block) {
            output.append("{\n", block.span());
            emitBlockContents(block, indent + 1);
            indent(indent);
            output.append("}");
        } else if (statement instanceof GodotAst.VariableStatement variable) {
            emitVariable(variable, true);
        } else if (statement instanceof GodotAst.ExpressionStatement expression) {
            emitExpression(expression.expression(), false);
            output.append(";");
        } else if (statement instanceof GodotAst.IfStatement conditional) {
            output.append("if (");
            emitExpression(conditional.condition(), false);
            output.append(") ");
            emitStatement(conditional.thenBranch(), indent);
            if (conditional.elseBranch() != null) {
                output.append(" else ");
                emitStatement(conditional.elseBranch(), indent);
            }
        } else if (statement instanceof GodotAst.ForStatement loop) {
            output.append("for (");
            if (loop.initializer() instanceof GodotAst.VariableStatement variable) {
                emitVariable(variable, false);
            } else if (loop.initializer() instanceof GodotAst.ExpressionStatement expression) {
                emitExpression(expression.expression(), false);
            }
            output.append("; ");
            emitExpression(loop.condition(), false);
            output.append("; ");
            emitExpression(loop.update(), false);
            output.append(") ");
            emitStatement(loop.body(), indent);
        } else if (statement instanceof GodotAst.ReturnStatement returned) {
            output.append("return");
            if (returned.value() != null) {
                output.append(" ");
                emitExpression(returned.value(), false);
            }
            output.append(";");
        } else if (statement instanceof GodotAst.BreakStatement) {
            output.append("break;");
        } else if (statement instanceof GodotAst.ContinueStatement) {
            output.append("continue;");
        } else if (statement instanceof GodotAst.DiscardStatement) {
            output.append("discard;");
        } else {
            throw new GodotImportException("STATEMENT_UNSUPPORTED",
                    "statement is not supported by the GLSL generator", statement.span());
        }
    }

    private void emitVariable(GodotAst.VariableStatement variable, boolean semicolon) {
        output.append(type(variable.type()) + " " + variable.name(), variable.span());
        if (variable.initializer() != null) {
            output.append(" = ");
            emitExpression(variable.initializer(), false);
        }
        if (semicolon) {
            output.append(";");
        }
    }

    private void emitExpression(GodotAst.Expression expression, boolean assignmentTarget) {
        if (expression == null) {
            return;
        }
        if (expression instanceof GodotAst.LiteralExpression literal) {
            output.append(literal.lexeme(), literal.span());
        } else if (expression instanceof GodotAst.NameExpression name) {
            output.append(mapName(name.name(), assignmentTarget), name.span());
        } else if (expression instanceof GodotAst.UnaryExpression unary) {
            if (unary.operator().equals("()")) {
                output.append("(");
                emitExpression(unary.operand(), false);
                output.append(")");
            } else {
                output.append(unary.operator());
                emitExpression(unary.operand(), false);
            }
        } else if (expression instanceof GodotAst.BinaryExpression binary) {
            output.append("(");
            emitExpression(binary.left(), false);
            output.append(" " + binary.operator() + " ");
            emitExpression(binary.right(), false);
            output.append(")");
        } else if (expression instanceof GodotAst.ConditionalExpression conditional) {
            output.append("(");
            emitExpression(conditional.condition(), false);
            output.append(" ? ");
            emitExpression(conditional.whenTrue(), false);
            output.append(" : ");
            emitExpression(conditional.whenFalse(), false);
            output.append(")");
        } else if (expression instanceof GodotAst.AssignmentExpression assignment) {
            emitExpression(assignment.target(), true);
            output.append(" " + assignment.operator() + " ");
            emitExpression(assignment.value(), false);
        } else if (expression instanceof GodotAst.CallExpression call) {
            if (call.callee() instanceof GodotAst.NameExpression called
                    && called.name().equals("texture")) {
                output.append(profile == ShaderTargetProfile.GLSL_ES_100 ? "texture2D" : "texture");
            } else {
                emitExpression(call.callee(), false);
            }
            output.append("(");
            for (int index = 0; index < call.arguments().size(); index++) {
                if (index > 0) {
                    output.append(", ");
                }
                emitExpression(call.arguments().get(index), false);
            }
            output.append(")");
        } else if (expression instanceof GodotAst.MemberExpression member) {
            emitExpression(member.target(), assignmentTarget);
            output.append("." + member.member());
        } else if (expression instanceof GodotAst.IndexExpression index) {
            emitExpression(index.target(), assignmentTarget);
            output.append("[");
            emitExpression(index.index(), false);
            output.append("]");
        } else if (expression instanceof GodotAst.PostfixExpression postfix) {
            emitExpression(postfix.operand(), true);
            output.append(postfix.operator());
        }
    }

    private String mapName(String name, boolean assignmentTarget) {
        if (analysis.screenTextureUniforms().contains(name)) {
            return "u_screenTexture";
        }
        return switch (name) {
            case "VERTEX" -> "godot_vertex";
            case "UV" -> "v_uv";
            case "COLOR" -> assignmentTarget && stage == Stage.FRAGMENT
                    ? fragmentOutput() : "v_color";
            case "TEXTURE" -> "u_source";
            case "TEXTURE_PIXEL_SIZE" -> "u_sourceTexelSize";
            case "TIME" -> "u_time";
            case "SCREEN_UV" -> "godot_screenUv";
            default -> name;
        };
    }

    private boolean has(ShaderSemantic semantic) {
        return analysis.semantics().contains(semantic);
    }

    private String fragmentOutput() {
        return profile == ShaderTargetProfile.GLSL_ES_100 ? "gl_FragColor" : "godot_fragColor";
    }

    private static String type(GodotAst.TypeReference type) {
        StringBuilder result = new StringBuilder(type.name());
        for (Integer size : type.arraySizes()) {
            result.append('[');
            if (size != null) {
                result.append(size);
            }
            result.append(']');
        }
        return result.toString();
    }

    private void indent(int count) {
        output.append("    ".repeat(count));
    }

    private enum Stage {
        VERTEX,
        FRAGMENT
    }
}
