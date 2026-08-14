package io.github.teemuki8.libgdx.agent.effects.libgdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.BufferUtils;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.PostProcessGraphDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.PostProcessRenderEvidence;
import io.github.teemuki8.libgdx.agent.effects.core.RenderPassDefinition;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Render-thread bounded pass-graph executor with reusable owned intermediate framebuffers. */
public final class PostProcessGraphRenderer implements AutoCloseable {
    private final PostProcessGraphDefinition graph;
    private final EffectsLimits limits;
    private final Thread ownerThread = Thread.currentThread();
    private final Material2dRenderer materialRenderer;
    private final Mesh fullscreenQuad;
    private final Map<String, FrameBuffer> pool = new LinkedHashMap<>();
    private long evictedFramebuffers;
    private int poolWidth;
    private int poolHeight;
    private boolean closed;

    /** Creates a graph executor without allocating framebuffers before the first render. */
    public PostProcessGraphRenderer(PostProcessGraphDefinition graph, EffectsLimits limits) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.limits = Objects.requireNonNull(limits, "limits");
        graph.validate(limits);
        materialRenderer = new Material2dRenderer(limits);
        fullscreenQuad = new Mesh(true, 6, 0,
                new VertexAttribute(Usage.Position, 2, "a_position"),
                new VertexAttribute(Usage.TextureCoordinates, 2, "a_texCoord0"));
        fullscreenQuad.setVertices(new float[] {
                -1f, -1f, 0f, 0f, 1f, -1f, 1f, 0f, 1f, 1f, 1f, 1f,
                -1f, -1f, 0f, 0f, 1f, 1f, 1f, 1f, -1f, 1f, 0f, 1f,
        });
    }

    /** Executes the stable graph using only explicitly named application captures. */
    public PostProcessGraphResult render(Map<String, SceneCapture> captures,
            int width, int height) {
        requireUsable();
        Objects.requireNonNull(captures, "captures");
        validateDimensions(width, height);
        List<String> missing = graph.externalInputs().stream()
                .filter(input -> !captures.containsKey(input)).toList();
        if (!missing.isEmpty()) {
            throw new EffectsException(EffectsException.Kind.INVALID_EFFECT,
                    "missing post-process inputs: " + String.join(",", missing));
        }
        ensurePool(width, height);
        Map<String, Texture> available = new HashMap<>();
        for (String name : graph.externalInputs()) {
            SceneCapture capture = Objects.requireNonNull(captures.get(name), "capture");
            validateCapture(name, capture);
            available.put(name, capture.colorTexture());
        }
        GlTargetState host = GlTargetState.capture();
        List<String> execution = new ArrayList<>();
        try {
            for (RenderPassDefinition pass : graph.executionOrder()) {
                FrameBuffer target = pool.get(pass.output());
                target.bind();
                Gdx.gl.glViewport(0, 0, width, height);
                Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                materialRenderer.render(pass.material(), fullscreenQuad, GL20.GL_TRIANGLES,
                        key -> available.get(key.value()));
                available.put(pass.output(), target.getColorBufferTexture());
                execution.add(pass.name());
            }
        } finally {
            host.restore();
        }
        FrameBuffer output = pool.get(graph.output());
        PostProcessRenderEvidence evidence = new PostProcessRenderEvidence(execution,
                pool.size(), evictedFramebuffers, List.of());
        return new PostProcessGraphResult(output, evidence);
    }

    /** Releases all renderer-owned framebuffers, geometry, and material resources. */
    @Override public void close() {
        requireOwnerThread();
        if (!closed) {
            closed = true;
            disposePool();
            fullscreenQuad.dispose();
            materialRenderer.close();
        }
    }

    private void ensurePool(int width, int height) {
        if (!pool.isEmpty() && (poolWidth != width || poolHeight != height)) {
            evictedFramebuffers += pool.size();
            disposePool();
        }
        if (pool.isEmpty()) {
            for (RenderPassDefinition pass : graph.passes()) {
                if (pool.size() >= graph.framebufferPoolLimit()) {
                    throw new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED,
                            "post-process framebuffer pool capacity exhausted");
                }
                pool.put(pass.output(),
                        new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false));
            }
            poolWidth = width;
            poolHeight = height;
        }
    }

    private void disposePool() {
        for (FrameBuffer framebuffer : pool.values()) {
            framebuffer.dispose();
        }
        pool.clear();
        poolWidth = 0;
        poolHeight = 0;
    }

    private void validateDimensions(int width, int height) {
        if (width <= 0 || height <= 0 || width > limits.maxRenderWidth()
                || height > limits.maxRenderHeight()
                || (long) width * height > limits.maxFramebufferPixels()) {
            throw new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED,
                    "post-process dimensions exceed configured limits");
        }
    }

    private static void validateCapture(String name, SceneCapture capture) {
        Texture texture = Objects.requireNonNull(capture.colorTexture(), "capture texture");
        if (capture.width() <= 0 || capture.height() <= 0
                || capture.width() != texture.getWidth() || capture.height() != texture.getHeight()) {
            throw new EffectsException(EffectsException.Kind.INVALID_EFFECT,
                    "invalid scene capture dimensions: " + name);
        }
    }

    private void requireUsable() {
        requireOwnerThread();
        if (closed) {
            throw new EffectsException(EffectsException.Kind.INVALID_LIFECYCLE,
                    "post-process graph renderer is closed");
        }
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new EffectsException(EffectsException.Kind.WRONG_THREAD,
                    "PostProcessGraphRenderer must be used on its owning render thread");
        }
    }

    private static int integer(int name) {
        IntBuffer value = BufferUtils.newIntBuffer(1);
        Gdx.gl.glGetIntegerv(name, value);
        return value.get(0);
    }

    private record GlTargetState(int framebuffer, int x, int y, int width, int height) {
        static GlTargetState capture() {
            IntBuffer viewport = BufferUtils.newIntBuffer(4);
            Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, viewport);
            return new GlTargetState(integer(GL20.GL_FRAMEBUFFER_BINDING),
                    viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
        }

        void restore() {
            Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, framebuffer);
            Gdx.gl.glViewport(x, y, width, height);
        }
    }
}
