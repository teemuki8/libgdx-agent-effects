package io.github.teemuki8.libgdx.agent.effects.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.teemuki8.libgdx.agent.effects.core.ActiveAttribute;
import io.github.teemuki8.libgdx.agent.effects.core.ActiveUniform;
import io.github.teemuki8.libgdx.agent.effects.core.AssetKey;
import io.github.teemuki8.libgdx.agent.effects.core.BlendMode;
import io.github.teemuki8.libgdx.agent.effects.core.CatalogLimits;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCapabilities;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalogEntry;
import io.github.teemuki8.libgdx.agent.effects.core.EffectFamily;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.FidelityClassification;
import io.github.teemuki8.libgdx.agent.effects.core.GeneratedShader;
import io.github.teemuki8.libgdx.agent.effects.core.Material2dDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.RgbaImage;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderDiagnostic;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderImportResult;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderQualificationResult;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderSource;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderTargetProfile;
import io.github.teemuki8.libgdx.agent.effects.core.UniformBinding;
import io.github.teemuki8.libgdx.agent.effects.core.UniformValue;
import java.util.List;
import org.junit.jupiter.api.Test;

class QualifiedShaderCatalogEntryTest {
    private static final CatalogLimits CATALOG_LIMITS = CatalogLimits.developmentDefaults();
    private static final EffectsLimits EFFECTS_LIMITS = EffectsLimits.developmentDefaults();

    @Test
    void createsEntryOnlyFromCompiledRenderedMatchingTarget() {
        EffectCatalogEntry entry = create(importedShader(), successfulQualification());

        assertEquals(EffectFamily.MATERIAL_2D, entry.family());
        assertTrue(entry.variants().get(0).supports(desktopGl2()));
        assertEquals("glsl-es-100", entry.variants().get(0).id());
        Material2dDefinition imported = importedShader().material();
        Material2dDefinition admitted = (Material2dDefinition) entry.variants().get(0).definition();
        assertEquals("water-ripple", admitted.name());
        assertEquals(generated(ShaderTargetProfile.GLSL_ES_100).shader(), admitted.shader());
        assertEquals(imported.blendMode(), admitted.blendMode());
        assertEquals(imported.uniforms(), admitted.uniforms());
        assertEquals(imported.textures(), admitted.textures());
        assertEquals(imported.textures(), entry.requiredAssets());
        assertNull(entry.attributionUrl());
    }

    @Test
    void admitsApproximatedImportAndUnqualifiedRenderedPreview() {
        ShaderImportResult approximated = imported(
                FidelityClassification.APPROXIMATED,
                List.of(generated(ShaderTargetProfile.GLSL_ES_100)));
        ShaderQualificationResult renderedWithoutReference = qualification(
                ShaderTargetProfile.GLSL_ES_100, compiled(), preview(),
                FidelityClassification.UNQUALIFIED);

        assertEquals(EffectFamily.MATERIAL_2D,
                create(approximated, renderedWithoutReference).family());
    }

    @Test
    void rejectsUnsupportedImportOrQualification() {
        ShaderImportResult unsupported = imported(
                FidelityClassification.UNSUPPORTED,
                List.of(generated(ShaderTargetProfile.GLSL_ES_100)));
        ShaderQualificationResult unsupportedQualification = qualification(
                ShaderTargetProfile.GLSL_ES_100, compiled(), preview(),
                FidelityClassification.UNSUPPORTED);

        assertThrows(IllegalArgumentException.class,
                () -> create(unsupported, successfulQualification()));
        assertThrows(IllegalArgumentException.class,
                () -> create(importedShader(), unsupportedQualification));
    }

    @Test
    void rejectsUncompiledOrPreviewlessQualification() {
        ShaderQualificationResult failed = qualification(
                ShaderTargetProfile.GLSL_ES_100, uncompiled(), null,
                FidelityClassification.UNQUALIFIED);
        ShaderQualificationResult previewless = qualification(
                ShaderTargetProfile.GLSL_ES_100, compiled(), null,
                FidelityClassification.UNQUALIFIED);

        assertThrows(IllegalArgumentException.class,
                () -> create(importedShader(), failed));
        assertThrows(IllegalArgumentException.class,
                () -> create(importedShader(), previewless));
    }

    @Test
    void rejectsMissingOrDuplicateMatchingGeneratedTargets() {
        ShaderImportResult missing = imported(FidelityClassification.APPROXIMATED,
                List.of(generated(ShaderTargetProfile.GLSL_ES_300)));
        ShaderImportResult duplicate = imported(FidelityClassification.APPROXIMATED,
                List.of(generated(ShaderTargetProfile.GLSL_ES_100),
                        generated(ShaderTargetProfile.GLSL_ES_100)));

        assertThrows(IllegalArgumentException.class,
                () -> create(missing, successfulQualification()));
        assertThrows(IllegalArgumentException.class,
                () -> create(duplicate, successfulQualification()));
    }

    @Test
    void rejectsQualificationBoundToDifferentGeneratedShader() {
        ShaderQualificationResult differentShader = new ShaderQualificationResult(
                ShaderTargetProfile.GLSL_ES_100,
                new ShaderSource("different vertex", "different fragment"), desktopGl2(),
                compiled(), preview(), null, FidelityClassification.UNQUALIFIED);

        assertThrows(IllegalArgumentException.class,
                () -> create(importedShader(), differentShader));
    }

    @Test
    void rejectsUnknownObservedCapabilities() {
        EffectCapabilities unknown = new EffectCapabilities(2, 0, 2048, false,
                EffectCapabilities.Profile.UNKNOWN);

        assertThrows(IllegalArgumentException.class,
                () -> new ShaderQualificationResult(ShaderTargetProfile.GLSL_ES_100,
                        generated(ShaderTargetProfile.GLSL_ES_100).shader(), unknown,
                        compiled(), preview(), null, FidelityClassification.UNQUALIFIED));
    }

    private static EffectCatalogEntry create(ShaderImportResult imported,
            ShaderQualificationResult qualification) {
        return QualifiedShaderCatalogEntry.create(
                "water-ripple", "1.0.0", "Water Ripple", "Imported water material",
                List.of("water"), "MIT", "Example author", " ",
                imported, qualification, CATALOG_LIMITS, EFFECTS_LIMITS);
    }

    private static ShaderImportResult importedShader() {
        return imported(FidelityClassification.APPROXIMATED,
                List.of(generated(ShaderTargetProfile.GLSL_ES_100)));
    }

    private static ShaderImportResult imported(FidelityClassification fidelity,
            List<GeneratedShader> generated) {
        Material2dDefinition material = new Material2dDefinition("imported-water",
                new ShaderSource("original vertex", "original fragment"),
                BlendMode.ADDITIVE,
                List.of(new UniformBinding("u_tint", new UniformValue.Float(0.75f))),
                List.of(new AssetKey("source")));
        return new ShaderImportResult("imported-water", material, generated,
                List.of(), List.of(), List.of(), fidelity);
    }

    private static GeneratedShader generated(ShaderTargetProfile target) {
        return new GeneratedShader(target,
                new ShaderSource("generated vertex " + target,
                        "generated fragment " + target), List.of());
    }

    private static ShaderQualificationResult successfulQualification() {
        return qualification(ShaderTargetProfile.GLSL_ES_100, compiled(), preview(),
                FidelityClassification.UNQUALIFIED);
    }

    private static ShaderQualificationResult qualification(ShaderTargetProfile target,
            ShaderDiagnostic diagnostic, RgbaImage preview, FidelityClassification fidelity) {
        return new ShaderQualificationResult(target, generated(target).shader(), desktopGl2(),
                diagnostic, preview, null, fidelity);
    }

    private static ShaderDiagnostic compiled() {
        return new ShaderDiagnostic(true, List.of(),
                List.<ActiveUniform>of(), List.<ActiveAttribute>of(), "compiled");
    }

    private static ShaderDiagnostic uncompiled() {
        return new ShaderDiagnostic(false, List.of(), List.of(), List.of(), "failed");
    }

    private static RgbaImage preview() {
        return RgbaImage.solid(2, 2, 0xff00ffff);
    }

    private static EffectCapabilities desktopGl2() {
        return new EffectCapabilities(2, 0, 2048, false,
                EffectCapabilities.Profile.DESKTOP_OPENGL);
    }
}
