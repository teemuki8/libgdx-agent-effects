package io.github.teemuki8.libgdx.agent.effects.libgdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.BufferUtils;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCapabilities;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import java.nio.IntBuffer;
import org.junit.jupiter.api.Test;

class GpuParticleInstanceTest {

    @Test
    void swapsBoundedStateTexturesAndRestoresHostFramebufferState() throws Exception {
        GdxTestHost.runGl3(() -> {
            EffectsLimits limits = EffectsLimits.developmentDefaults();
            int framebuffer = integer(GL20.GL_FRAMEBUFFER_BINDING);
            int viewportX = viewport(0);
            int viewportY = viewport(1);
            int viewportWidth = viewport(2);
            int viewportHeight = viewport(3);
            Gdx.gl.glDisable(GL20.GL_BLEND);
            try (GpuParticleInstance particles = new GpuParticleInstance(
                    ParticleTestDefinitions.supported(), limits,
                    new EffectCapabilities(3, 0, 4096, true))) {
                int first = particles.currentStateTextureHandle();
                particles.advance(0.25f);
                assertNotEquals(first, particles.currentStateTextureHandle());
                assertEquals(1L, particles.generation());
                assertTrue(particles.backendEvidence().stateTexturePixels()
                        <= limits.maxTexturePixels());
                assertEquals(framebuffer, integer(GL20.GL_FRAMEBUFFER_BINDING));
                assertEquals(viewportX, viewport(0));
                assertEquals(viewportY, viewport(1));
                assertEquals(viewportWidth, viewport(2));
                assertEquals(viewportHeight, viewport(3));
                assertEquals(false, Gdx.gl.glIsEnabled(GL20.GL_BLEND));
                assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
            }
        });
    }

    private static int integer(int name) {
        IntBuffer value = BufferUtils.newIntBuffer(1);
        Gdx.gl.glGetIntegerv(name, value);
        return value.get(0);
    }

    private static int viewport(int index) {
        IntBuffer values = BufferUtils.newIntBuffer(4);
        Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, values);
        return values.get(index);
    }
}
