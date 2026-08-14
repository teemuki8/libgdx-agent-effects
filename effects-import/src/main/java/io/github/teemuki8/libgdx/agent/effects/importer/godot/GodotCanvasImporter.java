package io.github.teemuki8.libgdx.agent.effects.importer.godot;

import io.github.teemuki8.libgdx.agent.effects.core.AssetKey;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.FidelityClassification;
import io.github.teemuki8.libgdx.agent.effects.core.GeneratedShader;
import io.github.teemuki8.libgdx.agent.effects.core.ImportDiagnostic;
import io.github.teemuki8.libgdx.agent.effects.core.ImportDiagnosticSeverity;
import io.github.teemuki8.libgdx.agent.effects.core.ImportLimits;
import io.github.teemuki8.libgdx.agent.effects.core.Material2dDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderImportRequest;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderImportResult;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderSemantic;
import io.github.teemuki8.libgdx.agent.effects.core.SourceSpan;
import io.github.teemuki8.libgdx.agent.effects.core.UniformBinding;
import io.github.teemuki8.libgdx.agent.effects.core.UniformValue;
import io.github.teemuki8.libgdx.agent.effects.importer.ShaderImporter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Imports a bounded Godot 4 {@code canvas_item} shader into portable libGDX GLSL. */
public final class GodotCanvasImporter implements ShaderImporter {
    private final ImportLimits limits;

    /** Creates an importer constrained by the supplied finite limits. */
    public GodotCanvasImporter(ImportLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    /** Parses, analyzes, and generates each requested target without resolving external assets. */
    @Override public ShaderImportResult importShader(ShaderImportRequest request) {
        Objects.requireNonNull(request, "request");
        try {
            request.validate(limits);
            GodotAst.GodotShaderAst shader = new GodotParser(
                    new GodotLexer(request.source(), limits).lex(), limits).parse();
            GodotSemanticAnalyzer.Analysis analysis =
                    new GodotSemanticAnalyzer(limits).analyze(shader);
            List<GeneratedShader> generated = new ArrayList<>();
            GodotGlslGenerator generator = new GodotGlslGenerator(limits, shader, analysis);
            for (var target : request.targets()) {
                generated.add(generator.generate(target));
            }
            List<UniformBinding> uniforms = uniforms(shader);
            List<AssetKey> textures = textures(analysis.semantics());
            Material2dDefinition material = new Material2dDefinition(
                    request.name(), generated.getFirst().shader(), analysis.blendMode(),
                    uniforms, textures);
            FidelityClassification fidelity = analysis.diagnostics().isEmpty()
                    ? FidelityClassification.STRUCTURALLY_EQUIVALENT
                    : FidelityClassification.APPROXIMATED;
            return new ShaderImportResult(request.name(), material, generated,
                    analysis.semantics(), analysis.mappings(), analysis.diagnostics(), fidelity)
                    .validate(limits, EffectsLimits.developmentDefaults());
        } catch (GodotImportException failure) {
            return unsupported(request.name(), failure.code(), failure.getMessage(), failure.span());
        } catch (io.github.teemuki8.libgdx.agent.effects.core.EffectsException failure) {
            return unsupported(request.name(), "IMPORT_LIMIT_EXCEEDED", failure.getMessage(),
                    sourceSpan(request.source()));
        }
    }

    private static List<UniformBinding> uniforms(GodotAst.GodotShaderAst shader) {
        List<UniformBinding> bindings = new ArrayList<>();
        for (GodotAst.Declaration declaration : shader.declarations()) {
            if (declaration instanceof GodotAst.UniformDeclaration uniform
                    && !uniform.type().name().startsWith("sampler")) {
                bindings.add(new UniformBinding(uniform.name(), uniformValue(uniform)));
            }
        }
        return List.copyOf(bindings);
    }

    private static UniformValue uniformValue(GodotAst.UniformDeclaration uniform) {
        if (uniform.initializer() instanceof GodotAst.LiteralExpression literal) {
            return switch (uniform.type().name()) {
                case "int", "uint" -> new UniformValue.Int(parseInteger(literal.lexeme()));
                case "bool" -> new UniformValue.Int(literal.lexeme().equals("true") ? 1 : 0);
                default -> new UniformValue.Float(Float.parseFloat(literal.lexeme()));
            };
        }
        if (uniform.initializer() instanceof GodotAst.CallExpression call
                && call.callee() instanceof GodotAst.NameExpression constructor) {
            float[] values = literalArguments(call.arguments());
            return switch (constructor.name()) {
                case "vec2" -> new UniformValue.Vec2(values[0], values[1]);
                case "vec3" -> new UniformValue.Vec3(values[0], values[1], values[2]);
                case "vec4" -> new UniformValue.Vec4(values[0], values[1], values[2], values[3]);
                default -> zeroValue(uniform.type().name());
            };
        }
        return zeroValue(uniform.type().name());
    }

    private static float[] literalArguments(List<GodotAst.Expression> arguments) {
        float[] values = new float[4];
        for (int index = 0; index < arguments.size() && index < values.length; index++) {
            if (arguments.get(index) instanceof GodotAst.LiteralExpression literal) {
                values[index] = Float.parseFloat(literal.lexeme());
            }
        }
        return values;
    }

    private static int parseInteger(String value) {
        return value.startsWith("0x") || value.startsWith("0X")
                ? Integer.parseUnsignedInt(value.substring(2), 16) : Integer.parseInt(value);
    }

    private static UniformValue zeroValue(String type) {
        return switch (type) {
            case "int", "uint", "bool" -> new UniformValue.Int(0);
            case "vec2", "ivec2", "uvec2" -> new UniformValue.Vec2(0, 0);
            case "vec3", "ivec3", "uvec3" -> new UniformValue.Vec3(0, 0, 0);
            case "vec4", "ivec4", "uvec4" -> new UniformValue.Vec4(0, 0, 0, 0);
            default -> new UniformValue.Float(0);
        };
    }

    private static List<AssetKey> textures(List<ShaderSemantic> semantics) {
        List<AssetKey> textures = new ArrayList<>();
        if (semantics.contains(ShaderSemantic.SOURCE_TEXTURE)) {
            textures.add(new AssetKey("source"));
        }
        if (semantics.contains(ShaderSemantic.SCREEN_TEXTURE)) {
            textures.add(new AssetKey("screen"));
        }
        return List.copyOf(textures);
    }

    private static ShaderImportResult unsupported(
            String name, String code, String message, SourceSpan span) {
        ImportDiagnostic diagnostic = new ImportDiagnostic(code, ImportDiagnosticSeverity.ERROR,
                span, message, "the shader cannot be represented by this importer",
                "remove the unsupported feature or provide a native libGDX shader");
        return new ShaderImportResult(name, null, List.of(), List.of(), List.of(),
                List.of(diagnostic), FidelityClassification.UNSUPPORTED);
    }

    private static SourceSpan sourceSpan(String source) {
        int line = 1;
        int column = 1;
        for (int index = 0; index < source.length(); index++) {
            if (source.charAt(index) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return new SourceSpan(1, 1, line, column, 0, source.length());
    }
}
