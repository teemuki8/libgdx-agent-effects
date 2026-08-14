package io.github.teemuki8.libgdx.agent.effects.protocol;

import io.github.teemuki8.libgdx.agent.effects.core.EffectCapabilities;
import io.github.teemuki8.libgdx.agent.effects.core.EffectFamily;
import io.github.teemuki8.libgdx.agent.effects.core.PixelComparisonSpec;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderTargetProfile;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/** Closed request records. */
public final class Requests {

    private Requests() {}

    public record CompileRequest(String effectName) {
        public CompileRequest {
            Objects.requireNonNull(effectName, "effectName");
        }
    }

    public record PreviewRequest(String effectName) {
        public PreviewRequest {
            Objects.requireNonNull(effectName, "effectName");
        }
    }

    public record DescribeEffectRequest(String effectName) {
        public DescribeEffectRequest {
            requireIdentifier(effectName, "effectName");
        }
    }

    public record SnapshotSummaryRequest(String effectName) {
        public SnapshotSummaryRequest {
            requireIdentifier(effectName, "effectName");
        }
    }

    public record CompareRequest(String referenceName, String actualName,
            PixelComparisonSpec spec) {
        public CompareRequest {
            Objects.requireNonNull(referenceName, "referenceName");
            Objects.requireNonNull(actualName, "actualName");
            Objects.requireNonNull(spec, "spec");
        }
    }

    public record ImportGodotCanvasRequest(
            String name, String source, List<ShaderTargetProfile> targetProfiles) {
        public ImportGodotCanvasRequest {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(source, "source");
            targetProfiles = List.copyOf(targetProfiles);
            if (name.isBlank() || name.length() > EffectsProtocol.MAX_IDENTIFIER_CHARS
                    || source.isBlank()
                    || source.length() > EffectsProtocol.MAX_SHADER_IMPORT_SOURCE_CHARS
                    || targetProfiles.isEmpty() || targetProfiles.size() > 2
                    || new HashSet<>(targetProfiles).size() != targetProfiles.size()) {
                throw new IllegalArgumentException("invalid bounded Godot import request");
            }
        }
    }

    public record ImportParticleRequest(String schemaVersion, ParticleSourceFormat format,
            String name, String source, String anchorName, String materialName,
            Map<String, String> assetMappings) {
        public ImportParticleRequest {
            Objects.requireNonNull(schemaVersion, "schemaVersion");
            Objects.requireNonNull(format, "format");
            requireIdentifier(name, "name");
            requireIdentifier(anchorName, "anchorName");
            requireIdentifier(materialName, "materialName");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(assetMappings, "assetMappings");
            assetMappings = java.util.Collections.unmodifiableMap(
                    new LinkedHashMap<>(assetMappings));
            if (!EffectsProtocol.SCHEMA_VERSION.equals(schemaVersion)
                    || source.isBlank()
                    || source.length() > EffectsProtocol.MAX_SHADER_IMPORT_SOURCE_CHARS
                    || assetMappings.size() > 256) {
                throw new IllegalArgumentException("invalid bounded particle import request");
            }
            assetMappings.forEach((sourceName, registeredName) -> {
                requireIdentifier(sourceName, "asset source name");
                requireIdentifier(registeredName, "registered asset name");
            });
        }
    }

    public record CatalogSearchRequest(EffectCapabilities target, EffectFamily family,
            List<String> tags, int limit) {
        public CatalogSearchRequest {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(tags, "tags");
            if (target.profile() == EffectCapabilities.Profile.UNKNOWN
                    || tags.size() > EffectsProtocol.MAX_CATALOG_TAGS
                    || limit <= 0 || limit > EffectsProtocol.MAX_CATALOG_RESULTS) {
                throw new IllegalArgumentException("invalid bounded catalog search request");
            }
            TreeSet<String> normalized = new TreeSet<>();
            for (String tag : tags) {
                requireIdentifier(tag, "catalog tag");
                if (!normalized.add(tag)) {
                    throw new IllegalArgumentException("duplicate catalog tag");
                }
            }
            tags = List.copyOf(normalized);
        }
    }

    public record CatalogLookupRequest(String id, EffectCapabilities target) {
        public CatalogLookupRequest {
            requireIdentifier(id, "catalog id");
            Objects.requireNonNull(target, "target");
            if (target.profile() == EffectCapabilities.Profile.UNKNOWN) {
                throw new IllegalArgumentException("catalog target profile must be explicit");
            }
        }
    }

    private static void requireIdentifier(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > EffectsProtocol.MAX_IDENTIFIER_CHARS
                || value.startsWith("/") || value.contains("..") || value.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("invalid " + name);
        }
    }
}
