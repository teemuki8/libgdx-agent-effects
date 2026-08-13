package io.github.teemuki8.libgdx.agent.effects.showcase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.RgbaImage;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShowcasePresetsTest {

    @Test
    void catalogHasStableOrderUniqueSlugsAndBoundedEffects() {
        List<ShowcasePreset> presets = ShowcasePresets.all();
        assertEquals(List.of("Damage Pulse", "Underwater Distortion", "Pixelation",
            "CRT Display", "Neon Edges", "Chromatic Shockwave"),
            presets.stream().map(ShowcasePreset::name).toList());
        assertEquals(6, new HashSet<>(presets.stream().map(ShowcasePreset::slug).toList()).size());

        RgbaImage source = BuiltInScene.create();
        for (ShowcasePreset preset : presets) {
            assertTrue(Float.isFinite(preset.defaultIntensity()));
            assertTrue(preset.defaultIntensity() >= 0f && preset.defaultIntensity() <= 1f);
            preset.effect(source, 1.25f, preset.defaultIntensity())
                .validate(EffectsLimits.developmentDefaults());
        }
    }
}
