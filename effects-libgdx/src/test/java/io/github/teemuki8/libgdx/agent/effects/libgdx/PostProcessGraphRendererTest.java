package io.github.teemuki8.libgdx.agent.effects.libgdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import io.github.teemuki8.libgdx.agent.effects.core.AssetKey;
import io.github.teemuki8.libgdx.agent.effects.core.BlendMode;
import io.github.teemuki8.libgdx.agent.effects.core.DistortionFieldDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.Material2dDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.PostProcessGraphDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.RenderPassDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderSource;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PostProcessGraphRendererTest {
    private static final String VERTEX = """
            attribute vec2 a_position;
            attribute vec2 a_texCoord0;
            varying vec2 v_uv;
            void main(){v_uv=a_texCoord0;gl_Position=vec4(a_position,0.0,1.0);}
            """;

    @Test
    void rendersStableMultiInputGraphAndReusesThenEvictsPool() throws Exception {
        GdxTestHost.run(() -> {
            Texture scene = texture(0xff0000ff);
            Texture overlay = texture(0x0000ffff);
            int sceneHandle = scene.getTextureObjectHandle();
            Gdx.gl.glClearColor(0.17f, 0.23f, 0.31f, 0.47f);
            float[] clearColor = clearColor();
            try (PostProcessGraphRenderer renderer = new PostProcessGraphRenderer(graph(),
                    EffectsLimits.developmentDefaults())) {
                Map<String, SceneCapture> captures = Map.of(
                        "scene", capture(scene), "overlay", capture(overlay));
                PostProcessGraphResult first = renderer.render(captures, 16, 16);
                byte[] pixel = read(first, 8, 8);
                assertTrue((pixel[0] & 0xff) > 200);
                assertTrue((pixel[2] & 0xff) > 200);
                assertEquals(List.of("copy", "combine"),
                        first.evidence().executionOrder());
                assertEquals(2, first.evidence().allocatedFramebuffers());
                assertEquals(0L, first.evidence().evictedFramebuffers());

                PostProcessGraphResult reused = renderer.render(captures, 16, 16);
                assertEquals(first.output().getTextureObjectHandle(),
                        reused.output().getTextureObjectHandle());
                PostProcessGraphResult resized = renderer.render(captures, 8, 8);
                assertNotEquals(reused.output().getTextureObjectHandle(),
                        resized.output().getTextureObjectHandle());
                assertEquals(2L, resized.evidence().evictedFramebuffers());
                assertEquals(sceneHandle, scene.getTextureObjectHandle());
                assertThrows(EffectsException.class,
                        () -> renderer.render(Map.of("scene", capture(scene)), 8, 8));
                assertEquals(List.of(clearColor[0], clearColor[1], clearColor[2], clearColor[3]),
                        floats(clearColor()));
            } finally {
                scene.dispose();
                overlay.dispose();
            }
        });
    }

    @Test
    void distortionRendererComposesApplicationOwnedCaptures() throws Exception {
        GdxTestHost.run(() -> {
            Texture scene = texture(0x00ff00ff);
            Texture vectors = texture(0x000000ff);
            try (DistortionRenderer renderer = new DistortionRenderer(distortion(),
                    EffectsLimits.developmentDefaults())) {
                PostProcessGraphResult result = renderer.render(capture(scene), capture(vectors),
                        8, 8);
                byte[] pixel = read(result, 4, 4);
                assertTrue((pixel[1] & 0xff) > 200);
            } finally {
                scene.dispose();
                vectors.dispose();
            }
        });
    }

    private static PostProcessGraphDefinition graph() {
        RenderPassDefinition copy = new RenderPassDefinition("copy",
                material("copy", List.of("scene"),
                        "gl_FragColor=texture2D(u_scene,v_uv);"),
                List.of("scene"), "copied");
        RenderPassDefinition combine = new RenderPassDefinition("combine",
                material("combine", List.of("copied", "overlay"),
                        "gl_FragColor=texture2D(u_copied,v_uv)+texture2D(u_overlay,v_uv);"),
                List.of("copied", "overlay"), "final");
        return new PostProcessGraphDefinition("multi", List.of("scene", "overlay"),
                List.of(combine, copy), "final", 2);
    }

    private static DistortionFieldDefinition distortion() {
        return new DistortionFieldDefinition("distort",
                material("distort", List.of("scene", "vectors"),
                        "gl_FragColor=texture2D(u_scene,v_uv);"),
                "scene", "vectors", "distorted");
    }

    private static Material2dDefinition material(String name, List<String> inputs,
            String body) {
        String uniforms = inputs.stream().map(input -> "uniform sampler2D u_" + input + ";")
                .reduce("", String::concat);
        return new Material2dDefinition(name, new ShaderSource(VERTEX,
                "varying vec2 v_uv;" + uniforms + "void main(){" + body + "}"),
                BlendMode.NORMAL, List.of(), inputs.stream().map(AssetKey::new).toList());
    }

    private static SceneCapture capture(Texture texture) {
        return new SceneCapture() {
            @Override public Texture colorTexture() {
                return texture;
            }

            @Override public int width() {
                return texture.getWidth();
            }

            @Override public int height() {
                return texture.getHeight();
            }
        };
    }

    private static Texture texture(int rgba) {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        try {
            pixmap.drawPixel(0, 0, rgba);
            return new Texture(pixmap);
        } finally {
            pixmap.dispose();
        }
    }

    private static byte[] read(PostProcessGraphResult result, int x, int y) {
        result.framebuffer().begin();
        try {
            return com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixels(x, y, 1, 1, false);
        } finally {
            result.framebuffer().end();
        }
    }

    private static float[] clearColor() {
        java.nio.FloatBuffer values = com.badlogic.gdx.utils.BufferUtils.newFloatBuffer(4);
        Gdx.gl.glGetFloatv(GL20.GL_COLOR_CLEAR_VALUE, values);
        return new float[] {values.get(0), values.get(1), values.get(2), values.get(3)};
    }

    private static List<Float> floats(float[] values) {
        return List.of(values[0], values[1], values[2], values[3]);
    }
}
