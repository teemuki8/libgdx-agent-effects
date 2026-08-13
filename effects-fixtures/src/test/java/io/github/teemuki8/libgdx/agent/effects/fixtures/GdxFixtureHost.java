package io.github.teemuki8.libgdx.agent.effects.fixtures;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Runs one test body on a real hidden LWJGL3 GL thread (headless under xvfb-run). */
final class GdxFixtureHost {
    private GdxFixtureHost() {
    }

    interface TestBody {
        void run() throws Exception;
    }

    static void run(TestBody body) throws Exception {
        runAsync(() -> {
            body.run();
            return CompletableFuture.completedFuture(null);
        });
    }

    interface AsyncTestBody {
        CompletionStage<Void> run() throws Exception;
    }

    static void runAsync(AsyncTestBody body) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
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
                    private boolean invoked;

                    @Override public void create() {
                        started.countDown();
                    }

                    @Override public void render() {
                        if (invoked) {
                            return;
                        }
                        invoked = true;
                        try {
                            body.run().whenComplete((ignored, thrown) -> {
                                if (thrown != null) {
                                    failure.compareAndSet(null, thrown);
                                }
                                Gdx.app.postRunnable(Gdx.app::exit);
                            });
                        } catch (Throwable thrown) {
                            failure.set(thrown);
                            Gdx.app.exit();
                        }
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
        assertTrue(!main.isAlive(), "fixture did not stop within 30 seconds");
        if (failure.get() != null) {
            throw new AssertionError("fixture body failed", failure.get());
        }
    }
}
