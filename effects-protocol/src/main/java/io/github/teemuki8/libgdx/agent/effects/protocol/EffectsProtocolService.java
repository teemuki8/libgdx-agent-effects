package io.github.teemuki8.libgdx.agent.effects.protocol;

import io.github.teemuki8.libgdx.agent.effects.core.EffectDescription;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalog;
import io.github.teemuki8.libgdx.agent.effects.core.BeamDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.DecalDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.DistortionFieldDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.EffectDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.EffectFamily;
import io.github.teemuki8.libgdx.agent.effects.core.LightningDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.Material2dDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.Material3dDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.PostProcessGraphDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.TrailDefinition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Process-local registry of declared effects, keyed by stable non-secret name. */
public final class EffectsProtocolService {

    private final Map<String, EffectDescription> effects = new LinkedHashMap<>();
    private final Map<String, EffectDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, PostProcessGraphDefinition> graphs = new LinkedHashMap<>();
    private final Map<String, Results.EffectSummaryResult> summaries = new LinkedHashMap<>();
    private EffectsBackend backend;
    private EffectsImportBackend importBackend;
    private EffectCatalog catalog;

    public synchronized EffectsProtocolService declare(EffectDescription effect) {
        effects.put(effect.name(), effect);
        summaries.put(effect.name(), new Results.EffectSummaryResult(effect.name(),
                EffectFamily.LEGACY_SHADER, 1, List.of("shader", "preview")));
        return this;
    }

    /** Declares one closed immutable general-VFX definition from application code. */
    public synchronized EffectsProtocolService declareDefinition(EffectDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        definitions.put(definition.name(), definition);
        summaries.put(definition.name(), summarize(definition));
        return this;
    }

    /** Declares one immutable bounded post-process graph from application code. */
    public synchronized EffectsProtocolService declareGraph(PostProcessGraphDefinition graph) {
        Objects.requireNonNull(graph, "graph");
        graphs.put(graph.name(), graph);
        summaries.put(graph.name(), new Results.EffectSummaryResult(graph.name(),
                EffectFamily.POST_PROCESS_GRAPH, graph.passes().size(),
                List.of("acyclic", "framebufferPool=" + graph.framebufferPoolLimit())));
        return this;
    }

    public synchronized List<String> effectNames() {
        return List.copyOf(summaries.keySet());
    }

    public synchronized EffectDescription effect(String name) {
        return effects.get(name);
    }

    /** Whether any legacy or general effect family is declared under the name. */
    public synchronized boolean isDeclared(String name) {
        return summaries.containsKey(name);
    }

    /** Immutable bounded summary of a declared family, or {@code null}. */
    public synchronized Results.EffectSummaryResult effectSummary(String name) {
        return summaries.get(name);
    }

    /** Closed general effect definition declared by application code, or {@code null}. */
    public synchronized EffectDefinition definition(String name) {
        return definitions.get(name);
    }

    /** Wires the render backend used by the compile/preview/compare tools. */
    public synchronized EffectsProtocolService backend(EffectsBackend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
        return this;
    }

    /** The wired backend, or {@code null} when the tools must answer {@code NOT_AVAILABLE}. */
    public synchronized EffectsBackend backend() {
        return backend;
    }

    /** Wires the source importer independently from the render backend. */
    public synchronized EffectsProtocolService importBackend(EffectsImportBackend backend) {
        this.importBackend = Objects.requireNonNull(backend, "backend");
        return this;
    }

    /** The wired importer, or {@code null} when source import is unavailable. */
    public synchronized EffectsImportBackend importBackend() {
        return importBackend;
    }

    /** Wires the optional target-aware catalog registered by application code. */
    public synchronized EffectsProtocolService catalog(EffectCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        return this;
    }

    /** The registered catalog, or {@code null} when catalog tools are unavailable. */
    public synchronized EffectCatalog catalog() {
        return catalog;
    }

    private static Results.EffectSummaryResult summarize(EffectDefinition definition) {
        if (definition instanceof Material2dDefinition material) {
            return new Results.EffectSummaryResult(material.name(), EffectFamily.MATERIAL_2D,
                    material.textures().size(), List.of("shader", "sprite", "mesh"));
        }
        if (definition instanceof Material3dDefinition material) {
            return new Results.EffectSummaryResult(material.name(), EffectFamily.MATERIAL_3D,
                    material.textures().size(), List.of("shader", "depth", "culling"));
        }
        if (definition instanceof TrailDefinition trail) {
            return new Results.EffectSummaryResult(trail.name(), EffectFamily.TRAIL,
                    trail.pointLimit(), List.of(trail.join().name(), trail.cap().name(),
                            trail.uvMode().name()));
        }
        if (definition instanceof BeamDefinition beam) {
            return new Results.EffectSummaryResult(beam.name(), EffectFamily.BEAM,
                    beam.segmentLimit(), List.of("startAnchor", "endAnchor"));
        }
        if (definition instanceof LightningDefinition lightning) {
            return new Results.EffectSummaryResult(lightning.name(), EffectFamily.LIGHTNING,
                    lightning.segmentLimit() + lightning.branchLimit(),
                    List.of("seeded", "branches=" + lightning.branchLimit()));
        }
        if (definition instanceof ParticleDefinition particles) {
            return new Results.EffectSummaryResult(particles.name(), EffectFamily.PARTICLE,
                    particles.capacity(), List.of("cpu", "gpuFallback",
                            particles.capacityPolicy().name()));
        }
        if (definition instanceof DecalDefinition decals) {
            return new Results.EffectSummaryResult(decals.name(), EffectFamily.DECAL,
                    decals.capacity(), List.of("2dOr3d", "ordered", "lifetime"));
        }
        DistortionFieldDefinition distortion = (DistortionFieldDefinition) definition;
        return new Results.EffectSummaryResult(distortion.name(), EffectFamily.DISTORTION,
                1, List.of("sceneCapture", "vectorField", "passGraph"));
    }
}
