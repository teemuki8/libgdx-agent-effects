package io.github.teemuki8.libgdx.agent.effects.libgdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.ScreenUtils;
import io.github.teemuki8.libgdx.agent.effects.core.EffectDescription;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.RgbaImage;
import io.github.teemuki8.libgdx.agent.effects.core.UniformBinding;
import io.github.teemuki8.libgdx.agent.effects.core.UniformValue;
import java.util.Objects;

/** Deterministic off-screen render of one effect to a bounded FrameBuffer. Render-thread confined. */
public final class PreviewRenderer implements AutoCloseable {

    private static final float[] QUAD = {
        -1f, -1f, 1f, -1f, 1f, 1f,
        -1f, -1f, 1f, 1f, -1f, 1f,
    };

    private final EffectsLimits limits;
    private final EffectCompiler compiler;
    private final Mesh fullscreenQuad;

    public PreviewRenderer(EffectsLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.compiler = new EffectCompiler(limits);
        this.fullscreenQuad = new Mesh(true, 6, 0,
            new VertexAttribute(Usage.Position, 2, "a_position"));
        fullscreenQuad.setVertices(QUAD);
    }

    public RgbaImage render(EffectDescription effect) {
        effect.validate(limits);
        CompiledEffect compiled = compiler.compile(effect);
        if (!compiled.compiled()) {
            throw new EffectsException(EffectsException.Kind.COMPILE_FAILED,
                compiled.diagnostic().infoLog());
        }
        ShaderProgram program = compiled.program();
        FrameBuffer fbo = new FrameBuffer(Pixmap.Format.RGBA8888,
            effect.renderWidth(), effect.renderHeight(), false);
        try {
            fbo.begin();
            Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            program.bind();
            bindUniforms(program, effect);
            fullscreenQuad.bind(program);
            fullscreenQuad.render(program, GL20.GL_TRIANGLES);
            fullscreenQuad.unbind(program);
            byte[] rgba = ScreenUtils.getFrameBufferPixels(0, 0,
                effect.renderWidth(), effect.renderHeight(), false);
            return toRgbaImage(rgba, effect.renderWidth(), effect.renderHeight());
        } finally {
            fbo.end();
            program.dispose();
            fbo.dispose();
        }
    }

    private void bindUniforms(ShaderProgram program, EffectDescription effect) {
        if (program.hasUniform("u_time")) {
            program.setUniformf("u_time", effect.timeSeconds());
        }
        if (program.hasUniform("u_resolution")) {
            program.setUniformf("u_resolution",
                (float) effect.renderWidth(), (float) effect.renderHeight());
        }
        for (UniformBinding binding : effect.uniforms()) {
            bindUniform(program, binding);
        }
    }

    private void bindUniform(ShaderProgram program, UniformBinding binding) {
        UniformValue value = binding.value();
        String name = binding.name();
        if (value instanceof UniformValue.Float f) {
            program.setUniformf(name, f.value());
        } else if (value instanceof UniformValue.Int i) {
            program.setUniformi(name, i.value());
        } else if (value instanceof UniformValue.Vec2 v) {
            program.setUniformf(name, v.x(), v.y());
        } else if (value instanceof UniformValue.Vec3 v) {
            program.setUniformf(name, v.x(), v.y(), v.z());
        } else if (value instanceof UniformValue.Vec4 v) {
            program.setUniformf(name, v.x(), v.y(), v.z(), v.w());
        } else if (value instanceof UniformValue.Mat4 m) {
            program.setUniformMatrix(name, new com.badlogic.gdx.math.Matrix4(m.values()));
        } else {
            throw new EffectsException(EffectsException.Kind.INVALID_EFFECT,
                "sampler2d uniforms are not supported in v0.1 preview");
        }
    }

    /** Packs raw GL RGBA bytes (bottom-up rows) into a top-left-origin RgbaImage. */
    private static RgbaImage toRgbaImage(byte[] rgba, int w, int h) {
        int[] pixels = new int[w * h];
        for (int y = 0; y < h; y++) {
            int srcRow = (h - 1 - y) * w * 4;
            for (int x = 0; x < w; x++) {
                int i = srcRow + x * 4;
                pixels[y * w + x] = ((rgba[i + 3] & 0xff) << 24) | ((rgba[i] & 0xff) << 16)
                    | ((rgba[i + 1] & 0xff) << 8) | (rgba[i + 2] & 0xff);
            }
        }
        return new RgbaImage(w, h, pixels);
    }

    @Override public void close() {
        fullscreenQuad.dispose();
    }
}
