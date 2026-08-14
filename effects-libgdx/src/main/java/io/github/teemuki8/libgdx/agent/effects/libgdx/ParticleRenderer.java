package io.github.teemuki8.libgdx.agent.effects.libgdx;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleSnapshot;
import java.util.Objects;

/** Render-thread-confined reusable quad or point-sprite particle adapter. */
public final class ParticleRenderer implements AutoCloseable {
    private final ParticleDefinition definition;
    private final ParticleRenderMode mode;
    private final Thread ownerThread = Thread.currentThread();
    private final Material2dRenderer materialRenderer;
    private final Mesh mesh;
    private final float[] vertices;
    private boolean closed;

    /** Allocates bounded renderer-owned geometry on the current render thread. */
    public ParticleRenderer(ParticleDefinition definition, EffectsLimits limits,
            ParticleRenderMode mode) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.mode = Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(limits, "limits");
        definition.validate(limits);
        materialRenderer = new Material2dRenderer(limits);
        if (mode == ParticleRenderMode.SPRITE_QUADS) {
            mesh = new Mesh(true, definition.capacity() * 6, 0,
                    new VertexAttribute(Usage.Position, 2, "a_position"),
                    new VertexAttribute(Usage.ColorUnpacked, 4, "a_color"),
                    new VertexAttribute(Usage.TextureCoordinates, 2, "a_texCoord0"));
            vertices = new float[definition.capacity() * 6 * 8];
        } else {
            mesh = new Mesh(true, definition.capacity(), 0,
                    new VertexAttribute(Usage.Position, 2, "a_position"),
                    new VertexAttribute(Usage.ColorUnpacked, 4, "a_color"),
                    new VertexAttribute(Usage.Generic, 1, "a_size"));
            vertices = new float[definition.capacity() * 7];
        }
    }

    /** Draws a matching bounded snapshot and restores material GL state. */
    public void render(ParticleSnapshot snapshot, RegisteredAssetResolver assets) {
        requireUsable();
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(assets, "assets");
        if (!definition.name().equals(snapshot.name())) {
            throw new EffectsException(EffectsException.Kind.INVALID_EFFECT,
                    "particle snapshot does not match renderer definition");
        }
        if (snapshot.particles().size() > definition.capacity()) {
            throw new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED,
                    "particle snapshot exceeds renderer capacity");
        }
        int cursor = mode == ParticleRenderMode.SPRITE_QUADS
                ? buildQuads(snapshot) : buildPoints(snapshot);
        if (cursor == 0) {
            return;
        }
        mesh.setVertices(vertices, 0, cursor);
        materialRenderer.render(definition.material(), mesh,
                mode == ParticleRenderMode.SPRITE_QUADS ? GL20.GL_TRIANGLES : GL20.GL_POINTS,
                assets);
    }

    /** Releases only renderer-owned mesh and material resources. */
    @Override public void close() {
        requireOwnerThread();
        if (!closed) {
            closed = true;
            mesh.dispose();
            materialRenderer.close();
        }
    }

    private int buildQuads(ParticleSnapshot snapshot) {
        int cursor = 0;
        for (ParticleSnapshot.Particle particle : snapshot.particles()) {
            float half = particle.size() * 0.5f;
            cursor = quadVertex(cursor, particle, -half, -half, 0f, 0f);
            cursor = quadVertex(cursor, particle, half, -half, 1f, 0f);
            cursor = quadVertex(cursor, particle, half, half, 1f, 1f);
            cursor = quadVertex(cursor, particle, -half, -half, 0f, 0f);
            cursor = quadVertex(cursor, particle, half, half, 1f, 1f);
            cursor = quadVertex(cursor, particle, -half, half, 0f, 1f);
        }
        return cursor;
    }

    private int quadVertex(int cursor, ParticleSnapshot.Particle particle,
            float offsetX, float offsetY, float u, float v) {
        vertices[cursor++] = particle.x() + offsetX;
        vertices[cursor++] = particle.y() + offsetY;
        cursor = color(cursor, particle);
        vertices[cursor++] = u;
        vertices[cursor++] = v;
        return cursor;
    }

    private int buildPoints(ParticleSnapshot snapshot) {
        int cursor = 0;
        for (ParticleSnapshot.Particle particle : snapshot.particles()) {
            vertices[cursor++] = particle.x();
            vertices[cursor++] = particle.y();
            cursor = color(cursor, particle);
            vertices[cursor++] = particle.size();
        }
        return cursor;
    }

    private int color(int cursor, ParticleSnapshot.Particle particle) {
        vertices[cursor++] = particle.r();
        vertices[cursor++] = particle.g();
        vertices[cursor++] = particle.b();
        vertices[cursor++] = particle.a();
        return cursor;
    }

    private void requireUsable() {
        requireOwnerThread();
        if (closed) {
            throw new EffectsException(EffectsException.Kind.INVALID_LIFECYCLE,
                    "particle renderer is closed");
        }
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new EffectsException(EffectsException.Kind.WRONG_THREAD,
                    "ParticleRenderer must be used on its owning render thread");
        }
    }
}
