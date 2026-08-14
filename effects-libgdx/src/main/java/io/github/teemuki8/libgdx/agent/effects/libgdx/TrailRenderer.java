package io.github.teemuki8.libgdx.agent.effects.libgdx;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.TrailCap;
import io.github.teemuki8.libgdx.agent.effects.core.TrailDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.TrailJoin;
import io.github.teemuki8.libgdx.agent.effects.core.TrailSnapshot;
import java.util.List;
import java.util.Objects;

/** Render-thread-confined ribbon adapter backed by one reusable owned mesh. */
public final class TrailRenderer implements AutoCloseable {
    private static final int FLOATS_PER_VERTEX = 8;

    private final TrailDefinition definition;
    private final Thread ownerThread = Thread.currentThread();
    private final Material2dRenderer materialRenderer;
    private final Mesh mesh;
    private final float[] vertices;
    private boolean closed;

    /** Allocates the bounded ribbon mesh on the current application render thread. */
    public TrailRenderer(TrailDefinition definition, EffectsLimits limits) {
        this.definition = Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(limits, "limits");
        definition.validate(limits);
        materialRenderer = new Material2dRenderer(limits);
        mesh = new Mesh(true, definition.pointLimit() * 2, 0,
                new VertexAttribute(Usage.Position, 2, "a_position"),
                new VertexAttribute(Usage.ColorUnpacked, 4, "a_color"),
                new VertexAttribute(Usage.TextureCoordinates, 2, "a_texCoord0"));
        vertices = new float[definition.pointLimit() * 2 * FLOATS_PER_VERTEX];
    }

    /** Draws one immutable snapshot and restores every GL state changed by the material adapter. */
    public void render(TrailSnapshot snapshot, RegisteredAssetResolver assets) {
        requireUsable();
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(assets, "assets");
        if (!definition.name().equals(snapshot.name())) {
            throw new EffectsException(EffectsException.Kind.INVALID_EFFECT,
                    "trail snapshot does not match renderer definition");
        }
        if (snapshot.points().size() > definition.pointLimit()) {
            throw new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED,
                    "trail snapshot exceeds renderer capacity");
        }
        if (snapshot.points().size() < 2) {
            return;
        }
        int floatCount = buildVertices(snapshot.points());
        mesh.setVertices(vertices, 0, floatCount);
        materialRenderer.render(definition.material(), mesh, GL20.GL_TRIANGLE_STRIP, assets);
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

    private int buildVertices(List<TrailSnapshot.Point> points) {
        int cursor = 0;
        for (int index = 0; index < points.size(); index++) {
            TrailSnapshot.Point point = points.get(index);
            Direction direction = direction(points, index);
            float offsetX = -direction.y();
            float offsetY = direction.x();
            float scale = point.width() * 0.5f * miterScale(points, index, offsetX, offsetY);
            float centerX = point.x();
            float centerY = point.y();
            if (definition.cap() == TrailCap.SQUARE && index == 0) {
                centerX -= direction.x() * point.width() * 0.5f;
                centerY -= direction.y() * point.width() * 0.5f;
            } else if (definition.cap() == TrailCap.SQUARE && index == points.size() - 1) {
                centerX += direction.x() * point.width() * 0.5f;
                centerY += direction.y() * point.width() * 0.5f;
            }
            cursor = writeVertex(cursor, centerX + offsetX * scale, centerY + offsetY * scale,
                    point, 0f);
            cursor = writeVertex(cursor, centerX - offsetX * scale, centerY - offsetY * scale,
                    point, 1f);
        }
        return cursor;
    }

    private float miterScale(List<TrailSnapshot.Point> points, int index,
            float miterX, float miterY) {
        if (definition.join() == TrailJoin.BEVEL || index == 0 || index == points.size() - 1) {
            return 1f;
        }
        Direction outgoing = segment(points.get(index), points.get(index + 1));
        float normalX = -outgoing.y();
        float normalY = outgoing.x();
        float denominator = Math.abs(miterX * normalX + miterY * normalY);
        if (denominator < 0.0001f) {
            return definition.miterLimit();
        }
        return Math.min(definition.miterLimit(), 1f / denominator);
    }

    private int writeVertex(int cursor, float x, float y, TrailSnapshot.Point point, float v) {
        vertices[cursor++] = x;
        vertices[cursor++] = y;
        vertices[cursor++] = point.r();
        vertices[cursor++] = point.g();
        vertices[cursor++] = point.b();
        vertices[cursor++] = point.a();
        vertices[cursor++] = point.u();
        vertices[cursor++] = v;
        return cursor;
    }

    private static Direction direction(List<TrailSnapshot.Point> points, int index) {
        if (index == 0) {
            return segment(points.get(0), points.get(1));
        }
        if (index == points.size() - 1) {
            return segment(points.get(index - 1), points.get(index));
        }
        Direction incoming = segment(points.get(index - 1), points.get(index));
        Direction outgoing = segment(points.get(index), points.get(index + 1));
        return normalized(incoming.x() + outgoing.x(), incoming.y() + outgoing.y(), outgoing);
    }

    private static Direction segment(TrailSnapshot.Point first, TrailSnapshot.Point second) {
        return normalized(second.x() - first.x(), second.y() - first.y(),
                new Direction(1f, 0f));
    }

    private static Direction normalized(float x, float y, Direction fallback) {
        float length = (float) Math.sqrt(x * x + y * y);
        if (length < 0.000001f) {
            return fallback;
        }
        return new Direction(x / length, y / length);
    }

    private void requireUsable() {
        requireOwnerThread();
        if (closed) {
            throw new EffectsException(EffectsException.Kind.INVALID_LIFECYCLE,
                    "trail renderer is closed");
        }
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new EffectsException(EffectsException.Kind.WRONG_THREAD,
                    "TrailRenderer must be used on its owning render thread");
        }
    }

    private record Direction(float x, float y) {}
}
