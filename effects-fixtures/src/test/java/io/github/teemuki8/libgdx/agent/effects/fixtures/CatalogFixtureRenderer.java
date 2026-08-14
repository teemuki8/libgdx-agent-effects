package io.github.teemuki8.libgdx.agent.effects.fixtures;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import io.github.teemuki8.libgdx.agent.effects.core.BeamDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.BeamSnapshot;
import io.github.teemuki8.libgdx.agent.effects.core.EffectDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.LightningDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.LightningSnapshot;
import io.github.teemuki8.libgdx.agent.effects.core.Material2dDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleSnapshot;
import io.github.teemuki8.libgdx.agent.effects.core.TrailDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.TrailSnapshot;
import io.github.teemuki8.libgdx.agent.effects.libgdx.BeamRenderer;
import io.github.teemuki8.libgdx.agent.effects.libgdx.Material2dRenderer;
import io.github.teemuki8.libgdx.agent.effects.libgdx.ParticleRenderMode;
import io.github.teemuki8.libgdx.agent.effects.libgdx.ParticleRenderer;
import io.github.teemuki8.libgdx.agent.effects.libgdx.TrailRenderer;
import java.util.List;
import java.util.Objects;

final class CatalogFixtureRenderer {
    private static final int SIZE = 32;
    private final EffectsLimits limits;

    CatalogFixtureRenderer(EffectsLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    RenderEvidence render(EffectDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        FrameBuffer target = new FrameBuffer(Pixmap.Format.RGBA8888, SIZE, SIZE, false);
        try {
            target.begin();
            Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            draw(definition);
            byte[] pixels = com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixels(
                    0, 0, SIZE, SIZE, false);
            target.end();
            return new RenderEvidence(count(pixels));
        } finally {
            target.dispose();
        }
    }

    private void draw(EffectDefinition definition) {
        if (definition instanceof Material2dDefinition material) {
            drawMaterial(material);
        } else if (definition instanceof TrailDefinition trail) {
            drawTrail(trail);
        } else if (definition instanceof BeamDefinition beam) {
            drawBeam(beam);
        } else if (definition instanceof LightningDefinition lightning) {
            drawLightning(lightning);
        } else if (definition instanceof ParticleDefinition particles) {
            drawParticles(particles);
        } else {
            throw new IllegalArgumentException("catalog fixture family is not supported");
        }
    }

    private void drawMaterial(Material2dDefinition definition) {
        Mesh mesh = texturedQuad();
        Texture source = texture(0x20d0ffff);
        try (Material2dRenderer renderer = new Material2dRenderer(limits)) {
            renderer.render(definition, mesh, GL20.GL_TRIANGLES,
                    key -> key.value().equals("source") ? source : null);
        } finally {
            source.dispose();
            mesh.dispose();
        }
    }

    private void drawTrail(TrailDefinition definition) {
        TrailSnapshot snapshot = new TrailSnapshot(definition.name(), List.of(
                trailPoint(-0.8f, -0.3f, 0f), trailPoint(0f, 0.4f, 0.5f),
                trailPoint(0.8f, -0.3f, 1f)), 0L);
        try (TrailRenderer renderer = new TrailRenderer(definition, limits)) {
            renderer.render(snapshot, key -> null);
        }
    }

    private void drawBeam(BeamDefinition definition) {
        BeamSnapshot snapshot = new BeamSnapshot(definition.name(), List.of(
                new BeamSnapshot.Segment(-0.8f, 0f, 0f, 0f, 0f, 0f,
                        0.2f, 1f, 0.6f, 0.1f, 1f),
                new BeamSnapshot.Segment(0f, 0f, 0f, 0.8f, 0f, 0f,
                        0.2f, 1f, 0.6f, 0.1f, 1f)), 0f);
        try (BeamRenderer renderer = new BeamRenderer(definition, limits)) {
            renderer.render(snapshot, key -> null);
        }
    }

    private void drawLightning(LightningDefinition definition) {
        LightningSnapshot snapshot = new LightningSnapshot(definition.name(), List.of(
                new LightningSnapshot.Segment(-0.8f, -0.4f, 0f, 0f, 0.4f, 0f,
                        0.12f, 0.2f, 0.7f, 1f, 1f, false),
                new LightningSnapshot.Segment(0f, 0.4f, 0f, 0.8f, -0.4f, 0f,
                        0.12f, 0.2f, 0.7f, 1f, 1f, false)), 0L, 0f);
        try (BeamRenderer renderer = new BeamRenderer(definition, limits)) {
            renderer.render(snapshot, key -> null);
        }
    }

    private void drawParticles(ParticleDefinition definition) {
        ParticleSnapshot snapshot = new ParticleSnapshot(definition.name(), List.of(
                particle(0L, -0.35f, 0f), particle(1L, 0f, 0.35f),
                particle(2L, 0.35f, 0f)), 0L, 0L);
        try (ParticleRenderer renderer = new ParticleRenderer(definition, limits,
                ParticleRenderMode.SPRITE_QUADS)) {
            renderer.render(snapshot, key -> null);
        }
    }

    private static Mesh texturedQuad() {
        Mesh mesh = new Mesh(true, 6, 0,
                new VertexAttribute(Usage.Position, 2, "a_position"),
                new VertexAttribute(Usage.TextureCoordinates, 2, "a_texCoord0"));
        mesh.setVertices(new float[] {
                -1f, -1f, 0f, 0f, 1f, -1f, 1f, 0f, 1f, 1f, 1f, 1f,
                -1f, -1f, 0f, 0f, 1f, 1f, 1f, 1f, -1f, 1f, 0f, 1f,
        });
        return mesh;
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

    private static TrailSnapshot.Point trailPoint(float x, float y, float u) {
        return new TrailSnapshot.Point(x, y, 0f, 0f, 0.3f,
                0.2f, 0.8f, 1f, 1f, u);
    }

    private static ParticleSnapshot.Particle particle(long id, float x, float y) {
        return new ParticleSnapshot.Particle(id, x, y, 0f, 0f, 0f, 0f,
                0f, 1f, 0.25f, 1f, 0.6f, 0.1f, 1f);
    }

    private static int count(byte[] pixels) {
        int result = 0;
        for (int offset = 0; offset < pixels.length; offset += 4) {
            if ((pixels[offset] & 0xff) != 0 || (pixels[offset + 1] & 0xff) != 0
                    || (pixels[offset + 2] & 0xff) != 0) {
                result++;
            }
        }
        return result;
    }

    record RenderEvidence(int nonBlackPixels) {
        RenderEvidence {
            if (nonBlackPixels < 0 || nonBlackPixels > SIZE * SIZE) {
                throw new IllegalArgumentException("invalid catalog render evidence");
            }
        }
    }
}
