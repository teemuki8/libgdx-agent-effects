package io.github.teemuki8.libgdx.agent.effects.libgdx;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import io.github.teemuki8.libgdx.agent.effects.core.BeamDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.BeamSnapshot;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.LightningDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.LightningSnapshot;
import io.github.teemuki8.libgdx.agent.effects.core.Material2dDefinition;
import java.util.Objects;

/** Shared render-thread quad adapter for bounded beams and lightning segments. */
public final class BeamRenderer implements AutoCloseable {
    private static final int VERTICES_PER_SEGMENT = 6;
    private static final int FLOATS_PER_VERTEX = 8;

    private final String name;
    private final Material2dDefinition material;
    private final int segmentCapacity;
    private final Thread ownerThread = Thread.currentThread();
    private final Material2dRenderer materialRenderer;
    private final Mesh mesh;
    private final float[] vertices;
    private boolean closed;

    /** Creates a renderer for one declared straight beam. */
    public BeamRenderer(BeamDefinition definition, EffectsLimits limits) {
        this(definition.name(), definition.material(),
                validatedCapacity(definition, limits), limits);
    }

    /** Creates a renderer for one declared lightning effect, including its branches. */
    public BeamRenderer(LightningDefinition definition, EffectsLimits limits) {
        this(definition.name(), definition.material(),
                validatedCapacity(definition, limits), limits);
    }

    private BeamRenderer(String name, Material2dDefinition material,
            int segmentCapacity, EffectsLimits limits) {
        this.name = Objects.requireNonNull(name, "name");
        this.material = Objects.requireNonNull(material, "material");
        this.segmentCapacity = segmentCapacity;
        Objects.requireNonNull(limits, "limits");
        materialRenderer = new Material2dRenderer(limits);
        int vertexCapacity = Math.multiplyExact(segmentCapacity, VERTICES_PER_SEGMENT);
        mesh = new Mesh(true, vertexCapacity, 0,
                new VertexAttribute(Usage.Position, 2, "a_position"),
                new VertexAttribute(Usage.ColorUnpacked, 4, "a_color"),
                new VertexAttribute(Usage.TextureCoordinates, 2, "a_texCoord0"));
        vertices = new float[Math.multiplyExact(vertexCapacity, FLOATS_PER_VERTEX)];
    }

    private static int validatedCapacity(BeamDefinition definition, EffectsLimits limits) {
        Objects.requireNonNull(definition, "definition").validate(limits);
        return definition.segmentLimit();
    }

    private static int validatedCapacity(LightningDefinition definition, EffectsLimits limits) {
        Objects.requireNonNull(definition, "definition").validate(limits);
        return Math.addExact(definition.segmentLimit(), definition.branchLimit());
    }

    /** Draws a matching straight-beam snapshot. */
    public void render(BeamSnapshot snapshot, RegisteredAssetResolver assets) {
        requireUsable();
        Objects.requireNonNull(snapshot, "snapshot");
        requireSnapshot(snapshot.name(), snapshot.segments().size());
        int cursor = 0;
        for (BeamSnapshot.Segment segment : snapshot.segments()) {
            cursor = writeQuad(cursor, segment.startX(), segment.startY(),
                    segment.endX(), segment.endY(), segment.width(),
                    segment.r(), segment.g(), segment.b(), segment.a());
        }
        draw(cursor, assets);
    }

    /** Draws a matching lightning snapshot using the same bounded segment geometry. */
    public void render(LightningSnapshot snapshot, RegisteredAssetResolver assets) {
        requireUsable();
        Objects.requireNonNull(snapshot, "snapshot");
        requireSnapshot(snapshot.name(), snapshot.segments().size());
        int cursor = 0;
        for (LightningSnapshot.Segment segment : snapshot.segments()) {
            cursor = writeQuad(cursor, segment.startX(), segment.startY(),
                    segment.endX(), segment.endY(), segment.width(),
                    segment.r(), segment.g(), segment.b(), segment.a());
        }
        draw(cursor, assets);
    }

    /** Releases only the renderer-owned mesh and material adapter. */
    @Override public void close() {
        requireOwnerThread();
        if (!closed) {
            closed = true;
            mesh.dispose();
            materialRenderer.close();
        }
    }

    private void draw(int floatCount, RegisteredAssetResolver assets) {
        Objects.requireNonNull(assets, "assets");
        if (floatCount == 0) {
            return;
        }
        mesh.setVertices(vertices, 0, floatCount);
        materialRenderer.render(material, mesh, GL20.GL_TRIANGLES, assets);
    }

    private int writeQuad(int cursor, float startX, float startY,
            float endX, float endY, float width, float r, float g, float b, float a) {
        float dx = endX - startX;
        float dy = endY - startY;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 0.000001f || width == 0f) {
            return cursor;
        }
        float offsetX = -dy / length * width * 0.5f;
        float offsetY = dx / length * width * 0.5f;
        cursor = vertex(cursor, startX + offsetX, startY + offsetY, r, g, b, a, 0f, 0f);
        cursor = vertex(cursor, startX - offsetX, startY - offsetY, r, g, b, a, 0f, 1f);
        cursor = vertex(cursor, endX - offsetX, endY - offsetY, r, g, b, a, 1f, 1f);
        cursor = vertex(cursor, startX + offsetX, startY + offsetY, r, g, b, a, 0f, 0f);
        cursor = vertex(cursor, endX - offsetX, endY - offsetY, r, g, b, a, 1f, 1f);
        return vertex(cursor, endX + offsetX, endY + offsetY, r, g, b, a, 1f, 0f);
    }

    private int vertex(int cursor, float x, float y, float r, float g, float b, float a,
            float u, float v) {
        vertices[cursor++] = x;
        vertices[cursor++] = y;
        vertices[cursor++] = r;
        vertices[cursor++] = g;
        vertices[cursor++] = b;
        vertices[cursor++] = a;
        vertices[cursor++] = u;
        vertices[cursor++] = v;
        return cursor;
    }

    private void requireSnapshot(String snapshotName, int segmentCount) {
        if (!name.equals(snapshotName)) {
            throw new EffectsException(EffectsException.Kind.INVALID_EFFECT,
                    "segment snapshot does not match renderer definition");
        }
        if (segmentCount > segmentCapacity) {
            throw new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED,
                    "segment snapshot exceeds renderer capacity");
        }
    }

    private void requireUsable() {
        requireOwnerThread();
        if (closed) {
            throw new EffectsException(EffectsException.Kind.INVALID_LIFECYCLE,
                    "beam renderer is closed");
        }
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new EffectsException(EffectsException.Kind.WRONG_THREAD,
                    "BeamRenderer must be used on its owning render thread");
        }
    }
}
