package io.github.teemuki8.libgdx.agent.effects.protocol;

import io.github.teemuki8.libgdx.agent.effects.core.PixelComparisonResult;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderDiagnostic;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderImportResult;
import io.github.teemuki8.libgdx.agent.effects.core.FidelityClassification;
import io.github.teemuki8.libgdx.agent.effects.core.EffectFamily;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalogMatch;
import io.github.teemuki8.libgdx.agent.effects.core.ImportDiagnostic;
import java.util.List;
import java.util.Objects;

/** Closed result records. */
public final class Results {

    private Results() {}

    public record CompileResult(String effectName, ShaderDiagnostic diagnostic) {
        public CompileResult {
            Objects.requireNonNull(effectName, "effectName");
            Objects.requireNonNull(diagnostic, "diagnostic");
        }
    }

    public record PreviewResult(String effectName, String artifactRef, int width, int height) {
        public PreviewResult {
            Objects.requireNonNull(effectName, "effectName");
            Objects.requireNonNull(artifactRef, "artifactRef");
        }
    }

    public record CompareResult(PixelComparisonResult result) {
        public CompareResult {
            Objects.requireNonNull(result, "result");
        }
    }

    public record ListResult(List<String> effectNames) {
        public ListResult {
            effectNames = List.copyOf(effectNames);
        }
    }

    public record CapabilitiesResult(List<String> tools) {
        public CapabilitiesResult {
            tools = List.copyOf(tools);
        }
    }

    public record ImportShaderResult(ShaderImportResult result) {
        public ImportShaderResult {
            Objects.requireNonNull(result, "result");
        }
    }

    public record EffectSummaryResult(String name, EffectFamily family,
            int capacity, List<String> features) {
        public EffectSummaryResult {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(family, "family");
            features = List.copyOf(features);
            if (name.isBlank() || capacity < 0 || features.size() > 64) {
                throw new IllegalArgumentException("invalid bounded effect summary");
            }
        }
    }

    public record SnapshotSummaryResult(String effectName, EffectFamily family,
            long stepOrGeneration, int elementCount, long droppedOrEvicted,
            List<String> evidence) {
        public SnapshotSummaryResult {
            Objects.requireNonNull(effectName, "effectName");
            Objects.requireNonNull(family, "family");
            evidence = List.copyOf(evidence);
            if (effectName.isBlank() || stepOrGeneration < 0L || elementCount < 0
                    || droppedOrEvicted < 0L || evidence.size() > 64) {
                throw new IllegalArgumentException("invalid bounded snapshot summary");
            }
        }
    }

    public record ImportParticleResult(String name, FidelityClassification fidelity,
            int capacity, List<ImportDiagnostic> diagnostics) {
        public ImportParticleResult {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(fidelity, "fidelity");
            diagnostics = List.copyOf(diagnostics);
            if (name.isBlank() || capacity <= 0 || diagnostics.size() > 256) {
                throw new IllegalArgumentException("invalid bounded particle import result");
            }
        }
    }

    public record CatalogSearchResult(List<EffectCatalogMatch> matches, boolean truncated) {
        public CatalogSearchResult {
            Objects.requireNonNull(matches, "matches");
            if (matches.size() > EffectsProtocol.MAX_CATALOG_RESULTS) {
                throw new IllegalArgumentException("too many catalog matches");
            }
            matches = List.copyOf(matches);
        }
    }

    public record CatalogLookupResult(EffectCatalogMatch match) {
        public CatalogLookupResult {
            Objects.requireNonNull(match, "match");
        }
    }
}
