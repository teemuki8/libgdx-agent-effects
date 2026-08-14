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
import io.github.teemuki8.libgdx.agent.effects.core.ParticleSnapshot;
import java.nio.IntBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
            uniform float u_lifetime;
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
                    float nextAge=value.a+u_delta;
                    fragColor=value.a<0.0||nextAge>=u_lifetime
                        ?vec4(0.0,0.0,0.0,-1.0)
                        :vec4(value.xyz+velocity.xyz*u_delta,nextAge);
                }else if(index<u_capacity*2.0){
                    vec4 position=texture(u_state,stateUv(index-u_capacity));
                    vec3 velocity=(value.xyz+u_gravity*u_delta)
                        *max(0.0,1.0-u_drag*u_delta);
                    fragColor=position.a<0.0||position.a+u_delta>=u_lifetime
                        ?vec4(0.0):vec4(velocity,0.0);
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
    private final int maxParticleOperations;
    private final boolean[] alive;
    private final long[] spawnIds;
    private final float[] ages;
    private long randomState;
    private long nextSpawnId;
    private long droppedParticles;
    private long evictedParticles;
    private double emissionAccumulator;
    private float anchorX;
    private float anchorY;
    private float anchorZ;
    private boolean hasAnchor;
    private int current;
    private long generation;
    private boolean closed;

    /** Allocates two bounded float state framebuffers on the current render thread. */
    public GpuParticleInstance(ParticleDefinition definition, EffectsLimits limits,
            EffectCapabilities capabilities) {
        this(definition, limits, capabilities, 0L);
    }

    /** Allocates a seeded bounded GPU simulation on the current render thread. */
    public GpuParticleInstance(ParticleDefinition definition, EffectsLimits limits,
            EffectCapabilities capabilities, long seed) {
        this.definition = Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(capabilities, "capabilities");
        definition.validate(limits);
        maxParticleOperations = limits.maxParticles();
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
        alive = new boolean[definition.capacity()];
        spawnIds = new long[definition.capacity()];
        ages = new float[definition.capacity()];
        randomState = seed;
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

    /** Updates the declared point-emitter anchor without retaining caller objects. */
    public void setAnchor(String name, float x, float y, float z) {
        requireUsable();
        Objects.requireNonNull(name, "name");
        if (!definition.anchorName().equals(name)) {
            throw new EffectsException(EffectsException.Kind.INVALID_EFFECT,
                    "unknown GPU particle anchor: " + name);
        }
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
            throw new EffectsException(EffectsException.Kind.INVALID_EFFECT,
                    "GPU particle anchor coordinates must be finite");
        }
        anchorX = x;
        anchorY = y;
        anchorZ = z;
        hasAnchor = true;
    }

    /** Emits an immediate seeded burst into bounded GPU state. */
    public void burst(int count) {
        requireUsable();
        if (count < 0 || count > maxParticleOperations) {
            throw new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED,
                    "GPU particle burst exceeds configured capacity");
        }
        if (!hasAnchor && count > 0) {
            throw new EffectsException(EffectsException.Kind.INVALID_EFFECT,
                    "GPU particle anchor is not available");
        }
        GlState host = GlState.capture();
        try {
            for (int index = 0; index < count; index++) {
                spawn();
            }
        } finally {
            host.restore();
        }
    }

    /** Executes one explicit ping-pong state update and restores host GL state. */
    public void advance(float deltaSeconds) {
        requireUsable();
        if (!Float.isFinite(deltaSeconds) || deltaSeconds < 0f) {
            throw new EffectsException(EffectsException.Kind.INVALID_EFFECT,
                    "deltaSeconds must be finite and nonnegative");
        }
        double prospectiveEmission = emissionAccumulator
                + definition.emissionRate() * (double) deltaSeconds;
        double rawEmissions = hasAnchor ? Math.floor(prospectiveEmission) : 0d;
        if (rawEmissions > maxParticleOperations) {
            throw new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED,
                    "GPU particle emission exceeds per-advance capacity");
        }
        int emissions = (int) rawEmissions;
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
            updateProgram.setUniformf("u_lifetime", definition.lifetimeSeconds());
            updateProgram.setUniformf("u_gravity", gravityX, gravityY, gravityZ);
            updateProgram.setUniformf("u_drag", drag);
            fullscreenQuad.render(updateProgram, GL20.GL_TRIANGLES);
            current = next;
            generation++;
            ageMetadata(deltaSeconds);
            emissionAccumulator = hasAnchor ? prospectiveEmission - emissions : 0d;
            for (int index = 0; index < emissions; index++) {
                spawn();
            }
        } finally {
            host.restore();
        }
    }

    /** Reads the current bounded GPU state into a stable immutable particle snapshot. */
    public ParticleSnapshot snapshot() {
        requireUsable();
        FloatBuffer values = BufferUtils.newFloatBuffer(dimensions.pixels() * 4);
        GlState host = GlState.capture();
        try {
            state[current].bind();
            Gdx.gl.glReadPixels(0, 0, dimensions.width(), dimensions.height(),
                    GL20.GL_RGBA, GL20.GL_FLOAT, values);
        } finally {
            host.restore();
        }
        List<ParticleSnapshot.Particle> particles = new ArrayList<>();
        for (int slot = 0; slot < alive.length; slot++) {
            if (!alive[slot]) {
                continue;
            }
            int position = slot * 4;
            int velocity = (definition.capacity() + slot) * 4;
            float age = values.get(position + 3);
            float normalizedAge = Math.min(1f, age / definition.lifetimeSeconds());
            io.github.teemuki8.libgdx.agent.effects.core.ColorGradient.Color color =
                    definition.color().sample(normalizedAge);
            particles.add(new ParticleSnapshot.Particle(spawnIds[slot],
                    values.get(position), values.get(position + 1), values.get(position + 2),
                    values.get(velocity), values.get(velocity + 1), values.get(velocity + 2),
                    age, definition.lifetimeSeconds(),
                    Math.max(0f, definition.size().sample(normalizedAge)),
                    color.r(), color.g(), color.b(), color.a()));
        }
        particles.sort(Comparator.comparingLong(ParticleSnapshot.Particle::spawnId));
        return new ParticleSnapshot(definition.name(), particles,
                droppedParticles, evictedParticles);
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
            Gdx.gl.glClearColor(0f, 0f, 0f, -1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        } finally {
            host.restore();
        }
    }

    private void ageMetadata(float deltaSeconds) {
        for (int slot = 0; slot < alive.length; slot++) {
            if (alive[slot]) {
                ages[slot] += deltaSeconds;
                if (ages[slot] >= definition.lifetimeSeconds()) {
                    alive[slot] = false;
                }
            }
        }
    }

    private void spawn() {
        int slot = freeSlot();
        if (slot < 0 && definition.capacityPolicy()
                == io.github.teemuki8.libgdx.agent.effects.core.ParticleCapacityPolicy.DROP_NEWEST) {
            droppedParticles++;
            nextSpawnId++;
            return;
        }
        if (slot < 0) {
            slot = oldestSlot();
            evictedParticles++;
        }
        float angle = nextFloat() * (float) (Math.PI * 2.0);
        float velocityX = (float) Math.cos(angle) * definition.initialSpeed();
        float velocityY = (float) Math.sin(angle) * definition.initialSpeed();
        alive[slot] = true;
        spawnIds[slot] = nextSpawnId++;
        ages[slot] = 0f;
        upload(slot, anchorX, anchorY, anchorZ, 0f);
        upload(definition.capacity() + slot, velocityX, velocityY, 0f, 0f);
    }

    private void upload(int stateIndex, float x, float y, float z, float w) {
        state[current].getColorBufferTexture().bind(0);
        FloatBuffer values = BufferUtils.newFloatBuffer(4);
        values.put(x).put(y).put(z).put(w).flip();
        Gdx.gl.glTexSubImage2D(GL20.GL_TEXTURE_2D, 0,
                stateIndex % dimensions.width(), stateIndex / dimensions.width(),
                1, 1, GL20.GL_RGBA, GL20.GL_FLOAT, values);
    }

    private int freeSlot() {
        for (int slot = 0; slot < alive.length; slot++) {
            if (!alive[slot]) {
                return slot;
            }
        }
        return -1;
    }

    private int oldestSlot() {
        int oldest = 0;
        for (int slot = 1; slot < alive.length; slot++) {
            if (spawnIds[slot] < spawnIds[oldest]) {
                oldest = slot;
            }
        }
        return oldest;
    }

    private float nextFloat() {
        randomState = randomState * 6364136223846793005L + 1442695040888963407L;
        return (randomState >>> 40) * (1f / (1 << 24));
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
            int viewportHeight, boolean blend, boolean depth, boolean cull,
            float clearRed, float clearGreen, float clearBlue, float clearAlpha) {
        static GlState capture() {
            IntBuffer viewport = BufferUtils.newIntBuffer(4);
            Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, viewport);
            FloatBuffer clear = BufferUtils.newFloatBuffer(4);
            Gdx.gl.glGetFloatv(GL20.GL_COLOR_CLEAR_VALUE, clear);
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
                    Gdx.gl.glIsEnabled(GL20.GL_CULL_FACE),
                    clear.get(0), clear.get(1), clear.get(2), clear.get(3));
        }

        void restore() {
            Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, framebuffer);
            Gdx.gl.glViewport(viewportX, viewportY, viewportWidth, viewportHeight);
            enabled(GL20.GL_BLEND, blend);
            enabled(GL20.GL_DEPTH_TEST, depth);
            enabled(GL20.GL_CULL_FACE, cull);
            Gdx.gl.glClearColor(clearRed, clearGreen, clearBlue, clearAlpha);
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
