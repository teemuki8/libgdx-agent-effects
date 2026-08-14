package io.github.teemuki8.libgdx.agent.effects.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.teemuki8.libgdx.agent.effects.showcase.GeneralVfxScene;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeneralVfxFixtureTest {
    @Test
    void everyGeneralFamilyProducesStableBoundedVisualEvidence() throws Exception {
        GdxFixtureHost.run(() -> {
            GeneralVfxScene.SceneEvidence evidence = new GeneralVfxScene().renderEvidence();
            assertEquals(List.of("material", "trail", "beam", "lightning",
                    "cpu-particles", "selected-particles", "decal", "distortion",
                    "post-process"), evidence.artifacts().stream()
                    .map(GeneralVfxScene.ArtifactEvidence::name).toList());
            for (GeneralVfxScene.ArtifactEvidence artifact : evidence.artifacts()) {
                assertTrue(artifact.nonBlackPixels() > 0, artifact.name());
                assertTrue(artifact.nonBlackPixels() <= 32 * 32, artifact.name());
            }
            assertTrue(evidence.particleBackend().equals("CPU")
                    || evidence.particleBackend().equals("GPU_GL3"));
            assertTrue(evidence.selectedParticleCount() > 0);
            if (evidence.particleBackend().equals("GPU_GL3")) {
                assertEquals(1L, evidence.particleGeneration());
            }
            assertEquals("APPROXIMATED", evidence.importFidelity());
        });
    }
}
