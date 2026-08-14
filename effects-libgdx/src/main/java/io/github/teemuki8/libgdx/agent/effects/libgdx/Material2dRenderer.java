package io.github.teemuki8.libgdx.agent.effects.libgdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.BufferUtils;
import io.github.teemuki8.libgdx.agent.effects.core.AssetKey;
import io.github.teemuki8.libgdx.agent.effects.core.BlendMode;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.Material2dDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.UniformBinding;
import io.github.teemuki8.libgdx.agent.effects.core.UniformValue;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Non-owning render-thread adapter for a caller-supplied 2D mesh and registered textures. */
public final class Material2dRenderer implements AutoCloseable {
    private final EffectsLimits limits;
    private final Thread ownerThread = Thread.currentThread();
    private boolean closed;

    /** Creates a renderer confined to the current application render thread. */
    public Material2dRenderer(EffectsLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    /**
     * Compiles and draws one mesh without owning it or its textures, restoring changed GL state.
     */
    public void render(Material2dDefinition material, Mesh mesh, int primitiveType,
            RegisteredAssetResolver assets) {
        requireUsable();
        Objects.requireNonNull(material, "material").validate(limits);
        Objects.requireNonNull(mesh, "mesh");
        Objects.requireNonNull(assets, "assets");
        ShaderProgram program = new ShaderProgram(
                material.shader().vertex(), material.shader().fragment());
        if (!program.isCompiled()) {
            String log = bounded(program.getLog());
            program.dispose();
            throw new EffectsException(EffectsException.Kind.COMPILE_FAILED, log);
        }
        GlState state = GlState.capture();
        List<TextureState> textures = new ArrayList<>();
        try {
            applyMaterialState(material.blendMode());
            program.bind();
            bindUniforms(program, material.uniforms());
            bindTextures(program, material.textures(), assets, textures);
            mesh.bind(program);
            try {
                mesh.render(program, primitiveType);
            } finally {
                mesh.unbind(program);
            }
        } finally {
            restoreTextures(textures);
            state.restore();
            program.dispose();
        }
    }

    /** Marks this adapter closed; caller meshes and textures are never disposed. */
    @Override public void close() {
        requireOwnerThread();
        closed = true;
    }

    private void bindTextures(ShaderProgram program, List<AssetKey> keys,
            RegisteredAssetResolver assets, List<TextureState> states) {
        for (int unit = 0; unit < keys.size(); unit++) {
            AssetKey key = keys.get(unit);
            Texture texture = assets.resolve(key);
            if (texture == null) {
                throw new EffectsException(EffectsException.Kind.INVALID_EFFECT,
                        "registered texture is unavailable: " + key.value());
            }
            String uniform = uniformName(key);
            Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0 + unit);
            int previous = integer(GL20.GL_TEXTURE_BINDING_2D);
            states.add(new TextureState(unit, previous));
            texture.bind(unit);
            if (program.hasUniform(uniform)) {
                program.setUniformi(uniform, unit);
            }
        }
    }

    private static void bindUniforms(ShaderProgram program, List<UniformBinding> bindings) {
        for (UniformBinding binding : bindings) {
            if (!program.hasUniform(binding.name())) {
                continue;
            }
            UniformValue value = binding.value();
            if (value instanceof UniformValue.Float item) {
                program.setUniformf(binding.name(), item.value());
            } else if (value instanceof UniformValue.Int item) {
                program.setUniformi(binding.name(), item.value());
            } else if (value instanceof UniformValue.Vec2 item) {
                program.setUniformf(binding.name(), item.x(), item.y());
            } else if (value instanceof UniformValue.Vec3 item) {
                program.setUniformf(binding.name(), item.x(), item.y(), item.z());
            } else if (value instanceof UniformValue.Vec4 item) {
                program.setUniformf(binding.name(), item.x(), item.y(), item.z(), item.w());
            } else if (value instanceof UniformValue.Mat4 item) {
                program.setUniformMatrix(binding.name(),
                        new com.badlogic.gdx.math.Matrix4(item.values()));
            } else if (value instanceof UniformValue.Sampler2d) {
                throw new EffectsException(EffectsException.Kind.INVALID_EFFECT,
                        "material renderer samplers must use registered asset keys");
            }
        }
    }

    private static void applyMaterialState(BlendMode blendMode) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glBlendEquationSeparate(GL20.GL_FUNC_ADD,
                blendMode == BlendMode.SUBTRACT ? GL20.GL_FUNC_REVERSE_SUBTRACT : GL20.GL_FUNC_ADD);
        int source = blendMode == BlendMode.MULTIPLY ? GL20.GL_DST_COLOR : GL20.GL_SRC_ALPHA;
        int destination = switch (blendMode) {
            case ADDITIVE -> GL20.GL_ONE;
            case MULTIPLY, NORMAL, SUBTRACT -> GL20.GL_ONE_MINUS_SRC_ALPHA;
        };
        Gdx.gl.glBlendFuncSeparate(source, destination, GL20.GL_ONE,
                GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    private static void restoreTextures(List<TextureState> states) {
        for (TextureState state : states) {
            Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0 + state.unit());
            Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, state.binding());
        }
    }

    private static String uniformName(AssetKey key) {
        if (!key.value().matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new EffectsException(EffectsException.Kind.INVALID_EFFECT,
                    "texture asset key cannot map to a shader uniform");
        }
        return "u_" + key.value();
    }

    private String bounded(String log) {
        return log.length() <= limits.maxDiagnosticChars()
                ? log : log.substring(0, limits.maxDiagnosticChars());
    }

    private void requireUsable() {
        requireOwnerThread();
        if (closed) {
            throw new EffectsException(EffectsException.Kind.INVALID_LIFECYCLE,
                    "material renderer is closed");
        }
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new EffectsException(EffectsException.Kind.WRONG_THREAD,
                    "Material2dRenderer must be used on its owning render thread");
        }
    }

    private static int integer(int name) {
        IntBuffer value = BufferUtils.newIntBuffer(1);
        Gdx.gl.glGetIntegerv(name, value);
        return value.get(0);
    }

    private record TextureState(int unit, int binding) {}

    private record GlState(
            int program,
            int activeTexture,
            boolean blend,
            boolean depth,
            boolean cull,
            int sourceRgb,
            int destinationRgb,
            int sourceAlpha,
            int destinationAlpha,
            int equationRgb,
            int equationAlpha) {

        static GlState capture() {
            return new GlState(integer(GL20.GL_CURRENT_PROGRAM), integer(GL20.GL_ACTIVE_TEXTURE),
                    Gdx.gl.glIsEnabled(GL20.GL_BLEND),
                    Gdx.gl.glIsEnabled(GL20.GL_DEPTH_TEST),
                    Gdx.gl.glIsEnabled(GL20.GL_CULL_FACE),
                    integer(GL20.GL_BLEND_SRC_RGB), integer(GL20.GL_BLEND_DST_RGB),
                    integer(GL20.GL_BLEND_SRC_ALPHA), integer(GL20.GL_BLEND_DST_ALPHA),
                    integer(GL20.GL_BLEND_EQUATION_RGB), integer(GL20.GL_BLEND_EQUATION_ALPHA));
        }

        void restore() {
            enabled(GL20.GL_BLEND, blend);
            enabled(GL20.GL_DEPTH_TEST, depth);
            enabled(GL20.GL_CULL_FACE, cull);
            Gdx.gl.glBlendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
            Gdx.gl.glBlendEquationSeparate(equationRgb, equationAlpha);
            Gdx.gl.glUseProgram(program);
            Gdx.gl.glActiveTexture(activeTexture);
        }

        private static void enabled(int capability, boolean enabled) {
            if (enabled) {
                Gdx.gl.glEnable(capability);
            } else {
                Gdx.gl.glDisable(capability);
            }
        }
    }
}
