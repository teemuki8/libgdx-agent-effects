package io.github.teemuki8.libgdx.agent.effects.libgdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.FloatFrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.BufferUtils;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCapabilities;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleBackendEvidence;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleModifier;
import java.nio.IntBuffer;
import java.util.Objects;

/** Render-thread GL3 ping-pong particle state-texture simulation. */
public final class GpuParticleInstance implements AutoCloseable {
    private static final String VERTEX_SHADER = """
            #version 150
            in vec2 a_position;
            void main(){ gl_Position=vec4(a_position,0.0,1.0); }
            """;
    private static final String FRAGMENT_SHADER = """
            #version 150
            uniform sampler2D u_state;
            uniform vec2 u_dimensions;
            uniform float u_capacity;
            uniform float u_delta;
            uniform vec3 u_gravity;
            uniform float u_drag;
            out vec4 fragColor;
            vec2 stateUv(float index) {
                float x=mod(index,u_dimensions.x);
                float y=floor(index/u_dimensions.x);
                return (vec2(x,y)+0.5)/u_dimensions;
            }
            void main(){
                float index=floor(gl_FragCoord.y)*u_dimensions.x+floor(gl_FragCoord.x);
                vec4 value=texture(u_state,stateUv(index));
                if(index<u_capacity){
                    vec4 velocity=texture(u_state,stateUv(index+u_capacity));
                    fragColor=vec4(value.xyz+velocity.xyz*u_delta,value.a+u_delta);
                }else if(index<u_capacity*2.0){
                    vec3 velocity=(value.xyz+u_gravity*u_delta)
                        *max(0.0,1.0-u_drag*u_delta);
                    fragColor=vec4(velocity,value.a);
                }else{
                    fragColor=vec4(0.0);
                }
            }
            """;

    private final Thread ownerThread = Thread.currentThread();
    private final ParticleDefinition definition;
    private final ParticleBackendEvidence backendEvidence;
    private final ParticleBackendSelector.Dimensions dimensions;
    private final FloatFrameBuffer[] state = new FloatFrameBuffer[2];
    private final ShaderProgram updateProgram;
    private final Mesh fullscreenQuad;
    private final float gravityX;
    private final float gravityY;
    private final float gravityZ;
    private final float drag;
    private int current;
    private long generation;
    private boolean closed;

    /** Allocates two bounded float state framebuffers on the current render thread. */
    public GpuParticleInstance(ParticleDefinition definition, EffectsLimits limits,
            EffectCapabilities capabilities) {
        this.definition = Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(capabilities, "capabilities");
        backendEvidence = ParticleBackendSelector.select(definition, capabilities,
                ParticleFallbackPolicy.REQUIRE_GPU, limits.maxTexturePixels());
        dimensions = ParticleBackendSelector.dimensions(
                definition.capacity(), capabilities.maxTextureSize());
        float gx = 0f;
        float gy = 0f;
        float gz = 0f;
        float damping = 0f;
        for (ParticleModifier modifier : definition.modifiers()) {
            if (modifier instanceof ParticleModifier.Gravity gravityModifier) {
                gx += gravityModifier.x();
                gy += gravityModifier.y();
                gz += gravityModifier.z();
            } else if (modifier instanceof ParticleModifier.Drag dragModifier) {
                damping += dragModifier.coefficient();
            }
        }
        gravityX = gx;
        gravityY = gy;
        gravityZ = gz;
        drag = damping;
        state[0] = new FloatFrameBuffer(dimensions.width(), dimensions.height(), false);
        state[1] = new FloatFrameBuffer(dimensions.width(), dimensions.height(), false);
        state[0].getColorBufferTexture().setFilter(Texture.TextureFilter.Nearest,
                Texture.TextureFilter.Nearest);
        state[1].getColorBufferTexture().setFilter(Texture.TextureFilter.Nearest,
                Texture.TextureFilter.Nearest);
        updateProgram = new ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if (!updateProgram.isCompiled()) {
            String log = updateProgram.getLog();
            disposeAllocated();
            throw new EffectsException(EffectsException.Kind.COMPILE_FAILED,
                    log.substring(0, Math.min(log.length(), limits.maxDiagnosticChars())));
        }
        fullscreenQuad = new Mesh(true, 6, 0,
                new VertexAttribute(Usage.Position, 2, "a_position"));
        fullscreenQuad.setVertices(new float[] {
                -1f, -1f, 1f, -1f, 1f, 1f,
                -1f, -1f, 1f, 1f, -1f, 1f,
        });
        clear(state[0]);
        clear(state[1]);
    }

    /** Executes one explicit ping-pong state update and restores host GL state. */
    public void advance(float deltaSeconds) {
        requireUsable();
        if (!Float.isFinite(deltaSeconds) || deltaSeconds < 0f) {
            throw new EffectsException(EffectsException.Kind.INVALID_EFFECT,
                    "deltaSeconds must be finite and nonnegative");
        }
        GlState host = GlState.capture();
        int next = 1 - current;
        try {
            state[next].bind();
            Gdx.gl.glViewport(0, 0, dimensions.width(), dimensions.height());
            Gdx.gl.glDisable(GL20.GL_BLEND);
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
            Gdx.gl.glDisable(GL20.GL_CULL_FACE);
            state[current].getColorBufferTexture().bind(0);
            updateProgram.bind();
            updateProgram.setUniformi("u_state", 0);
            updateProgram.setUniformf("u_dimensions", dimensions.width(), dimensions.height());
            updateProgram.setUniformf("u_capacity", definition.capacity());
            updateProgram.setUniformf("u_delta", deltaSeconds);
            updateProgram.setUniformf("u_gravity", gravityX, gravityY, gravityZ);
            updateProgram.setUniformf("u_drag", drag);
            fullscreenQuad.render(updateProgram, GL20.GL_TRIANGLES);
        } finally {
            host.restore();
        }
        current = next;
        generation++;
    }

    /** Returns immutable selection and texture-bound evidence. */
    public ParticleBackendEvidence backendEvidence() {
        requireUsable();
        return backendEvidence;
    }

    /** Current state texture object handle for render-thread diagnostics. */
    public int currentStateTextureHandle() {
        requireUsable();
        return state[current].getColorBufferTexture().getTextureObjectHandle();
    }

    /** Number of completed explicit state swaps. */
    public long generation() {
        requireUsable();
        return generation;
    }

    /** Releases only this instance's GL programs, mesh, and state framebuffers. */
    @Override public void close() {
        requireOwnerThread();
        if (!closed) {
            closed = true;
            fullscreenQuad.dispose();
            updateProgram.dispose();
            state[0].dispose();
            state[1].dispose();
        }
    }

    private static void clear(FloatFrameBuffer framebuffer) {
        GlState host = GlState.capture();
        try {
            framebuffer.bind();
            Gdx.gl.glViewport(0, 0, framebuffer.getWidth(), framebuffer.getHeight());
            Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        } finally {
            host.restore();
        }
    }

    private void disposeAllocated() {
        updateProgram.dispose();
        state[0].dispose();
        state[1].dispose();
    }

    private void requireUsable() {
        requireOwnerThread();
        if (closed) {
            throw new EffectsException(EffectsException.Kind.INVALID_LIFECYCLE,
                    "GPU particle instance is closed");
        }
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new EffectsException(EffectsException.Kind.WRONG_THREAD,
                    "GpuParticleInstance must be used on its owning render thread");
        }
    }

    private static int integer(int name) {
        IntBuffer value = BufferUtils.newIntBuffer(1);
        Gdx.gl.glGetIntegerv(name, value);
        return value.get(0);
    }

    private record GlState(int framebuffer, int program, int activeTexture,
            int activeTextureBinding, int textureZeroBinding,
            int viewportX, int viewportY, int viewportWidth,
            int viewportHeight, boolean blend, boolean depth, boolean cull) {
        static GlState capture() {
            IntBuffer viewport = BufferUtils.newIntBuffer(4);
            Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, viewport);
            int active = integer(GL20.GL_ACTIVE_TEXTURE);
            int activeBinding = integer(GL20.GL_TEXTURE_BINDING_2D);
            Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
            int zeroBinding = integer(GL20.GL_TEXTURE_BINDING_2D);
            Gdx.gl.glActiveTexture(active);
            return new GlState(integer(GL20.GL_FRAMEBUFFER_BINDING),
                    integer(GL20.GL_CURRENT_PROGRAM), active, activeBinding, zeroBinding,
                    viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3),
                    Gdx.gl.glIsEnabled(GL20.GL_BLEND),
                    Gdx.gl.glIsEnabled(GL20.GL_DEPTH_TEST),
                    Gdx.gl.glIsEnabled(GL20.GL_CULL_FACE));
        }

        void restore() {
            Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, framebuffer);
            Gdx.gl.glViewport(viewportX, viewportY, viewportWidth, viewportHeight);
            enabled(GL20.GL_BLEND, blend);
            enabled(GL20.GL_DEPTH_TEST, depth);
            enabled(GL20.GL_CULL_FACE, cull);
            Gdx.gl.glUseProgram(program);
            Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
            Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, textureZeroBinding);
            Gdx.gl.glActiveTexture(activeTexture);
            Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, activeTextureBinding);
        }

        private static void enabled(int capability, boolean enabled) {
            if (enabled) {
                Gdx.gl.glEnable(capability);
            } else {
                Gdx.gl.glDisable(capability);
            }
        }
    }
}
