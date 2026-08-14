package io.github.teemuki8.libgdx.agent.effects.libgdx;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.utils.BaseShaderProvider;
import com.badlogic.gdx.graphics.g3d.utils.RenderContext;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import io.github.teemuki8.libgdx.agent.effects.core.AssetKey;
import io.github.teemuki8.libgdx.agent.effects.core.BlendMode;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.Material3dDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.UniformBinding;
import io.github.teemuki8.libgdx.agent.effects.core.UniformValue;
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
        private final Material3dDefinition definition;
        private final RegisteredAssetResolver assets;
        private final ShaderProgram program;
        private RenderContext context;

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
            Objects.requireNonNull(camera, "camera");
            context = Objects.requireNonNull(renderContext, "renderContext");
            program.bind();
            if (program.hasUniform("u_projViewTrans")) {
                program.setUniformMatrix("u_projViewTrans", camera.combined);
            }
            context.setDepthTest(definition.depthTest() ? GL20.GL_LEQUAL : 0);
            context.setDepthMask(definition.depthWrite());
            context.setCullFace(definition.cullBackFaces() ? GL20.GL_BACK : 0);
            int destination = definition.blendMode() == BlendMode.ADDITIVE
                    ? GL20.GL_ONE : GL20.GL_ONE_MINUS_SRC_ALPHA;
            context.setBlending(true, GL20.GL_SRC_ALPHA, destination);
        }

        @Override public void render(Renderable renderable) {
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
            context = null;
        }

        @Override public void dispose() {
            program.dispose();
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
            for (AssetKey key : definition.textures()) {
                Texture texture = assets.resolve(key);
                if (texture == null) {
                    throw new EffectsException(EffectsException.Kind.INVALID_EFFECT,
                            "registered texture is unavailable: " + key.value());
                }
                String uniform = "u_" + key.value();
                if (program.hasUniform(uniform)) {
                    program.setUniformi(uniform, context.textureBinder.bind(texture));
                }
            }
        }
    }
}
