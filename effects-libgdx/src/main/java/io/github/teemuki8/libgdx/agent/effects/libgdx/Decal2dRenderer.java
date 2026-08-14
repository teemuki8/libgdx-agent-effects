package io.github.teemuki8.libgdx.agent.effects.libgdx;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import io.github.teemuki8.libgdx.agent.effects.core.DecalDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.DecalSnapshot;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.Material2dDefinition;
import java.util.Objects;

/** Render-thread reusable 2D decal quad adapter. */
public final class Decal2dRenderer implements AutoCloseable {
    private final DecalDefinition definition;
    private final Material2dDefinition material;
    private final Thread ownerThread = Thread.currentThread();
    private final Material2dRenderer materialRenderer;
    private final Mesh mesh;
    private final float[] vertices;
    private boolean closed;

    /** Allocates bounded renderer-owned 2D decal geometry. */
    public Decal2dRenderer(DecalDefinition definition, EffectsLimits limits) {
        this.definition = Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(limits, "limits");
        definition.validate(limits);
        if (!(definition.material() instanceof Material2dDefinition material2d)) {
            throw new EffectsException(EffectsException.Kind.INVALID_EFFECT,
                    "2D decal renderer requires a 2D material");
        }
        material = material2d;
        materialRenderer = new Material2dRenderer(limits);
        mesh = new Mesh(true, definition.capacity() * 6, 0,
                new VertexAttribute(Usage.Position, 2, "a_position"),
                new VertexAttribute(Usage.ColorUnpacked, 4, "a_color"),
                new VertexAttribute(Usage.TextureCoordinates, 2, "a_texCoord0"));
        vertices = new float[definition.capacity() * 6 * 8];
    }

    /** Draws a matching ordered snapshot and restores material GL state. */
    public void render(DecalSnapshot snapshot, RegisteredAssetResolver assets) {
        requireUsable();
        Objects.requireNonNull(snapshot, "snapshot");
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
        if (cursor > 0) {
            mesh.setVertices(vertices, 0, cursor);
            materialRenderer.render(material, mesh, GL20.GL_TRIANGLES, assets);
        }
    }

    @Override public void close() {
        requireOwnerThread();
        if (!closed) {
            closed = true;
            mesh.dispose();
            materialRenderer.close();
        }
    }

    private int writeQuad(int cursor, DecalSnapshot.Decal decal) {
        float radians = (float) Math.toRadians(decal.rotationDegrees());
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        float halfWidth = decal.width() * 0.5f;
        float halfHeight = decal.height() * 0.5f;
        cursor = vertex(cursor, decal, -halfWidth, -halfHeight, cos, sin, 0f, 0f);
        cursor = vertex(cursor, decal, halfWidth, -halfHeight, cos, sin, 1f, 0f);
        cursor = vertex(cursor, decal, halfWidth, halfHeight, cos, sin, 1f, 1f);
        cursor = vertex(cursor, decal, -halfWidth, -halfHeight, cos, sin, 0f, 0f);
        cursor = vertex(cursor, decal, halfWidth, halfHeight, cos, sin, 1f, 1f);
        return vertex(cursor, decal, -halfWidth, halfHeight, cos, sin, 0f, 1f);
    }

    private int vertex(int cursor, DecalSnapshot.Decal decal, float localX, float localY,
            float cos, float sin, float u, float v) {
        vertices[cursor++] = decal.x() + localX * cos - localY * sin;
        vertices[cursor++] = decal.y() + localX * sin + localY * cos;
        vertices[cursor++] = decal.r();
        vertices[cursor++] = decal.g();
        vertices[cursor++] = decal.b();
        vertices[cursor++] = decal.a();
        vertices[cursor++] = u;
        vertices[cursor++] = v;
        return cursor;
    }

    private void requireUsable() {
        requireOwnerThread();
        if (closed) {
            throw new EffectsException(EffectsException.Kind.INVALID_LIFECYCLE,
                    "2D decal renderer is closed");
        }
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new EffectsException(EffectsException.Kind.WRONG_THREAD,
                    "Decal2dRenderer must be used on its owning render thread");
        }
    }
}
