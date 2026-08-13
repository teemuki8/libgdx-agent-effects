package io.github.teemuki8.libgdx.agent.effects.fixtures;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class EffectsFixtureSmokeTest {
    @Test
    void knownGoodShaderCompilesAndPreviewIsSelfConsistent() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Boolean> passed = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("effects-fixture-test");
        config.setWindowedMode(64, 64);
        config.setInitialVisible(false);
        config.disableAudio(true);
        config.useVsync(false);
        config.setIdleFPS(60);
        Thread main = new Thread(() -> {
            try {
                new Lwjgl3Application(new ApplicationAdapter() {
                    @Override public void create() {
                        started.countDown();
                    }

                    @Override public void render() {
                        try {
                            passed.set(EffectsFixtureApplication.runScenario());
                        } catch (Throwable thrown) {
                            failure.set(thrown);
                        }
                        Gdx.app.exit();
                    }
                }, config);
            } catch (Throwable thrown) {
                failure.set(thrown);
                started.countDown();
            }
        }, "effects-fixture-test-main");
        main.start();
        assertTrue(started.await(30, TimeUnit.SECONDS), "GL context did not start");
        main.join(30_000);
        if (failure.get() != null) {
            throw new AssertionError("fixture failed", failure.get());
        }
        assertTrue(passed.get(), "fixture scenario must pass");
    }
}
