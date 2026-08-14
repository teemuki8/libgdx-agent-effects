package io.github.teemuki8.libgdx.agent.effects.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.teemuki8.libgdx.agent.effects.core.FidelityClassification;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderImportRequest;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderImportResult;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderTargetProfile;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShaderImporterTest {

    @Test
    void functionalBoundaryImportsExplicitSourceContent() {
        ShaderImporter importer = request -> new ShaderImportResult(
                request.name(), null, List.of(), List.of(), List.of(), List.of(),
                FidelityClassification.UNSUPPORTED);

        ShaderImportResult result = importer.importShader(new ShaderImportRequest(
                "unsupported", "shader_type canvas_item;",
                List.of(ShaderTargetProfile.GLSL_ES_100)));

        assertEquals(FidelityClassification.UNSUPPORTED, result.fidelity());
    }
}
