package io.github.teemuki8.libgdx.agent.effects.library;

import io.github.teemuki8.libgdx.agent.effects.core.CatalogLimits;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalogEntry;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalogVariant;
import io.github.teemuki8.libgdx.agent.effects.core.EffectFamily;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.FidelityClassification;
import io.github.teemuki8.libgdx.agent.effects.core.GeneratedShader;
import io.github.teemuki8.libgdx.agent.effects.core.Material2dDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderImportResult;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderQualificationResult;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Admits one imported shader only from successful target-specific qualification evidence. */
public final class QualifiedShaderCatalogEntry {

    /** Creates and validates one material catalog entry from immutable qualification evidence. */
    public static EffectCatalogEntry create(String id, String version,
            String displayName, String description, List<String> tags,
            String license, String provenance, String attributionUrl,
            ShaderImportResult imported, ShaderQualificationResult qualification,
            CatalogLimits catalogLimits, EffectsLimits effectsLimits) {
        Objects.requireNonNull(imported, "imported");
        Objects.requireNonNull(qualification, "qualification");
        Material2dDefinition importedMaterial = imported.material();
        if (importedMaterial == null
                || imported.fidelity() == FidelityClassification.UNSUPPORTED) {
            throw new IllegalArgumentException("imported shader is not admissible");
        }
        if (qualification.fidelity() == FidelityClassification.UNSUPPORTED
                || !qualification.diagnostic().compiled()
                || qualification.preview() == null) {
            throw new IllegalArgumentException("shader qualification is not admissible");
        }
        List<GeneratedShader> matching = imported.generatedShaders().stream()
                .filter(generated -> generated.profile() == qualification.target())
                .toList();
        if (matching.size() != 1) {
            throw new IllegalArgumentException(
                    "qualification target must have exactly one generated shader");
        }
        GeneratedShader generated = matching.getFirst();
        if (!generated.shader().equals(qualification.shader())) {
            throw new IllegalArgumentException(
                    "qualification shader does not match generated shader");
        }
        Material2dDefinition material = new Material2dDefinition(id,
                generated.shader(), importedMaterial.blendMode(),
                importedMaterial.uniforms(), importedMaterial.textures());
        String variantId = qualification.target().name().toLowerCase(Locale.ROOT)
                .replace('_', '-');
        EffectCatalogVariant variant = new EffectCatalogVariant(variantId, 0,
                material, List.of(qualification.capabilities()));
        String normalizedAttribution = attributionUrl == null || attributionUrl.isBlank()
                ? null : attributionUrl;
        return new EffectCatalogEntry(id, version, displayName, description,
                EffectFamily.MATERIAL_2D, tags, license, provenance,
                normalizedAttribution, material.textures(), List.of(variant))
                .validate(catalogLimits, effectsLimits);
    }

    private QualifiedShaderCatalogEntry() {}
}
