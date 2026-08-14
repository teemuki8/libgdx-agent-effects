package io.github.teemuki8.libgdx.agent.effects.libgdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.utils.BaseShaderProvider;
import com.badlogic.gdx.graphics.g3d.utils.RenderContext;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.BufferUtils;
import io.github.teemuki8.libgdx.agent.effects.core.AssetKey;
import io.github.teemuki8.libgdx.agent.effects.core.BlendMode;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.Material3dDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.UniformBinding;
import io.github.teemuki8.libgdx.agent.effects.core.UniformValue;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Non-owning render-thread shader provider for renderables carrying a 3D material definition. */
public final class Material3dShaderProvider extends BaseShaderProvider implements AutoCloseable {
    private final EffectsLimits limits;
    private final RegisteredAssetResolver assets;
    private final Thread ownerThread = Thread.currentThread();
    private boolean closed;

    /** Creates a provider that owns generated programs but never caller meshes or textures. */
    public Material3dShaderProvider(EffectsLimits limits, RegisteredAssetResolver assets) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.assets = Objects.requireNonNull(assets, "assets");
    }

    /** Resolves or creates the shader for the renderable's declared material. */
    @Override public Shader getShader(Renderable renderable) {
        requireUsable();
        requireDefinition(renderable);
        return super.getShader(renderable);
    }

    @Override protected Shader createShader(Renderable renderable) {
        Material3dDefinition definition = requireDefinition(renderable);
        definition.validate(limits);
        return new MaterialShader(definition, assets, limits.maxDiagnosticChars());
    }

    /** Disposes provider-owned programs only. */
    @Override public void dispose() {
        requireOwnerThread();
        if (!closed) {
            closed = true;
            super.dispose();
        }
    }

    /** Equivalent to {@link #dispose()}. */
    @Override public void close() {
        dispose();
    }

    private Material3dDefinition requireDefinition(Renderable renderable) {
        Objects.requireNonNull(renderable, "renderable");
        if (!(renderable.userData instanceof Material3dDefinition definition)) {
            throw new EffectsException(EffectsException.Kind.INVALID_EFFECT,
                    "renderable userData must be a Material3dDefinition");
        }
        return definition;
    }

    private void requireUsable() {
        requireOwnerThread();
        if (closed) {
            throw new EffectsException(EffectsException.Kind.INVALID_LIFECYCLE,
                    "material shader provider is closed");
        }
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new EffectsException(EffectsException.Kind.WRONG_THREAD,
                    "Material3dShaderProvider must be used on its owning render thread");
        }
    }

    private static final class MaterialShader implements Shader {
        private final Thread ownerThread = Thread.currentThread();
        private final Material3dDefinition definition;
        private final RegisteredAssetResolver assets;
        private final ShaderProgram program;
        private final List<TextureState> textureStates = new ArrayList<>();
        private RenderContext context;
        private GlState hostState;

        MaterialShader(Material3dDefinition definition, RegisteredAssetResolver assets,
                int maxDiagnosticChars) {
            this.definition = definition;
            this.assets = assets;
            program = new ShaderProgram(definition.shader().vertex(), definition.shader().fragment());
            if (!program.isCompiled()) {
                String log = program.getLog();
                String bounded = log.length() <= maxDiagnosticChars
                        ? log : log.substring(0, maxDiagnosticChars);
                program.dispose();
                throw new EffectsException(EffectsException.Kind.COMPILE_FAILED, bounded);
            }
        }

        @Override public void init() {
            // Program compilation is completed in the constructor so failure is synchronous.
        }

        @Override public int compareTo(Shader other) {
            return other == this ? 0 : definition.name().compareTo(
                    other instanceof MaterialShader material
                            ? material.definition.name() : other.getClass().getName());
        }

        @Override public boolean canRender(Renderable renderable) {
            return renderable.userData instanceof Material3dDefinition material
                    && material.equals(definition);
        }

        @Override public void begin(Camera camera, RenderContext renderContext) {
            requireOwnerThread();
            Objects.requireNonNull(camera, "camera");
            if (context != null) {
                throw new EffectsException(EffectsException.Kind.INVALID_LIFECYCLE,
                        "shader is already active");
            }
            hostState = GlState.capture();
            context = Objects.requireNonNull(renderContext, "renderContext");
            try {
                applyMaterialState();
                program.bind();
                if (program.hasUniform("u_projViewTrans")) {
                    program.setUniformMatrix("u_projViewTrans", camera.combined);
                }
            } catch (RuntimeException | Error failure) {
                context = null;
                hostState.restore();
                hostState = null;
                throw failure;
            }
        }

        @Override public void render(Renderable renderable) {
            requireOwnerThread();
            if (context == null || !canRender(renderable)) {
                throw new EffectsException(EffectsException.Kind.INVALID_LIFECYCLE,
                        "shader must begin before rendering a compatible material");
            }
            if (program.hasUniform("u_worldTrans")) {
                program.setUniformMatrix("u_worldTrans", renderable.worldTransform);
            }
            bindUniforms();
            bindTextures();
            renderable.meshPart.render(program);
        }

        @Override public void end() {
            requireOwnerThread();
            if (context == null) {
                throw new EffectsException(EffectsException.Kind.INVALID_LIFECYCLE,
                        "shader is not active");
            }
            try {
                restoreTextures();
                hostState.restore();
            } finally {
                textureStates.clear();
                hostState = null;
                context = null;
            }
        }

        @Override public void dispose() {
            requireOwnerThread();
            if (context != null) {
                throw new EffectsException(EffectsException.Kind.INVALID_LIFECYCLE,
                        "active shader must end before disposal");
            }
            program.dispose();
        }

        private void applyMaterialState() {
            enabled(GL20.GL_DEPTH_TEST, definition.depthTest());
            if (definition.depthTest()) {
                Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
            }
            Gdx.gl.glDepthMask(definition.depthWrite());
            enabled(GL20.GL_CULL_FACE, definition.cullBackFaces());
            if (definition.cullBackFaces()) {
                Gdx.gl.glCullFace(GL20.GL_BACK);
            }
            Gdx.gl.glEnable(GL20.GL_BLEND);
            int destination = definition.blendMode() == BlendMode.ADDITIVE
                    ? GL20.GL_ONE : GL20.GL_ONE_MINUS_SRC_ALPHA;
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, destination);
        }

        private void bindUniforms() {
            for (UniformBinding binding : definition.uniforms()) {
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
                }
            }
        }

        private void bindTextures() {
            for (int unit = 0; unit < definition.textures().size(); unit++) {
                AssetKey key = definition.textures().get(unit);
                Texture texture = assets.resolve(key);
                if (texture == null) {
                    throw new EffectsException(EffectsException.Kind.INVALID_EFFECT,
                            "registered texture is unavailable: " + key.value());
                }
                String uniform = "u_" + key.value();
                Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0 + unit);
                textureStates.add(new TextureState(unit, integer(GL20.GL_TEXTURE_BINDING_2D)));
                texture.bind(unit);
                if (program.hasUniform(uniform)) {
                    program.setUniformi(uniform, unit);
                }
            }
        }

        private void restoreTextures() {
            for (int index = textureStates.size() - 1; index >= 0; index--) {
                TextureState state = textureStates.get(index);
                Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0 + state.unit());
                Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, state.binding());
            }
        }

        private void requireOwnerThread() {
            if (Thread.currentThread() != ownerThread) {
                throw new EffectsException(EffectsException.Kind.WRONG_THREAD,
                        "material Shader must be used on its owning render thread");
            }
        }

        private static int integer(int name) {
            IntBuffer value = BufferUtils.newIntBuffer(1);
            Gdx.gl.glGetIntegerv(name, value);
            return value.get(0);
        }

        private static void enabled(int capability, boolean enabled) {
            if (enabled) {
                Gdx.gl.glEnable(capability);
            } else {
                Gdx.gl.glDisable(capability);
            }
        }

        private record TextureState(int unit, int binding) {}

        private record GlState(int program, int activeTexture, boolean blend, boolean depth,
                boolean depthMask, int depthFunction, boolean cull, int cullFace,
                int sourceRgb, int destinationRgb, int sourceAlpha, int destinationAlpha,
                int equationRgb, int equationAlpha) {
            static GlState capture() {
                return new GlState(integer(GL20.GL_CURRENT_PROGRAM),
                        integer(GL20.GL_ACTIVE_TEXTURE), Gdx.gl.glIsEnabled(GL20.GL_BLEND),
                        Gdx.gl.glIsEnabled(GL20.GL_DEPTH_TEST),
                        integer(GL20.GL_DEPTH_WRITEMASK) != 0, integer(GL20.GL_DEPTH_FUNC),
                        Gdx.gl.glIsEnabled(GL20.GL_CULL_FACE), integer(GL20.GL_CULL_FACE_MODE),
                        integer(GL20.GL_BLEND_SRC_RGB), integer(GL20.GL_BLEND_DST_RGB),
                        integer(GL20.GL_BLEND_SRC_ALPHA), integer(GL20.GL_BLEND_DST_ALPHA),
                        integer(GL20.GL_BLEND_EQUATION_RGB), integer(GL20.GL_BLEND_EQUATION_ALPHA));
            }

            void restore() {
                enabled(GL20.GL_BLEND, blend);
                enabled(GL20.GL_DEPTH_TEST, depth);
                Gdx.gl.glDepthMask(depthMask);
                Gdx.gl.glDepthFunc(depthFunction);
                enabled(GL20.GL_CULL_FACE, cull);
                Gdx.gl.glCullFace(cullFace);
                Gdx.gl.glBlendFuncSeparate(sourceRgb, destinationRgb,
                        sourceAlpha, destinationAlpha);
                Gdx.gl.glBlendEquationSeparate(equationRgb, equationAlpha);
                Gdx.gl.glUseProgram(program);
                Gdx.gl.glActiveTexture(activeTexture);
            }
        }
    }
}
