package io.github.teemuki8.libgdx.agent.effects.protocol;

import io.github.teemuki8.libgdx.agent.effects.core.EffectDescription;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Process-local registry of declared effects, keyed by stable non-secret name. */
public final class EffectsProtocolService {

    private final Map<String, EffectDescription> effects = new LinkedHashMap<>();

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
}
