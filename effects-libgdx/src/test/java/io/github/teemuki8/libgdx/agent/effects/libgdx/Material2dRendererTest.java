package io.github.teemuki8.libgdx.agent.effects.libgdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import io.github.teemuki8.libgdx.agent.effects.core.AssetKey;
import io.github.teemuki8.libgdx.agent.effects.core.BlendMode;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.Material2dDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderSource;
import io.github.teemuki8.libgdx.agent.effects.core.UniformBinding;
import io.github.teemuki8.libgdx.agent.effects.core.UniformValue;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class Material2dRendererTest {
    private static final ShaderSource SOURCE = new ShaderSource("""
            attribute vec2 a_position;
            attribute vec2 a_texCoord0;
            varying vec2 v_uv;
            void main(){ v_uv=a_texCoord0; gl_Position=vec4(a_position,0.0,1.0); }
            """, """
            varying vec2 v_uv;
            uniform sampler2D u_source;
            uniform vec4 u_tint;
            void main(){ gl_FragColor=texture2D(u_source,v_uv)*u_tint; }
            """);

    @Test
    void rendersCustomMeshAndPreservesApplicationTextureOwnership() throws Exception {
        GdxTestHost.run(() -> {
            Texture texture = texture(0x00ff00ff);
            int handle = texture.getTextureObjectHandle();
            Mesh mesh = quad();
            FrameBuffer target = new FrameBuffer(Pixmap.Format.RGBA8888, 8, 8, false);
            try (Material2dRenderer renderer =
                    new Material2dRenderer(EffectsLimits.developmentDefaults())) {
                target.begin();
                Gdx.gl.glClearColor(0, 0, 0, 1);
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                renderer.render(material(), mesh, GL20.GL_TRIANGLES, key -> texture);
                byte[] pixel = com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixels(4, 4, 1, 1,
                        false);
                target.end();
                assertEquals(0, pixel[0] & 0xff);
                assertEquals(255, pixel[1] & 0xff);
                assertNotEquals(0, texture.getTextureObjectHandle());
                assertEquals(handle, texture.getTextureObjectHandle());
            } finally {
                target.dispose();
                mesh.dispose();
                texture.dispose();
            }
        });
    }

    @Test
    void rejectsMissingAssetsCompileFailureAndWrongThread() throws Exception {
        GdxTestHost.run(() -> {
            Mesh mesh = quad();
            try (Material2dRenderer renderer =
                    new Material2dRenderer(EffectsLimits.developmentDefaults())) {
                EffectsException missing = assertThrows(EffectsException.class,
                        () -> renderer.render(material(), mesh, GL20.GL_TRIANGLES, key -> null));
                assertEquals(EffectsException.Kind.INVALID_EFFECT, missing.kind());

                Material2dDefinition broken = new Material2dDefinition("broken",
                        new ShaderSource("void main(){}", "void main(){ invalid glsl }"),
                        BlendMode.NORMAL, List.of(), List.of());
                EffectsException compile = assertThrows(EffectsException.class,
                        () -> renderer.render(broken, mesh, GL20.GL_TRIANGLES, key -> null));
                assertEquals(EffectsException.Kind.COMPILE_FAILED, compile.kind());

                AtomicReference<Throwable> failure = new AtomicReference<>();
                Thread other = new Thread(() -> {
                    try {
                        renderer.render(material(), mesh, GL20.GL_TRIANGLES, key -> null);
                    } catch (Throwable thrown) {
                        failure.set(thrown);
                    }
                });
                other.start();
                other.join();
                assertEquals(EffectsException.Kind.WRONG_THREAD,
                        ((EffectsException) failure.get()).kind());
            } finally {
                mesh.dispose();
            }
        });
    }

    private static Material2dDefinition material() {
        return new Material2dDefinition("green", SOURCE, BlendMode.NORMAL,
                List.of(new UniformBinding("u_tint", new UniformValue.Vec4(1, 1, 1, 1))),
                List.of(new AssetKey("source")));
    }

    private static Mesh quad() {
        Mesh mesh = new Mesh(true, 6, 0,
                new VertexAttribute(Usage.Position, 2, "a_position"),
                new VertexAttribute(Usage.TextureCoordinates, 2, "a_texCoord0"));
        mesh.setVertices(new float[] {
                -1, -1, 0, 0, 1, -1, 1, 0, 1, 1, 1, 1,
                -1, -1, 0, 0, 1, 1, 1, 1, -1, 1, 0, 1,
        });
        return mesh;
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
}
