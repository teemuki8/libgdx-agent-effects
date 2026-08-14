package io.github.teemuki8.libgdx.agent.effects.showcase;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import io.github.teemuki8.libgdx.agent.effects.core.AssetKey;
import io.github.teemuki8.libgdx.agent.effects.core.BeamDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.BeamSnapshot;
import io.github.teemuki8.libgdx.agent.effects.core.BlendMode;
import io.github.teemuki8.libgdx.agent.effects.core.ColorGradient;
import io.github.teemuki8.libgdx.agent.effects.core.DecalDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.DecalSnapshot;
import io.github.teemuki8.libgdx.agent.effects.core.DistortionFieldDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCapabilities;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.FloatCurve;
import io.github.teemuki8.libgdx.agent.effects.core.ImportLimits;
import io.github.teemuki8.libgdx.agent.effects.core.LightningDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.LightningSnapshot;
import io.github.teemuki8.libgdx.agent.effects.core.Material2dDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleBackendEvidence;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleCapacityPolicy;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleImportResult;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleSnapshot;
import io.github.teemuki8.libgdx.agent.effects.core.PostProcessGraphDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.RenderPassDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.RgbaImage;
import io.github.teemuki8.libgdx.agent.effects.core.RuntimeLimits;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderSource;
import io.github.teemuki8.libgdx.agent.effects.core.TrailCap;
import io.github.teemuki8.libgdx.agent.effects.core.TrailDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.TrailJoin;
import io.github.teemuki8.libgdx.agent.effects.core.TrailSnapshot;
import io.github.teemuki8.libgdx.agent.effects.core.TrailUvMode;
import io.github.teemuki8.libgdx.agent.effects.importer.libgdx.LibgdxParticleImporter;
import io.github.teemuki8.libgdx.agent.effects.runtime.CpuParticleInstance;
import io.github.teemuki8.libgdx.agent.effects.runtime.EffectAnchor;
import io.github.teemuki8.libgdx.agent.effects.libgdx.BeamRenderer;
import io.github.teemuki8.libgdx.agent.effects.libgdx.Decal2dRenderer;
import io.github.teemuki8.libgdx.agent.effects.libgdx.DistortionRenderer;
import io.github.teemuki8.libgdx.agent.effects.libgdx.GpuParticleInstance;
import io.github.teemuki8.libgdx.agent.effects.libgdx.Material2dRenderer;
import io.github.teemuki8.libgdx.agent.effects.libgdx.ParticleBackendSelector;
import io.github.teemuki8.libgdx.agent.effects.libgdx.ParticleFallbackPolicy;
import io.github.teemuki8.libgdx.agent.effects.libgdx.ParticleRenderMode;
import io.github.teemuki8.libgdx.agent.effects.libgdx.ParticleRenderer;
import io.github.teemuki8.libgdx.agent.effects.libgdx.PostProcessGraphRenderer;
import io.github.teemuki8.libgdx.agent.effects.libgdx.PostProcessGraphResult;
import io.github.teemuki8.libgdx.agent.effects.libgdx.SceneCapture;
import io.github.teemuki8.libgdx.agent.effects.libgdx.TrailRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Deterministic cross-family scene used by the showcase and native qualification fixture. */
public final class GeneralVfxScene {
    private static final int SIZE = 32;
    private static final EffectsLimits LIMITS = EffectsLimits.developmentDefaults();
    private static final ShaderSource COLOR_SHADER = new ShaderSource("""
            attribute vec2 a_position;
            attribute vec4 a_color;
            attribute vec2 a_texCoord0;
            varying vec4 v_color;
            void main(){v_color=a_color;gl_Position=vec4(a_position,0.0,1.0);}
            """, "varying vec4 v_color;void main(){gl_FragColor=v_color;}");
    private static final String TEXTURE_VERTEX = """
            attribute vec2 a_position;
            attribute vec2 a_texCoord0;
            varying vec2 v_uv;
            void main(){v_uv=a_texCoord0;gl_Position=vec4(a_position,0.0,1.0);}
            """;

    /** Renders every requested family on the current application render thread. */
    public SceneEvidence renderEvidence() {
        return renderEvidence(capabilities());
    }

    /** Renders the same scene while forcing the deterministic CPU particle fallback. */
    public SceneEvidence renderCpuFallbackEvidence() {
        return renderEvidence(new EffectCapabilities(2, 0, 1, false,
                EffectCapabilities.Profile.DESKTOP_OPENGL));
    }

    private SceneEvidence renderEvidence(EffectCapabilities capabilities) {
        List<ArtifactEvidence> artifacts = new ArrayList<>();
        artifacts.add(new ArtifactEvidence("material", renderMaterial()));
        artifacts.add(new ArtifactEvidence("trail", renderTrail()));
        artifacts.add(new ArtifactEvidence("beam", renderBeam()));
        artifacts.add(new ArtifactEvidence("lightning", renderLightning()));
        artifacts.add(new ArtifactEvidence("cpu-particles", renderParticles("cpu-particles")));
        ParticleDefinition selected = particleDefinition("selected-particles");
        ParticleBackendEvidence backend = ParticleBackendSelector.select(selected, capabilities,
                ParticleFallbackPolicy.FALLBACK_CPU, LIMITS.maxTexturePixels());
        SelectedParticleEvidence selectedEvidence = renderSelectedParticles(
                selected, capabilities, backend);
        artifacts.add(new ArtifactEvidence("selected-particles", selectedEvidence.pixels()));
        artifacts.add(new ArtifactEvidence("decal", renderDecal()));
        artifacts.add(new ArtifactEvidence("distortion", renderDistortion()));
        artifacts.add(new ArtifactEvidence("post-process", renderPostProcess()));
        ParticleImportResult imported = importParticle();
        return new SceneEvidence(artifacts, backend.backend().name(), imported.fidelity().name(),
                selectedEvidence.generation(), selectedEvidence.particleCount(),
                selectedEvidence.simulation());
    }

    /** Stable names of every visual family shown by this scene. */
    public List<String> artifactNames() {
        return List.of("material", "trail", "beam", "lightning", "cpu-particles",
                "selected-particles", "decal", "distortion", "post-process");
    }

    private static int renderMaterial() {
        return renderTarget(() -> {
            Mesh mesh = coloredQuad();
            try (Material2dRenderer renderer = new Material2dRenderer(LIMITS)) {
                renderer.render(colorMaterial("material"), mesh, GL20.GL_TRIANGLES, key -> null);
            } finally {
                mesh.dispose();
            }
        });
    }

    private static int renderTrail() {
        TrailDefinition definition = new TrailDefinition("trail", "ship",
                colorMaterial("trail-material"), curve(0.3f), gradient(),
                0.1f, 0f, 4, 1f, TrailJoin.MITER, TrailCap.BUTT,
                TrailUvMode.STRETCH, 2f);
        TrailSnapshot snapshot = new TrailSnapshot("trail", List.of(
                trailPoint(-0.8f, -0.3f, 0f), trailPoint(0f, 0.4f, 0.5f),
                trailPoint(0.8f, -0.3f, 1f)), 0L);
        return renderTarget(() -> {
            try (TrailRenderer renderer = new TrailRenderer(definition, LIMITS)) {
                renderer.render(snapshot, key -> null);
            }
        });
    }

    private static int renderBeam() {
        BeamDefinition definition = new BeamDefinition("beam", "a", "b",
                colorMaterial("beam-material"), curve(0.2f), gradient(), 2, 1f);
        BeamSnapshot snapshot = new BeamSnapshot("beam", List.of(
                new BeamSnapshot.Segment(-0.8f, 0f, 0f, 0f, 0f, 0f,
                        0.2f, 1f, 0.2f, 0.1f, 1f),
                new BeamSnapshot.Segment(0f, 0f, 0f, 0.8f, 0f, 0f,
                        0.2f, 1f, 0.2f, 0.1f, 1f)), 0f);
        return renderTarget(() -> {
            try (BeamRenderer renderer = new BeamRenderer(definition, LIMITS)) {
                renderer.render(snapshot, key -> null);
            }
        });
    }

    private static int renderLightning() {
        LightningDefinition definition = new LightningDefinition("lightning", "a", "b",
                colorMaterial("lightning-material"), curve(0.12f), gradient(),
                2, 1, 0.2f, 0.5f, 1f);
        LightningSnapshot snapshot = new LightningSnapshot("lightning", List.of(
                new LightningSnapshot.Segment(-0.8f, -0.4f, 0f, 0f, 0.4f, 0f,
                        0.12f, 0.2f, 0.7f, 1f, 1f, false),
                new LightningSnapshot.Segment(0f, 0.4f, 0f, 0.8f, -0.4f, 0f,
                        0.12f, 0.2f, 0.7f, 1f, 1f, false),
                new LightningSnapshot.Segment(0f, 0.4f, 0f, -0.2f, 0.8f, 0f,
                        0.08f, 0.2f, 0.7f, 1f, 1f, true)), 0L, 0f);
        return renderTarget(() -> {
            try (BeamRenderer renderer = new BeamRenderer(definition, LIMITS)) {
                renderer.render(snapshot, key -> null);
            }
        });
    }

    private static int renderParticles(String name) {
        ParticleDefinition definition = particleDefinition(name);
        ParticleSnapshot snapshot = new ParticleSnapshot(name, List.of(
                particle(0L, -0.35f, 0f), particle(1L, 0f, 0.35f),
                particle(2L, 0.35f, 0f)), 0L, 0L);
        return renderParticles(definition, snapshot);
    }

    private static int renderParticles(ParticleDefinition definition, ParticleSnapshot snapshot) {
        return renderTarget(() -> {
            try (ParticleRenderer renderer = new ParticleRenderer(definition, LIMITS,
                    ParticleRenderMode.SPRITE_QUADS)) {
                renderer.render(snapshot, key -> null);
            }
        });
    }

    private static SelectedParticleEvidence renderSelectedParticles(ParticleDefinition definition,
            EffectCapabilities capabilities, ParticleBackendEvidence backend) {
        if (backend.backend() == ParticleBackendEvidence.Backend.GPU_GL3) {
            try (GpuParticleInstance particles = new GpuParticleInstance(
                    definition, LIMITS, capabilities, 91L)) {
                particles.setAnchor(definition.anchorName(), 0f, 0f, 0f);
                particles.burst(3);
                particles.advance(0.1f);
                ParticleSnapshot snapshot = particles.snapshot();
                return new SelectedParticleEvidence(renderParticles(definition, snapshot),
                        particles.generation(), snapshot.particles().size(), "GPU");
            }
        }
        try (CpuParticleInstance particles = new CpuParticleInstance(definition,
                RuntimeLimits.developmentDefaults(), 91L)) {
            particles.setAnchor(new EffectAnchor(definition.anchorName(), 0f, 0f, 0f));
            particles.burst(3);
            particles.advance(0.1f);
            ParticleSnapshot snapshot = particles.snapshot();
            return new SelectedParticleEvidence(renderParticles(definition, snapshot), 0L,
                    snapshot.particles().size(), "CPU");
        }
    }

    private static int renderDecal() {
        DecalDefinition definition = new DecalDefinition("decal",
                colorMaterial("decal-material"), 2, 1f, 0f, 0.7f, 0.4f);
        DecalSnapshot snapshot = new DecalSnapshot("decal", List.of(
                new DecalSnapshot.Decal(0L, 0L, 0f, 0f, 0f,
                        0f, 0f, 1f, 20f, 0.7f, 0.4f, 0f,
                        0.9f, 0.2f, 0.1f, 1f)), 0L);
        return renderTarget(() -> {
            try (Decal2dRenderer renderer = new Decal2dRenderer(definition, LIMITS)) {
                renderer.render(snapshot, key -> null);
            }
        });
    }

    private static int renderDistortion() {
        Texture scene = texture(0x00ff00ff);
        Texture vectors = texture(0x808000ff);
        try (DistortionRenderer renderer = new DistortionRenderer(distortionDefinition(), LIMITS)) {
            PostProcessGraphResult result = renderer.render(capture(scene), capture(vectors),
                    SIZE, SIZE);
            return count(result.capture());
        } finally {
            scene.dispose();
            vectors.dispose();
        }
    }

    private static int renderPostProcess() {
        Texture scene = texture(0xff0000ff);
        Texture overlay = texture(0x0000ffff);
        try (PostProcessGraphRenderer renderer = new PostProcessGraphRenderer(graph(), LIMITS)) {
            PostProcessGraphResult result = renderer.render(Map.of(
                    "scene", capture(scene), "overlay", capture(overlay)), SIZE, SIZE);
            return count(result.capture());
        } finally {
            scene.dispose();
            overlay.dispose();
        }
    }

    private static int renderTarget(Draw draw) {
        FrameBuffer target = new FrameBuffer(Pixmap.Format.RGBA8888, SIZE, SIZE, false);
        try {
            target.begin();
            Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            draw.run();
            byte[] bytes = com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixels(
                    0, 0, SIZE, SIZE, false);
            target.end();
            int pixels = 0;
            for (int offset = 0; offset < bytes.length; offset += 4) {
                if ((bytes[offset] & 0xff) != 0 || (bytes[offset + 1] & 0xff) != 0
                        || (bytes[offset + 2] & 0xff) != 0) {
                    pixels++;
                }
            }
            return pixels;
        } finally {
            target.dispose();
        }
    }

    private static Material2dDefinition colorMaterial(String name) {
        return new Material2dDefinition(name, COLOR_SHADER, BlendMode.ADDITIVE,
                List.of(), List.of());
    }

    private static Mesh coloredQuad() {
        Mesh mesh = new Mesh(true, 6, 0,
                new VertexAttribute(Usage.Position, 2, "a_position"),
                new VertexAttribute(Usage.ColorUnpacked, 4, "a_color"),
                new VertexAttribute(Usage.TextureCoordinates, 2, "a_texCoord0"));
        mesh.setVertices(new float[] {
                -0.6f, -0.6f, 1f, 0.4f, 0.1f, 1f, 0f, 0f,
                0.6f, -0.6f, 1f, 0.4f, 0.1f, 1f, 1f, 0f,
                0.6f, 0.6f, 1f, 0.4f, 0.1f, 1f, 1f, 1f,
                -0.6f, -0.6f, 1f, 0.4f, 0.1f, 1f, 0f, 0f,
                0.6f, 0.6f, 1f, 0.4f, 0.1f, 1f, 1f, 1f,
                -0.6f, 0.6f, 1f, 0.4f, 0.1f, 1f, 0f, 1f,
        });
        return mesh;
    }

    private static ParticleDefinition particleDefinition(String name) {
        return new ParticleDefinition(name, "emitter", colorMaterial(name + "-material"),
                8, 0f, 1f, 0f, curve(0.25f), gradient(), List.of(),
                ParticleCapacityPolicy.DROP_NEWEST);
    }

    private static ParticleSnapshot.Particle particle(long id, float x, float y) {
        return new ParticleSnapshot.Particle(id, x, y, 0f, 0f, 0f, 0f,
                0f, 1f, 0.25f, 1f, 0.6f, 0.1f, 1f);
    }

    private static TrailSnapshot.Point trailPoint(float x, float y, float u) {
        return new TrailSnapshot.Point(x, y, 0f, 0f, 0.3f,
                0.2f, 0.8f, 1f, 1f, u);
    }

    private static FloatCurve curve(float value) {
        return new FloatCurve(List.of(new FloatCurve.Stop(0f, value)));
    }

    private static ColorGradient gradient() {
        return new ColorGradient(List.of(new ColorGradient.Stop(0f, 1f, 0.6f, 0.1f, 1f)));
    }

    private static EffectCapabilities capabilities() {
        com.badlogic.gdx.graphics.glutils.GLVersion version = Gdx.graphics.getGLVersion();
        EffectCapabilities.Profile profile = switch (version.getType()) {
            case OpenGL -> EffectCapabilities.Profile.DESKTOP_OPENGL;
            case GLES -> EffectCapabilities.Profile.OPENGL_ES;
            case WebGL -> EffectCapabilities.Profile.WEBGL;
            case NONE -> EffectCapabilities.Profile.DESKTOP_OPENGL;
        };
        java.nio.IntBuffer maximum = com.badlogic.gdx.utils.BufferUtils.newIntBuffer(1);
        Gdx.gl.glGetIntegerv(GL20.GL_MAX_TEXTURE_SIZE, maximum);
        return new EffectCapabilities(version.getMajorVersion(), version.getMinorVersion(),
                Math.max(1, maximum.get(0)), Gdx.gl30 != null, profile);
    }

    private static DistortionFieldDefinition distortionDefinition() {
        return new DistortionFieldDefinition("distortion",
                textureMaterial("distortion-material", List.of("scene", "vectors"),
                        "gl_FragColor=texture2D(u_scene,v_uv);"),
                "scene", "vectors", "distorted");
    }

    private static PostProcessGraphDefinition graph() {
        RenderPassDefinition copy = new RenderPassDefinition("copy",
                textureMaterial("copy-material", List.of("scene"),
                        "gl_FragColor=texture2D(u_scene,v_uv);"),
                List.of("scene"), "copied");
        RenderPassDefinition combine = new RenderPassDefinition("combine",
                textureMaterial("combine-material", List.of("copied", "overlay"),
                        "gl_FragColor=texture2D(u_copied,v_uv)+texture2D(u_overlay,v_uv);"),
                List.of("copied", "overlay"), "final");
        return new PostProcessGraphDefinition("post-process", List.of("scene", "overlay"),
                List.of(combine, copy), "final", 2);
    }

    private static Material2dDefinition textureMaterial(String name, List<String> inputs,
            String body) {
        String uniforms = inputs.stream().map(input -> "uniform sampler2D u_" + input + ";")
                .reduce("", String::concat);
        return new Material2dDefinition(name, new ShaderSource(TEXTURE_VERTEX,
                "varying vec2 v_uv;" + uniforms + "void main(){" + body + "}"),
                BlendMode.NORMAL, List.of(), inputs.stream().map(AssetKey::new).toList());
    }

    private static SceneCapture capture(Texture texture) {
        return new SceneCapture() {
            @Override public Texture colorTexture() {
                return texture;
            }

            @Override public int width() {
                return texture.getWidth();
            }

            @Override public int height() {
                return texture.getHeight();
            }
        };
    }

    private static Texture texture(int rgba) {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        try {
            pixmap.drawPixel(0, 0, rgba);
            return new Texture(pixmap);
        } finally {
            pixmap.dispose();
        }
    }

    private static ParticleImportResult importParticle() {
        String source = """
                spark
                - Count -
                min: 1
                max: 4
                - Emission -
                highMin: 4
                highMax: 4
                - Life -
                highMin: 500
                highMax: 500
                - Velocity -
                active: true
                highMin: 2
                highMax: 2
                - Unknown Extension -
                enabled: true
                - Image Paths -
                spark.png
                """;
        return new LibgdxParticleImporter(ImportLimits.developmentDefaults()).importParticle(
                source, "imported", "emitter", colorMaterial("import-material"),
                Map.of("spark.png", new AssetKey("spark_region")));
    }

    private static int count(RgbaImage image) {
        int result = 0;
        for (int pixel : image.pixels()) {
            if ((pixel & 0x00ffffff) != 0) {
                result++;
            }
        }
        return result;
    }

    /** One stable artifact name and bounded non-background pixel count. */
    public record ArtifactEvidence(String name, int nonBlackPixels) {
        public ArtifactEvidence {
            Objects.requireNonNull(name, "name");
            if (name.isBlank() || nonBlackPixels < 0 || nonBlackPixels > SIZE * SIZE) {
                throw new IllegalArgumentException("invalid showcase artifact evidence");
            }
        }
    }

    /** Immutable evidence spanning every requested family and compatibility boundary. */
    public record SceneEvidence(List<ArtifactEvidence> artifacts,
            String particleBackend, String importFidelity, long particleGeneration,
            int selectedParticleCount, String particleSimulation) {
        public SceneEvidence {
            artifacts = List.copyOf(artifacts);
            Objects.requireNonNull(particleBackend, "particleBackend");
            Objects.requireNonNull(importFidelity, "importFidelity");
            Objects.requireNonNull(particleSimulation, "particleSimulation");
            if (particleGeneration < 0L || selectedParticleCount < 0) {
                throw new IllegalArgumentException("invalid selected particle evidence");
            }
        }
    }

    private record SelectedParticleEvidence(int pixels, long generation, int particleCount,
            String simulation) {}

    @FunctionalInterface
    private interface Draw {
        void run();
    }
}
