package io.github.teemuki8.libgdx.agent.effects.libgdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.BufferUtils;
import io.github.teemuki8.libgdx.agent.effects.core.PostProcessRenderEvidence;
import io.github.teemuki8.libgdx.agent.effects.core.RgbaImage;
import java.nio.IntBuffer;
import java.util.Objects;

/** Borrowed render-thread graph output valid until the renderer's next resize or close. */
public final class PostProcessGraphResult {
    private final FrameBuffer framebuffer;
    private final PostProcessRenderEvidence evidence;
    private final Thread ownerThread = Thread.currentThread();

    PostProcessGraphResult(FrameBuffer framebuffer, PostProcessRenderEvidence evidence) {
        this.framebuffer = Objects.requireNonNull(framebuffer, "framebuffer");
        this.evidence = Objects.requireNonNull(evidence, "evidence");
    }

    /** Borrowed output texture; callers must not dispose it. */
    public Texture output() {
        return framebuffer.getColorBufferTexture();
    }

    /** Immutable bounded execution and pool evidence. */
    public PostProcessRenderEvidence evidence() {
        return evidence;
    }

    /** Captures the still-valid borrowed output into an immutable packed-RGBA image. */
    public RgbaImage capture() {
        if (Thread.currentThread() != ownerThread) {
            throw new io.github.teemuki8.libgdx.agent.effects.core.EffectsException(
                    io.github.teemuki8.libgdx.agent.effects.core.EffectsException.Kind.WRONG_THREAD,
                    "post-process output capture must run on its render thread");
        }
        IntBuffer viewport = BufferUtils.newIntBuffer(4);
        Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, viewport);
        IntBuffer binding = BufferUtils.newIntBuffer(1);
        Gdx.gl.glGetIntegerv(GL20.GL_FRAMEBUFFER_BINDING, binding);
        try {
            framebuffer.bind();
            Gdx.gl.glViewport(0, 0, framebuffer.getWidth(), framebuffer.getHeight());
            byte[] bytes = com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixels(
                    0, 0, framebuffer.getWidth(), framebuffer.getHeight(), false);
            int[] pixels = new int[framebuffer.getWidth() * framebuffer.getHeight()];
            for (int index = 0; index < pixels.length; index++) {
                int offset = index * 4;
                int red = bytes[offset] & 0xff;
                int green = bytes[offset + 1] & 0xff;
                int blue = bytes[offset + 2] & 0xff;
                int alpha = bytes[offset + 3] & 0xff;
                pixels[index] = alpha << 24 | red << 16 | green << 8 | blue;
            }
            return new RgbaImage(framebuffer.getWidth(), framebuffer.getHeight(), pixels);
        } finally {
            Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, binding.get(0));
            Gdx.gl.glViewport(viewport.get(0), viewport.get(1),
                    viewport.get(2), viewport.get(3));
        }
    }

    FrameBuffer framebuffer() {
        return framebuffer;
    }
}
