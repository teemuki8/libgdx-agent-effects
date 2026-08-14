package io.github.teemuki8.libgdx.agent.effects.libgdx;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import io.github.teemuki8.libgdx.agent.effects.core.PostProcessRenderEvidence;
import java.util.Objects;

/** Borrowed render-thread graph output valid until the renderer's next resize or close. */
public final class PostProcessGraphResult {
    private final FrameBuffer framebuffer;
    private final PostProcessRenderEvidence evidence;

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

    FrameBuffer framebuffer() {
        return framebuffer;
    }
}
