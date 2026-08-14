package io.github.teemuki8.libgdx.agent.effects.libgdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import io.github.teemuki8.libgdx.agent.effects.core.BlendMode;
import io.github.teemuki8.libgdx.agent.effects.core.ColorGradient;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.FloatCurve;
import io.github.teemuki8.libgdx.agent.effects.core.Material2dDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderSource;
import io.github.teemuki8.libgdx.agent.effects.core.TrailCap;
import io.github.teemuki8.libgdx.agent.effects.core.TrailDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.TrailJoin;
import io.github.teemuki8.libgdx.agent.effects.core.TrailSnapshot;
import io.github.teemuki8.libgdx.agent.effects.core.TrailUvMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrailRendererTest {
    private static final ShaderSource SOURCE = new ShaderSource("""
            attribute vec2 a_position;
            attribute vec4 a_color;
            attribute vec2 a_texCoord0;
            varying vec4 v_color;
            void main(){ v_color=a_color; gl_Position=vec4(a_position,0.0,1.0); }
            """, """
            varying vec4 v_color;
            void main(){ gl_FragColor=v_color; }
            """);

    @Test
    void rendersBoundedRibbonPixelsAndRestoresGlState() throws Exception {
        GdxTestHost.run(() -> {
            TrailDefinition definition = definition();
            FrameBuffer target = new FrameBuffer(Pixmap.Format.RGBA8888, 32, 32, false);
            try (TrailRenderer renderer = new TrailRenderer(definition,
                    EffectsLimits.developmentDefaults())) {
                target.begin();
                Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                Gdx.gl.glDisable(GL20.GL_BLEND);
                Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
                renderer.render(snapshot(), key -> null);
                byte[] pixel = com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixels(
                        16, 16, 1, 1, false);
                assertTrue((pixel[0] & 0xff) > 200);
                assertEquals(0, pixel[1] & 0xff);
                assertEquals(false, Gdx.gl.glIsEnabled(GL20.GL_BLEND));
                assertEquals(true, Gdx.gl.glIsEnabled(GL20.GL_DEPTH_TEST));
                assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
                target.end();
            } finally {
                target.dispose();
            }
        });
    }

    @Test
    void rejectsMismatchedAndOversizedSnapshotsAndClosedUse() throws Exception {
        GdxTestHost.run(() -> {
            TrailRenderer renderer = new TrailRenderer(definition(),
                    EffectsLimits.developmentDefaults());
            assertKind(EffectsException.Kind.INVALID_EFFECT,
                    () -> renderer.render(new TrailSnapshot("other", List.of(), 0), key -> null));
            TrailSnapshot oversized = new TrailSnapshot("ribbon", List.of(
                    point(-1f, 0f), point(-0.5f, 0f), point(0f, 0f),
                    point(0.5f, 0f), point(1f, 0f)), 0);
            assertKind(EffectsException.Kind.LIMIT_EXCEEDED,
                    () -> renderer.render(oversized, key -> null));
            renderer.close();
            assertKind(EffectsException.Kind.INVALID_LIFECYCLE,
                    () -> renderer.render(snapshot(), key -> null));
        });
    }

    private static TrailDefinition definition() {
        Material2dDefinition material = new Material2dDefinition("ribbon-material", SOURCE,
                BlendMode.NORMAL, List.of(), List.of());
        return new TrailDefinition("ribbon", "ship", material,
                new FloatCurve(List.of(new FloatCurve.Stop(0f, 0.4f))),
                new ColorGradient(List.of(new ColorGradient.Stop(0f, 1f, 0f, 0f, 1f))),
                0.1f, 0.01f, 4, 1f, TrailJoin.MITER, TrailCap.BUTT,
                TrailUvMode.STRETCH, 2f);
    }

    private static TrailSnapshot snapshot() {
        return new TrailSnapshot("ribbon", List.of(
                point(-0.8f, 0f), point(0f, 0f), point(0f, 0.01f),
                point(0.8f, 0f)), 0);
    }

    private static TrailSnapshot.Point point(float x, float y) {
        return new TrailSnapshot.Point(x, y, 0f, 0f, 0.4f,
                1f, 0f, 0f, 1f, (x + 0.8f) / 1.6f);
    }

    private static void assertKind(EffectsException.Kind kind,
            org.junit.jupiter.api.function.Executable executable) {
        EffectsException failure = assertThrows(EffectsException.class, executable);
        assertEquals(kind, failure.kind());
    }
}
