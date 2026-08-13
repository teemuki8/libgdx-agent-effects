package io.github.teemuki8.libgdx.agent.effects.showcase;

import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.RgbaImage;
import io.github.teemuki8.libgdx.agent.effects.libgdx.PreviewRenderer;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ShowcaseRenderingTest {

    @Test
    void allPresetsRenderAndControlsChangePixels() throws Exception {
        ShowcaseGdxHost.run(() -> {
            RgbaImage source = BuiltInScene.create();
            try (PreviewRenderer renderer =
                    new PreviewRenderer(EffectsLimits.developmentDefaults())) {
                for (ShowcasePreset preset : ShowcasePresets.all()) {
                    RgbaImage rendered = renderer.render(
                        preset.effect(source, 0.75f, preset.defaultIntensity()));
                    assertFalse(Arrays.equals(source.pixels(), rendered.pixels()), preset.name());
                }

                ShowcasePreset animated = ShowcasePresets.all().get(0);
                RgbaImage early = renderer.render(animated.effect(source, 0f, 1f));
                RgbaImage late = renderer.render(animated.effect(source, 1f, 1f));
                assertFalse(Arrays.equals(early.pixels(), late.pixels()),
                    "animated time must affect output");

                ShowcasePreset intensity = ShowcasePresets.all().get(4);
                RgbaImage low = renderer.render(intensity.effect(source, 0f, 0f));
                RgbaImage high = renderer.render(intensity.effect(source, 0f, 1f));
                assertFalse(Arrays.equals(low.pixels(), high.pixels()),
                    "intensity must affect output");
            }
        });
    }
}
