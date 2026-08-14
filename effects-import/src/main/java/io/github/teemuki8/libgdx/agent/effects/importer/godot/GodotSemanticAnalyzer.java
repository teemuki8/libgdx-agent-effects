package io.github.teemuki8.libgdx.agent.effects.importer.godot;

import io.github.teemuki8.libgdx.agent.effects.core.BlendMode;
import io.github.teemuki8.libgdx.agent.effects.core.FeatureMapping;
import io.github.teemuki8.libgdx.agent.effects.core.ImportDiagnostic;
import io.github.teemuki8.libgdx.agent.effects.core.ImportDiagnosticSeverity;
import io.github.teemuki8.libgdx.agent.effects.core.ImportLimits;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderSemantic;
import io.github.teemuki8.libgdx.agent.effects.core.SourceSpan;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Closed semantic inventory for the supported canvas shader subset. */
final class GodotSemanticAnalyzer {
    private static final Map<String, ShaderSemantic> BUILTINS = Map.ofEntries(
            Map.entry("VERTEX", ShaderSemantic.POSITION),
            Map.entry("UV", ShaderSemantic.UV),
            Map.entry("COLOR", ShaderSemantic.VERTEX_COLOR),
            Map.entry("TEXTURE", ShaderSemantic.SOURCE_TEXTURE),
            Map.entry("TEXTURE_PIXEL_SIZE", ShaderSemantic.SOURCE_TEXEL_SIZE),
            Map.entry("TIME", ShaderSemantic.TIME),
            Map.entry("SCREEN_UV", ShaderSemantic.SCREEN_UV));
    private static final Set<String> SUPPORTED_RENDER_MODES = Set.of(
            "blend_mix", "blend_add", "blend_mul", "blend_sub",
            "blend_premul_alpha", "unshaded", "skip_vertex_transform");

    private final ImportLimits limits;
    private final EnumSet<ShaderSemantic> semantics = EnumSet.noneOf(ShaderSemantic.class);
    private final LinkedHashMap<String, FeatureMapping> mappings = new LinkedHashMap<>();
    private final List<ImportDiagnostic> diagnostics = new ArrayList<>();
    private final Set<String> screenTextureUniforms = new HashSet<>();
    private final Set<String> functionNames = new HashSet<>();
    private BlendMode blendMode = BlendMode.NORMAL;

    GodotSemanticAnalyzer(ImportLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    Analysis analyze(GodotAst.GodotShaderAst shader) {
        analyzeRenderModes(shader);
        for (GodotAst.Declaration declaration : shader.declarations()) {
            if (declaration instanceof GodotAst.UniformDeclaration uniform
                    && uniform.hint() != null
                    && uniform.hint().name().equals("hint_screen_texture")) {
                screenTextureUniforms.add(uniform.name());
                addSemantic("SCREEN_TEXTURE", "u_screenTexture",
                        ShaderSemantic.SCREEN_TEXTURE, uniform.span(), true);
            }
        }
        for (GodotAst.FunctionDeclaration function : shader.functions()) {
            if (!functionNames.add(function.name())) {
                throw unsupported("DUPLICATE_FUNCTION", "function is declared more than once",
                        function.span());
            }
            if (function.name().equals("light")) {
                throw unsupported("GODOT_LIGHT_PROCESSOR_UNSUPPORTED",
                        "canvas light processors are not available in this material target",
                        function.span());
            }
        }
        for (GodotAst.FunctionDeclaration function : shader.functions()) {
            visitStatement(function.body(), function.name());
        }
        return new Analysis(blendMode, orderedSemantics(), List.copyOf(mappings.values()),
                diagnostics, Set.copyOf(screenTextureUniforms));
    }

    private void analyzeRenderModes(GodotAst.GodotShaderAst shader) {
        for (String mode : shader.renderModes()) {
            if (!SUPPORTED_RENDER_MODES.contains(mode)) {
                throw unsupported("GODOT_RENDER_MODE_UNSUPPORTED",
                        "render mode is not supported by the portable material target",
                        shader.span());
            }
            blendMode = switch (mode) {
                case "blend_add" -> BlendMode.ADDITIVE;
                case "blend_mul" -> BlendMode.MULTIPLY;
                case "blend_sub" -> BlendMode.SUBTRACT;
                case "blend_premul_alpha" -> {
                    addDiagnostic(new ImportDiagnostic(
                            "GODOT_BLEND_PREMULTIPLIED_APPROXIMATION",
                            ImportDiagnosticSeverity.WARNING, shader.span(),
                            "premultiplied alpha is mapped to normal alpha blending",
                            "edge color may differ where alpha is partially transparent",
                            "supply premultiplied source colors or qualify against a reference"));
                    yield BlendMode.NORMAL;
                }
                default -> blendMode;
            };
        }
    }

    private void visitStatement(GodotAst.Statement statement, String functionName) {
        if (statement instanceof GodotAst.BlockStatement block) {
            block.statements().forEach(child -> visitStatement(child, functionName));
        } else if (statement instanceof GodotAst.VariableStatement variable) {
            visitExpression(variable.initializer(), functionName, false);
        } else if (statement instanceof GodotAst.ExpressionStatement expression) {
            visitExpression(expression.expression(), functionName, false);
        } else if (statement instanceof GodotAst.IfStatement conditional) {
            visitExpression(conditional.condition(), functionName, false);
            visitStatement(conditional.thenBranch(), functionName);
            if (conditional.elseBranch() != null) {
                visitStatement(conditional.elseBranch(), functionName);
            }
        } else if (statement instanceof GodotAst.ForStatement loop) {
            if (loop.condition() == null) {
                throw unsupported("UNSAFE_DYNAMIC_LOOP",
                        "unbounded loops are not imported", loop.span());
            }
            if (loop.initializer() != null) {
                visitStatement(loop.initializer(), functionName);
            }
            visitExpression(loop.condition(), functionName, false);
            visitExpression(loop.update(), functionName, false);
            visitStatement(loop.body(), functionName);
        } else if (statement instanceof GodotAst.WhileStatement loop) {
            throw unsupported("UNSAFE_DYNAMIC_LOOP",
                    "while loops cannot be proven bounded during import", loop.span());
        } else if (statement instanceof GodotAst.ReturnStatement returned) {
            visitExpression(returned.value(), functionName, false);
        }
    }

    private void visitExpression(GodotAst.Expression expression, String functionName,
            boolean assignmentTarget) {
        if (expression == null) {
            return;
        }
        if (expression instanceof GodotAst.NameExpression name) {
            visitName(name, assignmentTarget);
        } else if (expression instanceof GodotAst.UnaryExpression unary) {
            visitExpression(unary.operand(), functionName, false);
        } else if (expression instanceof GodotAst.BinaryExpression binary) {
            visitExpression(binary.left(), functionName, false);
            visitExpression(binary.right(), functionName, false);
        } else if (expression instanceof GodotAst.ConditionalExpression conditional) {
            visitExpression(conditional.condition(), functionName, false);
            visitExpression(conditional.whenTrue(), functionName, false);
            visitExpression(conditional.whenFalse(), functionName, false);
        } else if (expression instanceof GodotAst.AssignmentExpression assignment) {
            visitExpression(assignment.value(), functionName, false);
            visitExpression(assignment.target(), functionName, true);
        } else if (expression instanceof GodotAst.CallExpression call) {
            if (call.callee() instanceof GodotAst.NameExpression called) {
                if (called.name().equals(functionName)) {
                    throw unsupported("RECURSION_UNSUPPORTED",
                            "recursive functions are not imported", call.span());
                }
                if (called.name().contains("sdf") || called.name().contains("SDF")) {
                    throw unsupported("GODOT_SDF_UNSUPPORTED",
                            "Godot SDF inputs are unavailable", call.span());
                }
            } else {
                visitExpression(call.callee(), functionName, false);
            }
            call.arguments().forEach(argument -> visitExpression(argument, functionName, false));
        } else if (expression instanceof GodotAst.MemberExpression member) {
            visitExpression(member.target(), functionName, assignmentTarget);
        } else if (expression instanceof GodotAst.IndexExpression index) {
            visitExpression(index.target(), functionName, assignmentTarget);
            visitExpression(index.index(), functionName, false);
        } else if (expression instanceof GodotAst.PostfixExpression postfix) {
            visitExpression(postfix.operand(), functionName, true);
        }
    }

    private void visitName(GodotAst.NameExpression name, boolean assignmentTarget) {
        ShaderSemantic semantic = BUILTINS.get(name.name());
        if (semantic != null) {
            String target = name.name().equals("COLOR") && assignmentTarget
                    ? "fragment output" : targetName(name.name());
            addSemantic(name.name(), target, semantic, name.span(), true);
        } else if (name.name().equals("DEPTH") || name.name().startsWith("SDF")) {
            throw unsupported("GODOT_BUILTIN_UNAVAILABLE",
                    "source shader requires an unavailable engine input", name.span());
        }
    }

    private void addSemantic(String source, String target, ShaderSemantic semantic,
            SourceSpan span, boolean direct) {
        semantics.add(semantic);
        mappings.computeIfAbsent(source,
                ignored -> new FeatureMapping(source, target, direct, span));
        if (mappings.size() > limits.maxFeatureMappings()) {
            throw unsupported("FEATURE_MAPPING_LIMIT_EXCEEDED",
                    "feature mapping count exceeds import limits", span);
        }
    }

    private void addDiagnostic(ImportDiagnostic diagnostic) {
        if (diagnostics.size() >= limits.maxDiagnostics()) {
            throw unsupported("DIAGNOSTIC_LIMIT_EXCEEDED",
                    "diagnostic count exceeds import limits", diagnostic.span());
        }
        diagnostics.add(diagnostic);
    }

    private List<ShaderSemantic> orderedSemantics() {
        List<ShaderSemantic> ordered = new ArrayList<>();
        for (ShaderSemantic semantic : ShaderSemantic.values()) {
            if (semantics.contains(semantic)) {
                ordered.add(semantic);
            }
        }
        return List.copyOf(ordered);
    }

    private static String targetName(String source) {
        return switch (source) {
            case "VERTEX" -> "godot_vertex";
            case "UV" -> "v_uv";
            case "COLOR" -> "v_color";
            case "TEXTURE" -> "u_source";
            case "TEXTURE_PIXEL_SIZE" -> "u_sourceTexelSize";
            case "TIME" -> "u_time";
            case "SCREEN_UV" -> "godot_screenUv";
            default -> throw new IllegalArgumentException("unknown builtin");
        };
    }

    private static GodotImportException unsupported(String code, String message, SourceSpan span) {
        return new GodotImportException(code, message, span);
    }

    record Analysis(
            BlendMode blendMode,
            List<ShaderSemantic> semantics,
            List<FeatureMapping> mappings,
            List<ImportDiagnostic> diagnostics,
            Set<String> screenTextureUniforms) {
        Analysis {
            Objects.requireNonNull(blendMode, "blendMode");
            semantics = List.copyOf(semantics);
            mappings = List.copyOf(mappings);
            diagnostics = List.copyOf(diagnostics);
            screenTextureUniforms = Set.copyOf(screenTextureUniforms);
        }
    }
}
