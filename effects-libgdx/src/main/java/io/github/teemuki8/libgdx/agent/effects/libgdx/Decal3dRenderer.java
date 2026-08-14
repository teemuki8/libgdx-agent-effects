package io.github.teemuki8.libgdx.agent.effects.libgdx;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.utils.RenderContext;
import io.github.teemuki8.libgdx.agent.effects.core.DecalDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.DecalSnapshot;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.Material3dDefinition;
import java.util.Objects;

/** Non-owning camera/context 3D decal adapter with reusable renderer-owned geometry. */
public final class Decal3dRenderer implements AutoCloseable {
    private final DecalDefinition definition;
    private final Material3dDefinition material;
    private final Thread ownerThread = Thread.currentThread();
    private final Material3dShaderProvider shaderProvider;
    private final Mesh mesh;
    private final float[] vertices;
    private boolean closed;

    /** Allocates bounded geometry and provider-owned programs on the current render thread. */
    public Decal3dRenderer(DecalDefinition definition, EffectsLimits limits,
            RegisteredAssetResolver assets) {
        this.definition = Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(limits, "limits");
        definition.validate(limits);
        if (!(definition.material() instanceof Material3dDefinition material3d)) {
            throw new EffectsException(EffectsException.Kind.INVALID_EFFECT,
                    "3D decal renderer requires a 3D material");
        }
        material = material3d;
        shaderProvider = new Material3dShaderProvider(limits,
                Objects.requireNonNull(assets, "assets"));
        mesh = new Mesh(true, definition.capacity() * 6, 0,
                new VertexAttribute(Usage.Position, 3, "a_position"),
                new VertexAttribute(Usage.ColorUnpacked, 4, "a_color"),
                new VertexAttribute(Usage.TextureCoordinates, 2, "a_texCoord0"));
        vertices = new float[definition.capacity() * 6 * 9];
    }

    /** Draws through an application-owned camera and already-begun render context. */
    public void render(DecalSnapshot snapshot, Camera camera, RenderContext context) {
        requireUsable();
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(context, "context");
        if (!definition.name().equals(snapshot.name())) {
            throw new EffectsException(EffectsException.Kind.INVALID_EFFECT,
                    "decal snapshot does not match renderer definition");
        }
        if (snapshot.decals().size() > definition.capacity()) {
            throw new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED,
                    "decal snapshot exceeds renderer capacity");
        }
        int cursor = 0;
        for (DecalSnapshot.Decal decal : snapshot.decals()) {
            cursor = writeQuad(cursor, decal);
        }
        if (cursor == 0) {
            return;
        }
        mesh.setVertices(vertices, 0, cursor);
        Renderable renderable = new Renderable();
        renderable.meshPart.set("decal", mesh, 0, mesh.getNumVertices(), GL20.GL_TRIANGLES);
        renderable.userData = material;
        renderable.worldTransform.idt();
        Shader shader = shaderProvider.getShader(renderable);
        shader.begin(camera, context);
        try {
            shader.render(renderable);
        } finally {
            shader.end();
        }
    }

    @Override public void close() {
        requireOwnerThread();
        if (!closed) {
            closed = true;
            mesh.dispose();
            shaderProvider.close();
        }
    }

    private int writeQuad(int cursor, DecalSnapshot.Decal decal) {
        Basis basis = basis(decal);
        float halfWidth = decal.width() * 0.5f;
        float halfHeight = decal.height() * 0.5f;
        cursor = vertex(cursor, decal, basis, -halfWidth, -halfHeight, 0f, 0f);
        cursor = vertex(cursor, decal, basis, halfWidth, -halfHeight, 1f, 0f);
        cursor = vertex(cursor, decal, basis, halfWidth, halfHeight, 1f, 1f);
        cursor = vertex(cursor, decal, basis, -halfWidth, -halfHeight, 0f, 0f);
        cursor = vertex(cursor, decal, basis, halfWidth, halfHeight, 1f, 1f);
        return vertex(cursor, decal, basis, -halfWidth, halfHeight, 0f, 1f);
    }

    private int vertex(int cursor, DecalSnapshot.Decal decal, Basis basis,
            float localX, float localY, float u, float v) {
        vertices[cursor++] = decal.x() + basis.tx() * localX + basis.bx() * localY;
        vertices[cursor++] = decal.y() + basis.ty() * localX + basis.by() * localY;
        vertices[cursor++] = decal.z() + basis.tz() * localX + basis.bz() * localY;
        vertices[cursor++] = decal.r();
        vertices[cursor++] = decal.g();
        vertices[cursor++] = decal.b();
        vertices[cursor++] = decal.a();
        vertices[cursor++] = u;
        vertices[cursor++] = v;
        return cursor;
    }

    private static Basis basis(DecalSnapshot.Decal decal) {
        float referenceX = 0f;
        float referenceY = Math.abs(decal.normalZ()) > 0.9f ? 1f : 0f;
        float referenceZ = referenceY == 0f ? 1f : 0f;
        float tx = referenceY * decal.normalZ() - referenceZ * decal.normalY();
        float ty = referenceZ * decal.normalX() - referenceX * decal.normalZ();
        float tz = referenceX * decal.normalY() - referenceY * decal.normalX();
        float length = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
        tx /= length;
        ty /= length;
        tz /= length;
        float bx = decal.normalY() * tz - decal.normalZ() * ty;
        float by = decal.normalZ() * tx - decal.normalX() * tz;
        float bz = decal.normalX() * ty - decal.normalY() * tx;
        float radians = (float) Math.toRadians(decal.rotationDegrees());
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        return new Basis(tx * cos + bx * sin, ty * cos + by * sin, tz * cos + bz * sin,
                bx * cos - tx * sin, by * cos - ty * sin, bz * cos - tz * sin);
    }

    private void requireUsable() {
        requireOwnerThread();
        if (closed) {
            throw new EffectsException(EffectsException.Kind.INVALID_LIFECYCLE,
                    "3D decal renderer is closed");
        }
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new EffectsException(EffectsException.Kind.WRONG_THREAD,
                    "Decal3dRenderer must be used on its owning render thread");
        }
    }

    private record Basis(float tx, float ty, float tz, float bx, float by, float bz) {}
}
