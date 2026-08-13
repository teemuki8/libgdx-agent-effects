package io.github.teemuki8.libgdx.agent.effects.showcase;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Runs a test body on a hidden real LWJGL3 render thread. */
final class ShowcaseGdxHost {

    interface TestBody {
        void run() throws Exception;
    }

    static void run(TestBody body) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("effects-showcase-test");
        config.setWindowedMode(64, 64);
        config.setInitialVisible(false);
        config.disableAudio(true);
        config.useVsync(false);
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
                            body.run();
                        } catch (Throwable thrown) {
                            failure.set(thrown);
                        } finally {
                            Gdx.app.exit();
                        }
                    }
                }, config);
            } catch (Throwable thrown) {
                failure.set(thrown);
                started.countDown();
            }
        }, "effects-showcase-test-main");
        main.start();
        assertTrue(started.await(30, TimeUnit.SECONDS), "GL context did not start");
        main.join(30_000);
        assertTrue(!main.isAlive(), "showcase GL test did not stop within 30 seconds");
        if (failure.get() != null) {
            throw new AssertionError("showcase GL body failed", failure.get());
        }
    }

    private ShowcaseGdxHost() {}
}
