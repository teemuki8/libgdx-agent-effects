package io.github.teemuki8.libgdx.agent.effects.importer.libgdx;

import io.github.teemuki8.libgdx.agent.effects.core.AssetKey;
import io.github.teemuki8.libgdx.agent.effects.core.ColorGradient;
import io.github.teemuki8.libgdx.agent.effects.core.FeatureMapping;
import io.github.teemuki8.libgdx.agent.effects.core.FidelityClassification;
import io.github.teemuki8.libgdx.agent.effects.core.FloatCurve;
import io.github.teemuki8.libgdx.agent.effects.core.ImportDiagnostic;
import io.github.teemuki8.libgdx.agent.effects.core.ImportLimits;
import io.github.teemuki8.libgdx.agent.effects.core.Material2dDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleCapacityPolicy;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleImportResult;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleModifier;
import io.github.teemuki8.libgdx.agent.effects.core.SourceSpan;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Bounded content-only importer for libGDX 2D particle text exports. */
public final class LibgdxParticleImporter {
    private static final Set<String> KNOWN_SECTIONS = Set.of(
            "Count", "Emission", "Life", "Velocity", "Gravity", "Image Paths");
    private final ImportLimits limits;

    /** Creates an importer with explicit parser and evidence bounds. */
    public LibgdxParticleImporter(ImportLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    /** Imports supplied source text and explicit source-name to registered-key mappings. */
    public ParticleImportResult importParticle(String source, String name, String anchorName,
            Material2dDefinition material, Map<String, AssetKey> assetMappings) {
        String[] lines = ParticleImportSupport.lines(source, limits);
        Map<String, Section> sections = parseSections(lines);
        List<ImportDiagnostic> diagnostics = new ArrayList<>();
        List<FeatureMapping> mappings = new ArrayList<>();
        for (Section section : sections.values()) {
            SourceSpan span = ParticleImportSupport.span(lines, section.lineIndex());
            if (!KNOWN_SECTIONS.contains(section.name())) {
                diagnostics.add(ParticleImportSupport.warning("UNKNOWN_PARTICLE_SECTION", span,
                        "Unknown libGDX particle section: " + section.name(),
                        "The section is omitted from the neutral particle definition.",
                        "Recreate this behavior with a supported closed modifier."));
            } else {
                mappings.add(new FeatureMapping(section.name(),
                        targetFeature(section.name()), true, span));
            }
        }
        int capacity = ParticleImportSupport.positiveInt(property(sections, "Count", "max"),
                "Count.max");
        float emission = ParticleImportSupport.finiteFloat(
                property(sections, "Emission", "highMax"), "Emission.highMax");
        float lifetime = ParticleImportSupport.finiteFloat(
                property(sections, "Life", "highMax"), "Life.highMax") / 1000f;
        float speed = activeValue(sections, "Velocity");
        List<ParticleModifier> modifiers = new ArrayList<>();
        if (active(sections, "Gravity")) {
            modifiers.add(new ParticleModifier.Gravity(0f,
                    activeValue(sections, "Gravity"), 0f));
        }
        List<String> images = sections.get("Image Paths").values();
        Material2dDefinition mapped = ParticleImportSupport.mappedMaterial(
                material, images, assetMappings);
        ParticleDefinition definition = new ParticleDefinition(name, anchorName, mapped,
                capacity, emission, lifetime, speed,
                new FloatCurve(List.of(new FloatCurve.Stop(0f, 0.1f))),
                new ColorGradient(List.of(new ColorGradient.Stop(0f, 1f, 1f, 1f, 1f))),
                modifiers, ParticleCapacityPolicy.DROP_NEWEST);
        ParticleImportSupport.requireEvidenceBounds(mappings, diagnostics, limits);
        FidelityClassification fidelity = diagnostics.isEmpty()
                ? FidelityClassification.STRUCTURALLY_EQUIVALENT
                : FidelityClassification.APPROXIMATED;
        return new ParticleImportResult(definition, fidelity, mappings, diagnostics);
    }

    private static Map<String, Section> parseSections(String[] lines) {
        Map<String, Section> result = new LinkedHashMap<>();
        Section current = null;
        for (int index = 1; index < lines.length; index++) {
            String line = lines[index].trim();
            if (line.startsWith("- ") && line.endsWith(" -")) {
                String name = line.substring(2, line.length() - 2);
                current = new Section(name, index, new LinkedHashMap<>(), new ArrayList<>());
                if (result.put(name, current) != null) {
                    throw new io.github.teemuki8.libgdx.agent.effects.core.EffectsException(
                            io.github.teemuki8.libgdx.agent.effects.core.EffectsException.Kind
                                    .INVALID_IMPORT,
                            "duplicate particle section: " + name);
                }
            } else if (!line.isEmpty() && current != null) {
                int separator = line.indexOf(':');
                if (separator >= 0) {
                    current.properties().put(line.substring(0, separator).trim(),
                            line.substring(separator + 1).trim());
                } else {
                    current.values().add(line);
                }
            }
        }
        for (String required : List.of("Count", "Emission", "Life", "Velocity",
                "Image Paths")) {
            if (!result.containsKey(required)) {
                throw new io.github.teemuki8.libgdx.agent.effects.core.EffectsException(
                        io.github.teemuki8.libgdx.agent.effects.core.EffectsException.Kind
                                .INVALID_IMPORT,
                        "missing particle section: " + required);
            }
        }
        return result;
    }

    private static boolean active(Map<String, Section> sections, String section) {
        Section value = sections.get(section);
        return value != null && Boolean.parseBoolean(value.properties().getOrDefault(
                "active", "false"));
    }

    private static float activeValue(Map<String, Section> sections, String section) {
        if (!active(sections, section)) {
            return 0f;
        }
        return ParticleImportSupport.finiteFloat(property(sections, section, "highMax"),
                section + ".highMax");
    }

    private static String property(Map<String, Section> sections,
            String section, String property) {
        Section item = sections.get(section);
        String result = item == null ? null : item.properties().get(property);
        if (result == null) {
            throw new io.github.teemuki8.libgdx.agent.effects.core.EffectsException(
                    io.github.teemuki8.libgdx.agent.effects.core.EffectsException.Kind.INVALID_IMPORT,
                    "missing particle field: " + section + "." + property);
        }
        return result;
    }

    private static String targetFeature(String sourceFeature) {
        return switch (sourceFeature) {
            case "Count" -> "capacity";
            case "Emission" -> "emissionRate";
            case "Life" -> "lifetimeSeconds";
            case "Velocity" -> "initialSpeed";
            case "Gravity" -> "Gravity modifier";
            case "Image Paths" -> "registered material textures";
            default -> throw new IllegalArgumentException("unknown feature");
        };
    }

    private record Section(String name, int lineIndex,
            Map<String, String> properties, List<String> values) {}
}
