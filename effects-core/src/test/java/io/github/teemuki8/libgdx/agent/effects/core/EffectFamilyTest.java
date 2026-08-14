package io.github.teemuki8.libgdx.agent.effects.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EffectFamilyTest {

    @Test
    void derivesClosedFamiliesIncludingPostProcessGraphs() {
        assertEquals(EffectFamily.TRAIL,
                EffectFamily.from(CatalogTestFixtures.trail("trail")));
        assertEquals(EffectFamily.POST_PROCESS_GRAPH,
                EffectFamily.from(CatalogTestFixtures.postProcessGraph("graph")));
    }
}
