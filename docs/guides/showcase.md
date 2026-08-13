# Interactive effects showcase

The showcase is an application-owned LWJGL3 desktop workbench for exploring effects produced by
the public library API. Launch it from the repository root:

```bash
./gradlew :effects-showcase:run
```

It displays one deterministic built-in scene beside the selected processed result. The six presets
are Damage Pulse, Underwater Distortion, Pixelation, CRT Display, Neon Edges, and Chromatic
Shockwave. The first four demonstrate practical game post-processing; the final two emphasize
stylized shader art.

## Controls

- Click a preset in the left rail to select and reset it.
- Drag the time and intensity sliders beneath the preview panes.
- Press `Space` to pause or resume animation.
- Press `R` to reset time and intensity.
- Press `S` to save the processed frame beneath `showcase-output/`.

The status row reports shader compilation or structured failure information and names the active
uniforms. A failed effect clears only the processed pane; it does not hide the source or close the
application.

## What it proves

The built-in scene is an immutable 320 by 240 `RgbaImage`. Every preset creates an
`EffectDescription` containing that image as a bounded `UniformValue.Sampler2d` plus intensity,
time, and resolution inputs. `PreviewRenderer` compiles and renders the selected shader on the
application render thread, and the application displays the returned image.

The showcase owns the loop, input, window, display textures, output directory, and disposal. It is
not published to Maven Central and does not add filesystem access, a file picker, multipass graphs,
particles, arbitrary live-game capture, or any new public library or MCP API.

## Verification

Linux qualification uses a real hidden LWJGL3 context under Xvfb:

```bash
xvfb-run -a ./gradlew :effects-showcase:test --rerun-tasks --warning-mode=fail
```

The suite renders every preset, proves output differs from the source, exercises time and
intensity, and launches the actual desktop application in a bounded smoke mode.
