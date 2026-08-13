package io.github.teemuki8.libgdx.agent.effects.showcase;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import java.nio.file.Path;

/** Desktop entry point for the interactive showcase. */
public final class DesktopLauncher {

    public static void main(String[] args) {
        launch(args);
    }

    static void launch(String[] args) {
        int smokeFrames = 0;
        boolean hidden = false;
        for (int index = 0; index < args.length; index++) {
            if ("--hidden".equals(args[index])) {
                hidden = true;
            } else if ("--smoke".equals(args[index])) {
                if (index + 1 >= args.length) {
                    throw new IllegalArgumentException("--smoke requires a frame count");
                }
                smokeFrames = Integer.parseInt(args[++index]);
                if (smokeFrames <= 0) {
                    throw new IllegalArgumentException("--smoke frame count must be positive");
                }
            } else {
                throw new IllegalArgumentException("unknown argument: " + args[index]);
            }
        }

        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("libGDX Agent Effects Showcase");
        config.setWindowedMode(1180, 700);
        config.setInitialVisible(!hidden);
        config.disableAudio(true);
        config.useVsync(true);
        config.setForegroundFPS(30);
        config.setIdleFPS(15);
        new Lwjgl3Application(
            new EffectsShowcaseApplication(smokeFrames, Path.of("showcase-output")), config);
    }

    private DesktopLauncher() {}
}
