package io.github.teemuki8.libgdx.agent.effects.libgdx;

import io.github.teemuki8.libgdx.agent.effects.core.EffectCapabilities;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleBackendEvidence;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleModifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Pure capability and modifier-subset selector for GPU particles or deterministic CPU fallback. */
public final class ParticleBackendSelector {
    private ParticleBackendSelector() {}

    /** Selects a backend without touching GL or allocating runtime resources. */
    public static ParticleBackendEvidence select(ParticleDefinition definition,
            EffectCapabilities capabilities, ParticleFallbackPolicy fallbackPolicy,
            int maxStateTexturePixels) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(capabilities, "capabilities");
        Objects.requireNonNull(fallbackPolicy, "fallbackPolicy");
        Dimensions dimensions = dimensions(definition.capacity(), capabilities.maxTextureSize());
        int totalPixels = Math.multiplyExact(dimensions.pixels(), 2);
        if (totalPixels > maxStateTexturePixels) {
            throw new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED,
                    "GPU particle state textures exceed configured pixels");
        }
        List<String> diagnostics = new ArrayList<>();
        if (!capabilities.supportsGl3()) {
            diagnostics.add("OpenGL 3 is unavailable; selected deterministic CPU backend");
        }
        if (!capabilities.floatTextures()) {
            diagnostics.add("floating-point state textures are unavailable");
        }
        boolean unsupported = definition.modifiers().stream()
                .anyMatch(ParticleModifier.Turbulence.class::isInstance);
        if (unsupported) {
            diagnostics.add("turbulence is CPU-only");
        }
        boolean gpu = diagnostics.isEmpty();
        if (!gpu && fallbackPolicy == ParticleFallbackPolicy.REQUIRE_GPU) {
            throw new EffectsException(EffectsException.Kind.INVALID_EFFECT,
                    String.join("; ", diagnostics));
        }
        return new ParticleBackendEvidence(gpu ? ParticleBackendEvidence.Backend.GPU_GL3
                : ParticleBackendEvidence.Backend.CPU, !gpu, diagnostics,
                gpu ? totalPixels : 0);
    }

    static Dimensions dimensions(int capacity, int maxTextureSize) {
        int stateValues = Math.multiplyExact(capacity, 2);
        int width = Math.min(maxTextureSize, (int) Math.ceil(Math.sqrt(stateValues)));
        int height = (stateValues + width - 1) / width;
        if (height > maxTextureSize) {
            throw new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED,
                    "GPU particle state exceeds maximum texture dimensions");
        }
        return new Dimensions(width, height);
    }

    record Dimensions(int width, int height) {
        int pixels() {
            return Math.multiplyExact(width, height);
        }
    }
}
