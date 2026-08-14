package io.github.teemuki8.libgdx.agent.effects.importer.libgdx;

import io.github.teemuki8.libgdx.agent.effects.core.AssetKey;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.ImportDiagnostic;
import io.github.teemuki8.libgdx.agent.effects.core.ImportDiagnosticSeverity;
import io.github.teemuki8.libgdx.agent.effects.core.ImportLimits;
import io.github.teemuki8.libgdx.agent.effects.core.Material2dDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.SourceSpan;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class ParticleImportSupport {
    private ParticleImportSupport() {}

    static String[] lines(String source, ImportLimits limits) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(limits, "limits");
        if (source.length() > limits.maxSourceChars()) {
            throw new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED,
                    "particle source exceeds configured character limit");
        }
        String[] lines = source.split("\\R", -1);
        if (lines.length > limits.maxTokens()) {
            throw new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED,
                    "particle source exceeds configured line limit");
        }
        return lines;
    }

    static Material2dDefinition mappedMaterial(Material2dDefinition material,
            List<String> sourceAssets, Map<String, AssetKey> mappings) {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(mappings, "mappings");
        List<AssetKey> keys = new ArrayList<>(sourceAssets.size());
        for (String sourceAsset : sourceAssets) {
            AssetKey key = mappings.get(sourceAsset);
            if (key == null) {
                throw new EffectsException(EffectsException.Kind.INVALID_IMPORT,
                        "missing registered asset mapping: " + sourceAsset);
            }
            keys.add(key);
        }
        return new Material2dDefinition(material.name(), material.shader(), material.blendMode(),
                material.uniforms(), keys);
    }

    static SourceSpan span(String[] lines, int lineIndex) {
        int offset = 0;
        for (int index = 0; index < lineIndex; index++) {
            offset += lines[index].length() + 1;
        }
        int length = lines[lineIndex].length();
        return new SourceSpan(lineIndex + 1, 1, lineIndex + 1, length + 1,
                offset, offset + length);
    }

    static ImportDiagnostic warning(String code, SourceSpan span,
            String message, String visualImpact, String remedy) {
        return new ImportDiagnostic(code, ImportDiagnosticSeverity.WARNING, span,
                message, visualImpact, remedy);
    }

    static void requireEvidenceBounds(List<?> mappings, List<?> diagnostics, ImportLimits limits) {
        if (mappings.size() > limits.maxFeatureMappings()
                || diagnostics.size() > limits.maxDiagnostics()) {
            throw new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED,
                    "particle import evidence exceeds configured limits");
        }
    }

    static float finiteFloat(String text, String field) {
        try {
            float value = Float.parseFloat(text.trim());
            if (!Float.isFinite(value)) {
                throw new NumberFormatException("nonfinite");
            }
            return value;
        } catch (NumberFormatException failure) {
            throw new EffectsException(EffectsException.Kind.INVALID_IMPORT,
                    "invalid numeric particle field: " + field);
        }
    }

    static int positiveInt(String text, String field) {
        float value = finiteFloat(text, field);
        if (value <= 0f || value > 1024 * 1024 || value != Math.rint(value)) {
            throw new EffectsException(EffectsException.Kind.INVALID_IMPORT,
                    "invalid integer particle field: " + field);
        }
        return (int) value;
    }
}
