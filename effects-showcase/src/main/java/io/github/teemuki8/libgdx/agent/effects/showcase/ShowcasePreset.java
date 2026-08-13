package io.github.teemuki8.libgdx.agent.effects.showcase;

import io.github.teemuki8.libgdx.agent.effects.core.EffectDescription;
import io.github.teemuki8.libgdx.agent.effects.core.RgbaImage;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderSource;
import io.github.teemuki8.libgdx.agent.effects.core.UniformBinding;
import io.github.teemuki8.libgdx.agent.effects.core.UniformValue;
import io.github.teemuki8.libgdx.agent.effects.libgdx.DefaultVertexShader;
import java.util.List;
import java.util.Objects;

/** One immutable, bounded showcase shader declaration. */
public record ShowcasePreset(String name, String slug, Group group, String fragmentShader,
        float defaultIntensity, boolean animated) {

    public enum Group { PRACTICAL, FLASHY }

    public ShowcasePreset {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(fragmentShader, "fragmentShader");
        if (name.isBlank() || !slug.matches("[a-z0-9-]+") || fragmentShader.isBlank()) {
            throw new IllegalArgumentException("preset text must be non-blank and slug-safe");
        }
        if (!Float.isFinite(defaultIntensity) || defaultIntensity < 0f || defaultIntensity > 1f) {
            throw new IllegalArgumentException("defaultIntensity must be within [0,1]");
        }
    }

    /** Creates the public library input for this preset and source image. */
    public EffectDescription effect(RgbaImage source, float timeSeconds, float intensity) {
        Objects.requireNonNull(source, "source");
        float boundedIntensity = Math.max(0f, Math.min(1f, intensity));
        return new EffectDescription(slug,
            new ShaderSource(DefaultVertexShader.SOURCE, fragmentShader),
            List.of(
                new UniformBinding("u_source", new UniformValue.Sampler2d(source)),
                new UniformBinding("u_intensity", new UniformValue.Float(boundedIntensity))),
            source.width(), source.height(), timeSeconds);
    }
}
