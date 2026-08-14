package io.github.teemuki8.libgdx.agent.effects.protocol;

import io.github.teemuki8.libgdx.agent.effects.core.EffectDescription;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Process-local registry of declared effects, keyed by stable non-secret name. */
public final class EffectsProtocolService {

    private final Map<String, EffectDescription> effects = new LinkedHashMap<>();
    private EffectsBackend backend;
    private EffectsImportBackend importBackend;

    public synchronized EffectsProtocolService declare(EffectDescription effect) {
        effects.put(effect.name(), effect);
        return this;
    }

    public synchronized List<String> effectNames() {
        return List.copyOf(effects.keySet());
    }

    public synchronized EffectDescription effect(String name) {
        return effects.get(name);
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
}
