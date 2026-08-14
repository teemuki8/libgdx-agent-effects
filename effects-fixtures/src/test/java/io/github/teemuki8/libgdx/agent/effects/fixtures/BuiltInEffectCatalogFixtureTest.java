package io.github.teemuki8.libgdx.agent.effects.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import io.github.teemuki8.libgdx.agent.effects.core.CatalogLimits;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCapabilities;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalog;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalogMatch;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalogQuery;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCatalogSearchResult;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.library.BuiltInEffectCatalog;
import java.nio.IntBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;

class BuiltInEffectCatalogFixtureTest {
    private static final CatalogLimits CATALOG_LIMITS = CatalogLimits.developmentDefaults();
    private static final EffectsLimits EFFECTS_LIMITS = EffectsLimits.developmentDefaults();

    @Test
    void everyVisibleDesktopEntryCompilesAndRenders() throws Exception {
        GdxFixtureHost.run(() -> {
            EffectCatalog catalog = BuiltInEffectCatalog.create(
                    CATALOG_LIMITS, EFFECTS_LIMITS);
            EffectCatalogSearchResult visible = catalog.search(new EffectCatalogQuery(
                    actualDesktopCapabilities(), null, List.of(), 32));
            assertEquals(6, visible.matches().size());
            CatalogFixtureRenderer renderer = new CatalogFixtureRenderer(EFFECTS_LIMITS);
            for (EffectCatalogMatch match : visible.matches()) {
                assertTrue(renderer.render(match.variant().definition()).nonBlackPixels() > 0,
                        match.entry().id());
            }
        });
    }

    private static EffectCapabilities actualDesktopCapabilities() {
        com.badlogic.gdx.graphics.glutils.GLVersion version = Gdx.graphics.getGLVersion();
        IntBuffer maximum = com.badlogic.gdx.utils.BufferUtils.newIntBuffer(1);
        Gdx.gl.glGetIntegerv(GL20.GL_MAX_TEXTURE_SIZE, maximum);
        return new EffectCapabilities(version.getMajorVersion(), version.getMinorVersion(),
                Math.max(1, maximum.get(0)), Gdx.gl30 != null,
                EffectCapabilities.Profile.DESKTOP_OPENGL);
    }
}
