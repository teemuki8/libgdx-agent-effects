package io.github.teemuki8.libgdx.agent.effects.libgdx;

import io.github.teemuki8.libgdx.agent.effects.core.EffectDescription;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.FidelityClassification;
import io.github.teemuki8.libgdx.agent.effects.core.GeneratedShader;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderImportResult;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderSemantic;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderTargetProfile;
import io.github.teemuki8.libgdx.agent.effects.core.UniformBinding;
import io.github.teemuki8.libgdx.agent.effects.core.UniformValue;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Adapts generated material evidence to the existing bounded preview contract. */
public final class ImportedMaterialAdapter {
    private final EffectsLimits limits;

    /** Creates an adapter with the same limits used by compilation and preview. */
    public ImportedMaterialAdapter(EffectsLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    /** Creates a temporary effect; it does not register or retain the imported material. */
    public EffectDescription adapt(ShaderImportResult imported, ShaderTargetProfile target,
            List<UniformBinding> suppliedBindings, int width, int height) {
        Objects.requireNonNull(imported, "imported");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(suppliedBindings, "suppliedBindings");
        if (imported.fidelity() == FidelityClassification.UNSUPPORTED
                || imported.material() == null) {
            throw new EffectsException(EffectsException.Kind.INVALID_EFFECT,
                    "unsupported import cannot be adapted");
        }
        GeneratedShader generated = imported.generatedShaders().stream()
                .filter(candidate -> candidate.profile() == target)
                .findFirst()
                .orElseThrow(() -> new EffectsException(EffectsException.Kind.INVALID_EFFECT,
                        "requested shader target was not generated"));
        Map<String, UniformBinding> bindings = new LinkedHashMap<>();
        imported.material().uniforms().forEach(binding -> bindings.put(binding.name(), binding));
        for (UniformBinding binding : suppliedBindings) {
            bindings.put(binding.name(), binding);
        }
        requireSampler(imported, bindings, ShaderSemantic.SOURCE_TEXTURE, "u_source");
        requireSampler(imported, bindings, ShaderSemantic.SCREEN_TEXTURE, "u_screenTexture");
        if (imported.requiredSemantics().contains(ShaderSemantic.SOURCE_TEXEL_SIZE)
                && !bindings.containsKey("u_sourceTexelSize")) {
            UniformBinding source = bindings.get("u_source");
            if (source != null && source.value() instanceof UniformValue.Sampler2d sampler) {
                bindings.put("u_sourceTexelSize", new UniformBinding("u_sourceTexelSize",
                        new UniformValue.Vec2(1f / sampler.image().width(),
                                1f / sampler.image().height())));
            }
        }
        return new EffectDescription(imported.name(), generated.shader(),
                List.copyOf(bindings.values()), width, height, 0f).validate(limits);
    }

    private static void requireSampler(ShaderImportResult imported,
            Map<String, UniformBinding> bindings, ShaderSemantic semantic, String name) {
        if (!imported.requiredSemantics().contains(semantic)) {
            return;
        }
        UniformBinding binding = bindings.get(name);
        if (binding == null || !(binding.value() instanceof UniformValue.Sampler2d)) {
            throw new EffectsException(EffectsException.Kind.INVALID_EFFECT,
                    "required imported sampler is not bound: " + name);
        }
    }
}
