package io.github.teemuki8.libgdx.agent.effects.libgdx;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import io.github.teemuki8.libgdx.agent.effects.core.ActiveAttribute;
import io.github.teemuki8.libgdx.agent.effects.core.ActiveUniform;
import io.github.teemuki8.libgdx.agent.effects.core.EffectDescription;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderDiagnostic;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderDiagnosticParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Compiles a declared effect's shader into structured diagnostics. Render-thread confined. */
public final class EffectCompiler {

    private final EffectsLimits limits;
    private final Thread ownerThread = Thread.currentThread();
    private final ShaderDiagnosticParser parser = new ShaderDiagnosticParser();

    public EffectCompiler(EffectsLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public CompiledEffect compile(EffectDescription effect) {
        if (Thread.currentThread() != ownerThread) {
            throw new EffectsException(EffectsException.Kind.WRONG_THREAD,
                "EffectCompiler must be used on its owning render thread");
        }
        effect.validate(limits);
        ShaderProgram program = new ShaderProgram(
            effect.shader().vertex(), effect.shader().fragment());
        List<ActiveUniform> uniforms = new ArrayList<>();
        List<ActiveAttribute> attributes = new ArrayList<>();
        if (program.isCompiled()) {
            for (String name : program.getUniforms()) {
                uniforms.add(new ActiveUniform(name, typeName(program.getUniformType(name)),
                    program.getUniformSize(name)));
            }
            for (String name : program.getAttributes()) {
                attributes.add(new ActiveAttribute(name, typeName(program.getAttributeType(name))));
            }
        }
        ShaderDiagnostic diagnostic = parser.parse(program.isCompiled(), program.getLog(),
            uniforms, attributes, limits);
        if (!program.isCompiled()) {
            program.dispose();
            return new CompiledEffect(diagnostic, null);
        }
        return new CompiledEffect(diagnostic, program);
    }

    /** Maps a GL type constant to its GLSL type name (fallback: hex constant). */
    private static String typeName(int type) {
        return switch (type) {
            case GL20.GL_FLOAT -> "float";
            case GL20.GL_FLOAT_VEC2 -> "vec2";
            case GL20.GL_FLOAT_VEC3 -> "vec3";
            case GL20.GL_FLOAT_VEC4 -> "vec4";
            case GL20.GL_INT -> "int";
            case GL20.GL_INT_VEC2 -> "ivec2";
            case GL20.GL_INT_VEC3 -> "ivec3";
            case GL20.GL_INT_VEC4 -> "ivec4";
            case GL20.GL_BOOL -> "bool";
            case GL20.GL_BOOL_VEC2 -> "bvec2";
            case GL20.GL_BOOL_VEC3 -> "bvec3";
            case GL20.GL_BOOL_VEC4 -> "bvec4";
            case GL20.GL_FLOAT_MAT2 -> "mat2";
            case GL20.GL_FLOAT_MAT3 -> "mat3";
            case GL20.GL_FLOAT_MAT4 -> "mat4";
            case GL20.GL_SAMPLER_2D -> "sampler2D";
            case GL20.GL_SAMPLER_CUBE -> "samplerCube";
            default -> "0x" + Integer.toHexString(type);
        };
    }
}
