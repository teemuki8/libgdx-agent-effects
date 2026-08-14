package io.github.teemuki8.libgdx.agent.effects.libgdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import io.github.teemuki8.libgdx.agent.effects.core.BeamDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.BeamSnapshot;
import io.github.teemuki8.libgdx.agent.effects.core.BlendMode;
import io.github.teemuki8.libgdx.agent.effects.core.ColorGradient;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.FloatCurve;
import io.github.teemuki8.libgdx.agent.effects.core.LightningDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.LightningSnapshot;
import io.github.teemuki8.libgdx.agent.effects.core.Material2dDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderSource;
import java.util.List;
import org.junit.jupiter.api.Test;

class BeamRendererTest {
    private static final ShaderSource SOURCE = new ShaderSource("""
            attribute vec2 a_position;
            attribute vec4 a_color;
            varying vec4 v_color;
            void main(){ v_color=a_color; gl_Position=vec4(a_position,0.0,1.0); }
            """, """
            varying vec4 v_color;
            void main(){ gl_FragColor=v_color; }
            """);

    @Test
    void rendersBeamAndLightningQuadsUnderRealGl() throws Exception {
        GdxTestHost.run(() -> {
            FrameBuffer target = new FrameBuffer(Pixmap.Format.RGBA8888, 32, 32, false);
            try (BeamRenderer beam = new BeamRenderer(beamDefinition(),
                    EffectsLimits.developmentDefaults());
                    BeamRenderer lightning = new BeamRenderer(lightningDefinition(),
                            EffectsLimits.developmentDefaults())) {
                target.begin();
                Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                beam.render(beamSnapshot(), key -> null);
                lightning.render(lightningSnapshot(), key -> null);
                byte[] center = com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixels(
                        16, 16, 1, 1, false);
                assertTrue((center[2] & 0xff) > 100);
                assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
                target.end();
            } finally {
                target.dispose();
            }
        });
    }

    private static BeamDefinition beamDefinition() {
        return new BeamDefinition("beam", "a", "b", material(), curve(), gradient(), 2, 1f);
    }

    private static LightningDefinition lightningDefinition() {
        return new LightningDefinition("lightning", "a", "b", material(), curve(), gradient(),
                2, 1, 0.2f, 0.2f, 1f);
    }

    private static BeamSnapshot beamSnapshot() {
        return new BeamSnapshot("beam", List.of(
                new BeamSnapshot.Segment(-0.8f, 0f, 0f, 0.8f, 0f, 0f,
                        0.2f, 0f, 0f, 1f, 1f)), 0f);
    }

    private static LightningSnapshot lightningSnapshot() {
        return new LightningSnapshot("lightning", List.of(
                new LightningSnapshot.Segment(-0.8f, -0.4f, 0f, 0f, 0.4f, 0f,
                        0.1f, 0f, 0f, 1f, 1f, false),
                new LightningSnapshot.Segment(0f, 0.4f, 0f, 0.8f, -0.4f, 0f,
                        0.1f, 0f, 0f, 1f, 1f, false)), 0, 0f);
    }

    private static Material2dDefinition material() {
        return new Material2dDefinition("lines", SOURCE, BlendMode.ADDITIVE,
                List.of(), List.of());
    }

    private static FloatCurve curve() {
        return new FloatCurve(List.of(new FloatCurve.Stop(0f, 0.1f)));
    }

    private static ColorGradient gradient() {
        return new ColorGradient(List.of(new ColorGradient.Stop(0f, 0f, 0f, 1f, 1f)));
    }
}
