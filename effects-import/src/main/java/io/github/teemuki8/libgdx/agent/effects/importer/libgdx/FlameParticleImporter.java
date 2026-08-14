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

/** Bounded content-only importer for the documented Flame text-export subset. */
public final class FlameParticleImporter {
    private static final Set<String> KNOWN = Set.of("name", "maxParticles",
            "emissionPerSecond", "lifetimeSeconds", "initialSpeed", "gravity",
            "texture", "influencer");
    private final ImportLimits limits;

    /** Creates an importer with explicit source and evidence bounds. */
    public FlameParticleImporter(ImportLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    /** Imports supplied Flame text and explicit registered texture mappings. */
    public ParticleImportResult importParticle(String source, String anchorName,
            Material2dDefinition material, Map<String, AssetKey> assetMappings) {
        String[] lines = ParticleImportSupport.lines(source, limits);
        Map<String, Field> fields = parse(lines);
        List<FeatureMapping> mappings = new ArrayList<>();
        List<ImportDiagnostic> diagnostics = new ArrayList<>();
        for (Field field : fields.values()) {
            SourceSpan span = ParticleImportSupport.span(lines, field.lineIndex());
            if (!KNOWN.contains(field.name())) {
                diagnostics.add(ParticleImportSupport.warning("UNKNOWN_FLAME_FIELD", span,
                        "Unknown Flame field: " + field.name(),
                        "The field is omitted from the neutral particle definition.",
                        "Recreate it with a supported closed modifier."));
            } else if (field.name().equals("influencer")) {
                diagnostics.add(ParticleImportSupport.warning("UNSUPPORTED_FLAME_INFLUENCER", span,
                        "Flame influencer is not in the supported CPU/GPU modifier subset: "
                                + field.value(),
                        "Influencer-specific 3D motion is omitted.",
                        "Replace it with gravity, drag, or deterministic turbulence."));
                mappings.add(new FeatureMapping(field.name(), "diagnostic only", false, span));
            } else {
                mappings.add(new FeatureMapping(field.name(), target(field.name()), true, span));
            }
        }
        String name = required(fields, "name");
        int capacity = ParticleImportSupport.positiveInt(required(fields, "maxParticles"),
                "maxParticles");
        float emission = ParticleImportSupport.finiteFloat(
                required(fields, "emissionPerSecond"), "emissionPerSecond");
        float lifetime = ParticleImportSupport.finiteFloat(
                required(fields, "lifetimeSeconds"), "lifetimeSeconds");
        float speed = ParticleImportSupport.finiteFloat(
                required(fields, "initialSpeed"), "initialSpeed");
        String[] gravity = required(fields, "gravity").split(",", -1);
        if (gravity.length != 3) {
            throw new io.github.teemuki8.libgdx.agent.effects.core.EffectsException(
                    io.github.teemuki8.libgdx.agent.effects.core.EffectsException.Kind.INVALID_IMPORT,
                    "gravity must contain three finite components");
        }
        ParticleModifier.Gravity gravityModifier = new ParticleModifier.Gravity(
                ParticleImportSupport.finiteFloat(gravity[0], "gravity.x"),
                ParticleImportSupport.finiteFloat(gravity[1], "gravity.y"),
                ParticleImportSupport.finiteFloat(gravity[2], "gravity.z"));
        Material2dDefinition mapped = ParticleImportSupport.mappedMaterial(material,
                List.of(required(fields, "texture")), assetMappings);
        ParticleDefinition definition = new ParticleDefinition(name, anchorName, mapped,
                capacity, emission, lifetime, speed,
                new FloatCurve(List.of(new FloatCurve.Stop(0f, 0.1f))),
                new ColorGradient(List.of(new ColorGradient.Stop(0f, 1f, 1f, 1f, 1f))),
                List.of(gravityModifier), ParticleCapacityPolicy.DROP_NEWEST);
        ParticleImportSupport.requireEvidenceBounds(mappings, diagnostics, limits);
        FidelityClassification fidelity = diagnostics.isEmpty()
                ? FidelityClassification.STRUCTURALLY_EQUIVALENT
                : FidelityClassification.APPROXIMATED;
        return new ParticleImportResult(definition, fidelity, mappings, diagnostics);
    }

    private static Map<String, Field> parse(String[] lines) {
        Map<String, Field> result = new LinkedHashMap<>();
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].trim();
            if (line.isEmpty()) {
                continue;
            }
            int separator = line.indexOf(':');
            if (separator <= 0) {
                throw new io.github.teemuki8.libgdx.agent.effects.core.EffectsException(
                        io.github.teemuki8.libgdx.agent.effects.core.EffectsException.Kind.INVALID_IMPORT,
                        "Flame text fields must use name:value syntax");
            }
            String name = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (value.isEmpty() || result.put(name, new Field(name, value, index)) != null) {
                throw new io.github.teemuki8.libgdx.agent.effects.core.EffectsException(
                        io.github.teemuki8.libgdx.agent.effects.core.EffectsException.Kind.INVALID_IMPORT,
                        "empty or duplicate Flame field: " + name);
            }
        }
        return result;
    }

    private static String required(Map<String, Field> fields, String name) {
        Field field = fields.get(name);
        if (field == null) {
            throw new io.github.teemuki8.libgdx.agent.effects.core.EffectsException(
                    io.github.teemuki8.libgdx.agent.effects.core.EffectsException.Kind.INVALID_IMPORT,
                    "missing Flame field: " + name);
        }
        return field.value();
    }

    private static String target(String source) {
        return switch (source) {
            case "name" -> "definition name";
            case "maxParticles" -> "capacity";
            case "emissionPerSecond" -> "emissionRate";
            case "lifetimeSeconds" -> "lifetimeSeconds";
            case "initialSpeed" -> "initialSpeed";
            case "gravity" -> "Gravity modifier";
            case "texture" -> "registered material texture";
            default -> "diagnostic only";
        };
    }

    private record Field(String name, String value, int lineIndex) {}
}
