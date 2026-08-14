package io.github.teemuki8.libgdx.agent.effects.libgdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.g3d.utils.DefaultTextureBinder;
import com.badlogic.gdx.graphics.g3d.utils.RenderContext;
import io.github.teemuki8.libgdx.agent.effects.core.BlendMode;
import io.github.teemuki8.libgdx.agent.effects.core.DecalDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.DecalSnapshot;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.Material2dDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.Material3dDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderSource;
import java.util.List;
import org.junit.jupiter.api.Test;

class DecalRendererTest {

    @Test
    void renders2dAnd3dDecalsUnderRealGl() throws Exception {
        GdxTestHost.run(() -> {
            FrameBuffer target = new FrameBuffer(Pixmap.Format.RGBA8888, 32, 32, true);
            try (Decal2dRenderer renderer2d = new Decal2dRenderer(definition2d(),
                    EffectsLimits.developmentDefaults());
                    Decal3dRenderer renderer3d = new Decal3dRenderer(definition3d(),
                            EffectsLimits.developmentDefaults(), key -> null)) {
                target.begin();
                Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
                renderer2d.render(snapshot("decal-2d", -0.4f), key -> null);

                OrthographicCamera camera = new OrthographicCamera(2f, 2f);
                camera.position.set(0f, 0f, 2f);
                camera.lookAt(0f, 0f, 0f);
                camera.update();
                RenderContext context = new RenderContext(
                        new DefaultTextureBinder(DefaultTextureBinder.LRU, 1));
                context.begin();
                renderer3d.render(snapshot("decal-3d", 0.4f), camera, context);
                context.end();

                byte[] left = com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixels(
                        10, 16, 1, 1, false);
                byte[] right = com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixels(
                        22, 16, 1, 1, false);
                assertTrue((left[0] & 0xff) > 200);
                assertTrue((right[2] & 0xff) > 200);
                assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
                target.end();
            } finally {
                target.dispose();
            }
        });
    }

    private static DecalDefinition definition2d() {
        Material2dDefinition material = new Material2dDefinition("decal-2d-material",
                new ShaderSource("""
                        attribute vec2 a_position;
                        attribute vec4 a_color;
                        varying vec4 v_color;
                        void main(){v_color=a_color;gl_Position=vec4(a_position,0.0,1.0);}
                        """, fragment()), BlendMode.NORMAL, List.of(), List.of());
        return new DecalDefinition("decal-2d", material, 4, 1f, 0f, 0.3f, 0.3f);
    }

    private static DecalDefinition definition3d() {
        Material3dDefinition material = new Material3dDefinition("decal-3d-material",
                new ShaderSource("""
                        attribute vec3 a_position;
                        attribute vec4 a_color;
                        uniform mat4 u_projViewTrans;
                        uniform mat4 u_worldTrans;
                        varying vec4 v_color;
                        void main(){v_color=a_color;gl_Position=u_projViewTrans*u_worldTrans
                            *vec4(a_position,1.0);}
                        """, fragment()), BlendMode.NORMAL, true, false, false,
                List.of(), List.of());
        return new DecalDefinition("decal-3d", material, 4, 1f, 0f, 0.3f, 0.3f);
    }

    private static DecalSnapshot snapshot(String name, float x) {
        float red = name.equals("decal-2d") ? 1f : 0f;
        float blue = name.equals("decal-3d") ? 1f : 0f;
        return new DecalSnapshot(name, List.of(new DecalSnapshot.Decal(0L, 0L,
                x, 0f, 0f, 0f, 0f, 1f, 0f, 0.3f, 0.3f, 0f,
                red, 0f, blue, 1f)), 0L);
    }

    private static String fragment() {
        return "varying vec4 v_color; void main(){gl_FragColor=v_color;}";
    }
}
