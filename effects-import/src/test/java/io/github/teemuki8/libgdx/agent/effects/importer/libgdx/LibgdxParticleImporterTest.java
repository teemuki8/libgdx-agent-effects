package io.github.teemuki8.libgdx.agent.effects.importer.libgdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.teemuki8.libgdx.agent.effects.core.AssetKey;
import io.github.teemuki8.libgdx.agent.effects.core.BlendMode;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.FidelityClassification;
import io.github.teemuki8.libgdx.agent.effects.core.ImportLimits;
import io.github.teemuki8.libgdx.agent.effects.core.Material2dDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleImportResult;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleModifier;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LibgdxParticleImporterTest {

    @Test
    void importsKnownFieldsAssetsAndReportsUnknownApproximation() throws IOException {
        String source = resource("/libgdx/particle/simple.p");
        ParticleImportResult result = new LibgdxParticleImporter(
                ImportLimits.developmentDefaults()).importParticle(source, "sparks", "emitter",
                        material(), Map.of("spark.png", new AssetKey("spark_region")));
        assertEquals(4, result.definition().capacity());
        assertEquals(4f, result.definition().emissionRate());
        assertEquals(0.5f, result.definition().lifetimeSeconds());
        assertEquals(2f, result.definition().initialSpeed());
        assertTrue(result.definition().modifiers().stream()
                .anyMatch(ParticleModifier.Gravity.class::isInstance));
        assertEquals(List.of(new AssetKey("spark_region")),
                result.definition().material().textures());
        assertEquals(FidelityClassification.APPROXIMATED, result.fidelity());
        assertTrue(result.diagnostics().stream()
                .anyMatch(item -> item.code().equals("UNKNOWN_PARTICLE_SECTION")));
    }

    @Test
    void rejectsMissingAssetMappingsAndOversizedContent() throws IOException {
        String source = resource("/libgdx/particle/simple.p");
        LibgdxParticleImporter importer = new LibgdxParticleImporter(
                ImportLimits.developmentDefaults());
        assertThrows(EffectsException.class, () -> importer.importParticle(
                source, "sparks", "emitter", material(), Map.of()));
        assertThrows(EffectsException.class, () -> importer.importParticle(
                "x".repeat(ImportLimits.developmentDefaults().maxSourceChars() + 1),
                "sparks", "emitter", material(), Map.of()));
    }

    private static Material2dDefinition material() {
        return new Material2dDefinition("particle",
                new ShaderSource("void main(){}", "void main(){}"),
                BlendMode.ADDITIVE, List.of(), List.of());
    }

    private static String resource(String path) throws IOException {
        try (java.io.InputStream stream = LibgdxParticleImporterTest.class
                .getResourceAsStream(path)) {
            return new String(java.util.Objects.requireNonNull(stream).readAllBytes(),
                    StandardCharsets.UTF_8);
        }
    }
}
