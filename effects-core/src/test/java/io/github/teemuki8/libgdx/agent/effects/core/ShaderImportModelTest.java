package io.github.teemuki8.libgdx.agent.effects.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShaderImportModelTest {

    @Test
    void requestDefensivelyCopiesUniqueTargetProfiles() {
        List<ShaderTargetProfile> targets = new ArrayList<>();
        targets.add(ShaderTargetProfile.GLSL_ES_100);

        ShaderImportRequest request = new ShaderImportRequest(
                "pulse", "shader_type canvas_item;", targets);
        targets.add(ShaderTargetProfile.GLSL_ES_300);

        assertEquals(List.of(ShaderTargetProfile.GLSL_ES_100), request.targets());
        assertThrows(IllegalArgumentException.class, () -> new ShaderImportRequest(
                "pulse", "shader_type canvas_item;",
                List.of(ShaderTargetProfile.GLSL_ES_100,
                        ShaderTargetProfile.GLSL_ES_100)));
    }

    @Test
    void registeredAssetKeyCannotSelectAFilePath() {
        assertEquals("ship-glow", new AssetKey("ship-glow").value());
        assertThrows(IllegalArgumentException.class, () -> new AssetKey("/tmp/ship.png"));
        assertThrows(IllegalArgumentException.class, () -> new AssetKey("../ship.png"));
        assertThrows(IllegalArgumentException.class, () -> new AssetKey("ship\\glow"));
    }

    @Test
    void sourceSpanUsesOrderedOneBasedCoordinatesAndOffsets() {
        SourceSpan span = new SourceSpan(1, 2, 3, 4, 1, 12);

        assertEquals(1, span.startLine());
        assertThrows(IllegalArgumentException.class,
                () -> new SourceSpan(0, 1, 1, 1, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new SourceSpan(2, 1, 1, 1, 4, 3));
    }

    @Test
    void successfulImportRequiresMaterialAndBoundedGeneratedSource() {
        ImportLimits limits = new ImportLimits(
                128, 32, 64, 16, 8, 8, 8, 32, 64, 8, 8);
        SourceSpan span = new SourceSpan(1, 1, 1, 10, 0, 9);
        GeneratedShader shader = new GeneratedShader(
                ShaderTargetProfile.GLSL_ES_100,
                new ShaderSource("vertex", "fragment"),
                List.of(new SourceMapping(span, span)));
        Material2dDefinition material = new Material2dDefinition(
                "pulse", new ShaderSource("vertex", "fragment"), BlendMode.NORMAL,
                List.of(), List.of());
        List<ImportDiagnostic> diagnostics = new ArrayList<>();
        diagnostics.add(new ImportDiagnostic(
                "DIRECT_MAPPING", ImportDiagnosticSeverity.INFO, span,
                "mapped directly", "none", "none"));

        ShaderImportResult result = new ShaderImportResult(
                "pulse", material, List.of(shader), List.of(ShaderSemantic.UV),
                List.of(new FeatureMapping("UV", "v_texCoords", true, span)),
                diagnostics, FidelityClassification.STRUCTURALLY_EQUIVALENT)
                .validate(limits, EffectsLimits.developmentDefaults());
        diagnostics.clear();

        assertEquals(1, result.diagnostics().size());
        assertThrows(IllegalArgumentException.class, () -> new ShaderImportResult(
                "pulse", null, List.of(shader), List.of(), List.of(), List.of(),
                FidelityClassification.STRUCTURALLY_EQUIVALENT));
        GeneratedShader oversized = new GeneratedShader(
                ShaderTargetProfile.GLSL_ES_100,
                new ShaderSource("v".repeat(33), "fragment"), List.of());
        ShaderImportResult oversizedResult = new ShaderImportResult(
                "pulse", material, List.of(oversized), List.of(), List.of(), List.of(),
                FidelityClassification.UNQUALIFIED);
        assertThrows(EffectsException.class,
                () -> oversizedResult.validate(limits, EffectsLimits.developmentDefaults()));
    }

    @Test
    void unsupportedImportMayOmitGeneratedMaterial() {
        ShaderImportResult result = new ShaderImportResult(
                "spatial", null, List.of(), List.of(), List.of(),
                List.of(new ImportDiagnostic(
                        "UNSUPPORTED_SHADER_TYPE", ImportDiagnosticSeverity.ERROR,
                        new SourceSpan(1, 1, 1, 8, 0, 7),
                        "shader type is not canvas_item", "not renderable", "use canvas_item")),
                FidelityClassification.UNSUPPORTED);

        assertEquals(FidelityClassification.UNSUPPORTED, result.fidelity());
    }
}
