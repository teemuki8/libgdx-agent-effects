package io.github.teemuki8.libgdx.agent.effects.importer.libgdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.teemuki8.libgdx.agent.effects.core.AssetKey;
import io.github.teemuki8.libgdx.agent.effects.core.BlendMode;
import io.github.teemuki8.libgdx.agent.effects.core.FidelityClassification;
import io.github.teemuki8.libgdx.agent.effects.core.ImportLimits;
import io.github.teemuki8.libgdx.agent.effects.core.Material2dDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleImportResult;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FlameParticleImporterTest {
    @Test
    void importsBoundedFlameTextExportAndDisclosesUnsupportedInfluencer() throws IOException {
        ParticleImportResult result = new FlameParticleImporter(
                ImportLimits.developmentDefaults()).importParticle(
                        resource("/libgdx/flame/simple.pfx"), "emitter", material(),
                        Map.of("spark-region", new AssetKey("spark_region")));
        assertEquals(8, result.definition().capacity());
        assertEquals(6f, result.definition().emissionRate());
        assertEquals(1.5f, result.definition().lifetimeSeconds());
        assertEquals(FidelityClassification.APPROXIMATED, result.fidelity());
        assertTrue(result.diagnostics().stream()
                .anyMatch(item -> item.code().equals("UNSUPPORTED_FLAME_INFLUENCER")));
    }

    private static Material2dDefinition material() {
        return new Material2dDefinition("particle",
                new ShaderSource("void main(){}", "void main(){}"),
                BlendMode.ADDITIVE, List.of(), List.of());
    }

    private static String resource(String path) throws IOException {
        try (java.io.InputStream stream = FlameParticleImporterTest.class
                .getResourceAsStream(path)) {
            return new String(java.util.Objects.requireNonNull(stream).readAllBytes(),
                    StandardCharsets.UTF_8);
        }
    }
}
