package io.github.teemuki8.libgdx.agent.effects.fixtures;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EffectsFixtureSmokeTest {
    @Test
    void knownGoodShaderCompilesAndPreviewIsSelfConsistent() throws Exception {
        GdxFixtureHost.run(() -> assertTrue(EffectsFixtureApplication.runScenario(),
            "fixture scenario must pass"));
    }
}
