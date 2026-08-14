package io.github.teemuki8.libgdx.agent.effects.libgdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Renderable;
import io.github.teemuki8.libgdx.agent.effects.core.BlendMode;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.Material3dDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderSource;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class Material3dShaderProviderTest {
    @Test
    void createsShaderOnlyForRenderableWithDeclaredMaterial() throws Exception {
        GdxTestHost.run(() -> {
            Mesh mesh = triangle();
            try (Material3dShaderProvider provider = new Material3dShaderProvider(
                    EffectsLimits.developmentDefaults(), key -> null)) {
                Renderable renderable = renderable(mesh, material());
                com.badlogic.gdx.graphics.g3d.Shader shader = provider.getShader(renderable);
                assertTrue(shader.canRender(renderable));
                assertEquals(shader, provider.getShader(renderable));
                assertThrows(EffectsException.class,
                        () -> provider.getShader(renderable(mesh, null)));
            } finally {
                mesh.dispose();
            }
        });
    }

    @Test
    void rejectsCompileFailureAndWrongThread() throws Exception {
        GdxTestHost.run(() -> {
            Mesh mesh = triangle();
            try (Material3dShaderProvider provider = new Material3dShaderProvider(
                    EffectsLimits.developmentDefaults(), key -> null)) {
                Material3dDefinition broken = new Material3dDefinition("broken",
                        new ShaderSource("void main(){}", "void main(){ invalid glsl }"),
                        BlendMode.NORMAL, true, true, true, List.of(), List.of());
                EffectsException compile = assertThrows(EffectsException.class,
                        () -> provider.getShader(renderable(mesh, broken)));
                assertEquals(EffectsException.Kind.COMPILE_FAILED, compile.kind());

                AtomicReference<Throwable> failure = new AtomicReference<>();
                Thread other = new Thread(() -> {
                    try {
                        provider.getShader(renderable(mesh, material()));
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

    private static Material3dDefinition material() {
        return new Material3dDefinition("mesh", new ShaderSource("""
                attribute vec3 a_position;
                uniform mat4 u_projViewTrans;
                uniform mat4 u_worldTrans;
                void main(){gl_Position=u_projViewTrans*u_worldTrans*vec4(a_position,1.0);}
                """, "void main(){gl_FragColor=vec4(1.0);}"),
                BlendMode.NORMAL, true, true, true, List.of(), List.of());
    }

    private static Mesh triangle() {
        Mesh mesh = new Mesh(true, 3, 0,
                new VertexAttribute(Usage.Position, 3, "a_position"));
        mesh.setVertices(new float[] {-1, -1, 0, 1, -1, 0, 0, 1, 0});
        return mesh;
    }

    private static Renderable renderable(Mesh mesh, Material3dDefinition material) {
        Renderable renderable = new Renderable();
        renderable.meshPart.set("triangle", mesh, 0, 3, GL20.GL_TRIANGLES);
        renderable.userData = material;
        return renderable;
    }
}
