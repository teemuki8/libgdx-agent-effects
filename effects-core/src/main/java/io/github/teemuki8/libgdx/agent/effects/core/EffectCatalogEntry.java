package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Immutable metadata and qualified variants for one reusable effect. */
public record EffectCatalogEntry(String id, String version, String displayName,
        String description, EffectFamily family, List<String> tags,
        String license, String provenance, String attributionUrl,
        List<AssetKey> requiredAssets, List<EffectCatalogVariant> variants) {
    private static final Pattern ID = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern VERSION = Pattern.compile("[0-9]+\\.[0-9]+\\.[0-9]+");
    private static final int HARD_CAP = 1024 * 1024;

    public EffectCatalogEntry {
        requireText(id, "id");
        requireText(version, "version");
        requireText(displayName, "displayName");
        requireText(description, "description");
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(tags, "tags");
        requireText(license, "license");
        requireText(provenance, "provenance");
        if (attributionUrl != null) {
            requireText(attributionUrl, "attributionUrl");
        }
        Objects.requireNonNull(requiredAssets, "requiredAssets");
        Objects.requireNonNull(variants, "variants");
        if (tags.size() > HARD_CAP || requiredAssets.size() > HARD_CAP
                || variants.size() > HARD_CAP) {
            throw new IllegalArgumentException(
                    "catalog entry collections are outside hard bounds");
        }
        tags = List.copyOf(tags);
        requiredAssets = List.copyOf(requiredAssets);
        variants = List.copyOf(variants);
        if (!ID.matcher(id).matches() || !VERSION.matcher(version).matches()) {
            throw new IllegalArgumentException("invalid catalog entry ID or version");
        }
        requireUniqueSortedTags(tags);
        if (variants.isEmpty()) {
            throw new IllegalArgumentException("catalog entry requires a qualified variant");
        }
        requireUniqueAssets(requiredAssets);
        requireConsistentVariants(family, variants);
        requireResolvedAssets(requiredAssets, variants);
    }

    /** Validates configured metadata bounds and each normalized effect definition. */
    public EffectCatalogEntry validate(CatalogLimits catalogLimits, EffectsLimits effectsLimits) {
        Objects.requireNonNull(catalogLimits, "catalogLimits");
        Objects.requireNonNull(effectsLimits, "effectsLimits");
        if (tags.size() > catalogLimits.maxTagsPerEntry()
                || requiredAssets.size() > catalogLimits.maxTagsPerEntry()
                || variants.size() > catalogLimits.maxVariantsPerEntry()) {
            throw limit("catalog entry collections exceed configured limits");
        }
        requireConfiguredText(id, catalogLimits);
        requireConfiguredText(version, catalogLimits);
        requireConfiguredText(displayName, catalogLimits);
        requireConfiguredText(description, catalogLimits);
        requireConfiguredText(license, catalogLimits);
        requireConfiguredText(provenance, catalogLimits);
        if (attributionUrl != null) {
            requireConfiguredText(attributionUrl, catalogLimits);
        }
        for (String tag : tags) {
            requireConfiguredText(tag, catalogLimits);
        }
        for (AssetKey asset : requiredAssets) {
            requireConfiguredText(asset.value(), catalogLimits);
        }
        for (EffectCatalogVariant variant : variants) {
            requireConfiguredText(variant.id(), catalogLimits);
            if (variant.qualifiedTargets().size() > catalogLimits.maxVariantsPerEntry()) {
                throw limit("variant targets exceed configured limit");
            }
            variant.definition().validate(effectsLimits);
        }
        return this;
    }

    private static void requireUniqueSortedTags(List<String> tags) {
        Set<String> unique = new HashSet<>();
        String previous = null;
        for (String tag : tags) {
            requireText(tag, "tag");
            if (!unique.add(tag) || previous != null && previous.compareTo(tag) >= 0) {
                throw new IllegalArgumentException("catalog tags must be unique and sorted");
            }
            previous = tag;
        }
    }

    private static void requireUniqueAssets(List<AssetKey> requiredAssets) {
        if (new HashSet<>(requiredAssets).size() != requiredAssets.size()) {
            throw new IllegalArgumentException("required assets must be unique");
        }
    }

    private static void requireConsistentVariants(EffectFamily family,
            List<EffectCatalogVariant> variants) {
        Set<String> ids = new HashSet<>();
        for (EffectCatalogVariant variant : variants) {
            if (!ids.add(variant.id()) || EffectFamily.from(variant.definition()) != family) {
                throw new IllegalArgumentException(
                        "variant IDs must be unique and definitions must match the family");
            }
        }
    }

    private static void requireResolvedAssets(List<AssetKey> requiredAssets,
            List<EffectCatalogVariant> variants) {
        Set<AssetKey> declared = Set.copyOf(requiredAssets);
        for (EffectCatalogVariant variant : variants) {
            if (!declared.containsAll(definitionAssets(variant.definition()))) {
                throw new IllegalArgumentException(
                        "definition contains an unresolved logical asset");
            }
        }
    }

    private static Set<AssetKey> definitionAssets(EffectDefinition definition) {
        return switch (definition) {
            case Material2dDefinition material -> Set.copyOf(material.textures());
            case Material3dDefinition material -> Set.copyOf(material.textures());
            case TrailDefinition trail -> Set.copyOf(trail.material().textures());
            case BeamDefinition beam -> Set.copyOf(beam.material().textures());
            case LightningDefinition lightning -> Set.copyOf(lightning.material().textures());
            case ParticleDefinition particles -> Set.copyOf(particles.material().textures());
            case DecalDefinition decal -> definitionAssets(decal.material());
            case DistortionFieldDefinition distortion -> Set.copyOf(
                    distortion.material().textures());
            case PostProcessGraphDefinition graph -> graphAssets(graph);
        };
    }

    private static Set<AssetKey> graphAssets(PostProcessGraphDefinition graph) {
        Set<AssetKey> assets = new LinkedHashSet<>();
        for (String input : graph.externalInputs()) {
            assets.add(new AssetKey(input));
        }
        return Set.copyOf(assets);
    }

    private static void requireConfiguredText(String value, CatalogLimits limits) {
        if (value.length() > limits.maxTextChars()) {
            throw limit("catalog text exceeds configured limit");
        }
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.length() > HARD_CAP) {
            throw new IllegalArgumentException("catalog entry text is outside hard bounds");
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static EffectsException limit(String message) {
        return new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED, message);
    }
}
