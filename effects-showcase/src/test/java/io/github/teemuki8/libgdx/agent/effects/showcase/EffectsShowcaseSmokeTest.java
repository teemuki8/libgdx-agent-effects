package io.github.teemuki8.libgdx.agent.effects.showcase;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class EffectsShowcaseSmokeTest {

    @Test
    void actualDesktopApplicationRendersBoundedFramesAndExits() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread desktop = new Thread(() -> {
            try {
                DesktopLauncher.launch(new String[] {"--smoke", "4", "--hidden"});
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        }, "effects-showcase-smoke-main");
        desktop.start();
        desktop.join(30_000);

        assertFalse(desktop.isAlive(), "showcase smoke launch did not stop within 30 seconds");
        assertNull(failure.get(), "showcase smoke launch failed");
    }
}
