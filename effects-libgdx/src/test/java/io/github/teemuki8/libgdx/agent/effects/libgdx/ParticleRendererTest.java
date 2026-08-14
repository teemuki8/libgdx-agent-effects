package io.github.teemuki8.libgdx.agent.effects.libgdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import io.github.teemuki8.libgdx.agent.effects.core.BlendMode;
import io.github.teemuki8.libgdx.agent.effects.core.ColorGradient;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.FloatCurve;
import io.github.teemuki8.libgdx.agent.effects.core.Material2dDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleCapacityPolicy;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleSnapshot;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderSource;
import java.util.List;
import org.junit.jupiter.api.Test;

class ParticleRendererTest {

    @Test
    void rendersSpriteQuadsAndPointSpritesUnderRealGl() throws Exception {
        GdxTestHost.run(() -> {
            FrameBuffer target = new FrameBuffer(Pixmap.Format.RGBA8888, 32, 32, false);
            try (ParticleRenderer quads = new ParticleRenderer(definition("quads", quadShader()),
                    EffectsLimits.developmentDefaults(), ParticleRenderMode.SPRITE_QUADS);
                    ParticleRenderer points = new ParticleRenderer(
                            definition("points", pointShader()),
                            EffectsLimits.developmentDefaults(), ParticleRenderMode.POINT_SPRITES)) {
                target.begin();
                Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                quads.render(snapshot("quads", -0.4f), key -> null);
                points.render(snapshot("points", 0.4f), key -> null);
                byte[] quadPixel = com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixels(
                        10, 16, 1, 1, false);
                byte[] pointPixel = com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixels(
                        22, 16, 1, 1, false);
                assertTrue((quadPixel[0] & 0xff) > 200);
                assertTrue((pointPixel[0] & 0xff) > 200);
                assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
                target.end();
            } finally {
                target.dispose();
            }
        });
    }

    private static ParticleDefinition definition(String name, ShaderSource shader) {
        Material2dDefinition material = new Material2dDefinition(name + "-material", shader,
                BlendMode.ADDITIVE, List.of(), List.of());
        return new ParticleDefinition(name, "emitter", material, 4, 0f, 1f, 0f,
                new FloatCurve(List.of(new FloatCurve.Stop(0f, 0.3f))),
                new ColorGradient(List.of(new ColorGradient.Stop(0f, 1f, 0f, 0f, 1f))),
                List.of(), ParticleCapacityPolicy.DROP_NEWEST);
    }

    private static ParticleSnapshot snapshot(String name, float x) {
        float size = name.equals("points") ? 8f : 0.3f;
        return new ParticleSnapshot(name, List.of(new ParticleSnapshot.Particle(0L,
                x, 0f, 0f, 0f, 0f, 0f, 0f, 1f, size,
                1f, 0f, 0f, 1f)), 0L, 0L);
    }

    private static ShaderSource quadShader() {
        return new ShaderSource("""
                attribute vec2 a_position;
                attribute vec4 a_color;
                varying vec4 v_color;
                void main(){ v_color=a_color; gl_Position=vec4(a_position,0.0,1.0); }
                """, fragment());
    }

    private static ShaderSource pointShader() {
        return new ShaderSource("""
                attribute vec2 a_position;
                attribute vec4 a_color;
                attribute float a_size;
                varying vec4 v_color;
                void main(){ v_color=a_color; gl_PointSize=a_size;\
                    gl_Position=vec4(a_position,0.0,1.0); }
                """, fragment());
    }

    private static String fragment() {
        return "varying vec4 v_color; void main(){ gl_FragColor=v_color; }";
    }
}
