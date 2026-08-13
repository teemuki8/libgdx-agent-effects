# Interactive Effects Showcase Design

## Goal

Add a polished, runnable desktop application that demonstrates real effects produced by the public
`libgdx-agent-effects` API. The application compares one deterministic built-in source scene with
the selected processed result and never implies unsupported multipass rendering or arbitrary live
game capture.

## Scope

Create an unpublished `effects-showcase` LWJGL3 application module. It depends on `effects-core`
and `effects-libgdx`, but no published module depends on it. It does not change the public Java API,
protocol, MCP schema, or Maven Central publication set.

The showcase contains six presets in stable order:

1. Damage Pulse
2. Underwater Distortion
3. Pixelation
4. CRT Display
5. Neon Edges
6. Chromatic Shockwave

The first four emphasize practical game post-processing; the last two emphasize shader art.

## Data flow and ownership

Application-owned Java code generates one deterministic 320 by 240 source scene as an immutable,
bounded `RgbaImage`. The source contains enough edges, gradients, silhouettes, and color variation
to make every preset legible.

Each preset is an immutable Java declaration containing its display name, fragment shader, default
intensity, and time behavior. Selecting a preset constructs an `EffectDescription` with:

- `DefaultVertexShader.SOURCE`;
- the preset fragment shader;
- the source image as `UniformValue.Sampler2d` bound to `u_source`;
- a bounded `u_intensity` float;
- a fixed 320 by 240 resolution; and
- the application's bounded animation time.

`PreviewRenderer` compiles and renders on the application's render thread. Its returned
`RgbaImage` becomes an application-owned libGDX texture in the processed pane. The left pane always
shows the original source texture. The application owns and disposes all display textures, fonts,
batches, shapes, and UI resources. The library continues to own no loop, window, filesystem, or
long-lived GL resources.

## Desktop interface

The window uses a dark technical-workbench layout:

- a left preset rail grouped into practical and flashy effects;
- equal source and processed preview panes with labels and resolution/status metadata;
- time and intensity controls beneath the panes;
- pause, reset, and save-PNG actions; and
- a compact status row listing active uniforms, compile state, and render-thread ownership.

Mouse interaction selects presets and adjusts sliders. Keyboard shortcuts are:

- `Space`: pause or resume time;
- `R`: reset time and intensity to the selected preset defaults;
- `S`: save the processed image.

Time advances only while running and wraps at a fixed upper bound. Pause and reset therefore
produce exactly reproducible frames. Preset selection resets time and intensity.

PNG export is application-owned and writes into `showcase-output/` beneath the launch directory.
The filename is derived from the preset's fixed slug plus a deterministic frame-time suffix. The
showcase is the only code that selects this path; no caller-controlled path reaches the library.

## Failure behavior

Preset declarations are validated against the default `EffectsLimits` before use. A compile or
render failure does not close the application: the source pane stays visible, the previous
processed image is cleared, and the structured error kind/message appears in the status area.
Input values remain finite and clamped. Texture replacement and shutdown always dispose
application-owned GL resources on the render thread.

## Verification

Tests begin with the preset catalog and source scene. They assert stable names/order, unique slugs,
finite bounded defaults, source dimensions, and deterministic source pixels.

The real Linux GL test runs under Xvfb and:

- compiles and renders all six presets through `PreviewRenderer`;
- proves every processed result differs from the source;
- proves time affects animated presets and intensity changes the selected result;
- verifies reset values and bounded time wrapping; and
- launches the desktop application in a bounded smoke mode that renders frames and exits.

The showcase module is added to settings and dependency locking, but remains absent from
`publishedModules`. The final gates are the focused showcase/Xvfb test and the repository full gate.

## Explicit boundaries

- No new public library or MCP API.
- No multipass effect graph, particle system, arbitrary scene capture, hot reload, or file picker.
- No AI-generated screenshots or synthetic marketing claims; checked evidence, if added, must be
  rendered by this application through the real library.
- No Maven Central publication of `effects-showcase`.
