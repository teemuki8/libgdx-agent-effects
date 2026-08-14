# libGDX Agent Effects

`libgdx-agent-effects` is a Java 25 library for declaring, compiling, previewing, and verifying
effects and shaders in a live libGDX game, exposed to Java tests and coding agents.

It answers questions such as:

- Does this GLSL source compile, and which uniforms and attributes does it declare?
- What does a shader or render-pass graph render under a fixed camera and fixed `u_time`?
- Does the framebuffer output match an expected image within a bounded tolerance, ignoring masked
  regions?

It does not run an LLM, infer intent, or inspect arbitrary objects. The game chooses every effect,
shader source, uniform, and render pass.

## Relationship to libGDX and the teemuki8 stack

This is an ordinary third-party library. It does not fork, patch, own, or dispose libGDX.
Applications retain control of `ApplicationListener`, update, render, input, assets, and GL
resources. All GL work is confined to the application's render thread.

It is the rendering layer of the teemuki8 stack, alongside
[`libgdx-ui-markup`](https://github.com/teemuki8/libgdx-ui-markup) (declarative UI),
[`libgdx-ui-harness`](https://github.com/teemuki8/libgdx-ui-harness) (semantic UI automation),
[`libgdx-agent-gameplay`](https://github.com/teemuki8/libgdx-agent-gameplay) (fixed-tick gameplay),
and [`libgdx-agent-runtime`](https://github.com/teemuki8/libgdx-agent-runtime) (typed game state).
None of them is a dependency of this library. It is built directly on libGDX primitives
(`ShaderProgram`, `FrameBuffer`, `ScreenUtils`) with its own bounded JSON protocol and closed
stdio MCP surface.

## Capabilities

- Declarative effect model (shader source, closed uniform union, bounded effect description) in
  JDK-only `effects-core`.
- Structured shader compile diagnostics (severity-tagged, line-mapped messages; active uniforms
  and attributes) parsed from a raw GLSL log.
- Deterministic headless preview rendering a fullscreen quad to a bounded `FrameBuffer`, captured
  to a packed-RGBA image with PNG emission.
- Tolerance- and region-mask-aware pixel comparison over captured framebuffer output.
- Bounded Godot 4 `canvas_item` parsing and semantic translation to GLSL ES 100/300, with explicit
  mappings, approximation diagnostics, real-GL qualification, and no path/include resolution.
- Explicitly stepped trails/ribbons, beams, seeded lightning, deterministic CPU particles, bounded
  GL3 ping-pong particles with disclosed GL2 fallback, ordered 2D/3D decals, distortion, and stable
  multipass graphs.
- Content-only libGDX 2D particle and Flame compatibility importers using explicit registered asset
  mappings, plus a closed eleven-tool stdio MCP surface.
- An optional JDK-only, target-aware catalog with six original Apache-2.0 effects qualified for
  desktop OpenGL, and admission of imported shaders only after successful target-specific
  qualification.
- Real LWJGL3 qualification fixtures under Xvfb, including deterministic cross-family evidence.

## Modules

| Module | Purpose | Published artifact |
| --- | --- | --- |
| `effects-core` | JDK-only effect/pass-graph schema, diagnostics, bounds | `agent-effects-core` |
| `effects-library` | JDK-only qualified catalog and bundled effects | `agent-effects-library` |
| `effects-import` | JDK-only bounded engine shader importers | `agent-effects-import` |
| `effects-runtime` | JDK-only explicitly stepped bounded visual state | `agent-effects-runtime` |
| `effects-protocol` | strict bounded JSON commands, results, and errors | `agent-effects-protocol` |
| `effects-libgdx` | render-thread shader compile, passes, deterministic preview | `agent-effects-libgdx` |
| `effects-mcp` | closed stdio MCP tool catalog | `agent-effects-mcp` |
| `effects-fixtures` | deterministic LWJGL3 qualification | not published |

Group: `io.github.teemuki8`. Development version: `0.2.0-SNAPSHOT`.

## Build

Linux repository verification, native LWJGL3 fixture tests, and the bundled headless MCP fixture
require `xvfb-run` (`xorg-x11-server-Xvfb` on Fedora/Nobara, `xvfb` on Debian/Ubuntu).

```bash
./gradlew clean check javadoc --warning-mode=fail
```

Use `.agents/skills/libgdx-agent-effects-dev/scripts/verify.sh full` for the official local gate.

## Interactive showcase

Run the unpublished desktop before/after workbench to explore six real shader effects:

```bash
./gradlew :effects-showcase:run
```

The showcase generates its own deterministic source scene, sends it through the public
`EffectDescription`/`Sampler2d`/`PreviewRenderer` path, and displays source and processed images
side by side. See the [showcase guide](docs/guides/showcase.md) for controls and boundaries.

See the [Godot import guide](docs/guides/godot-import.md) for the supported `canvas_item` subset,
Java/MCP usage, fidelity meanings, and qualification boundary.
See the [general VFX guide](docs/guides/general-vfx.md) for lifecycle, ownership, renderer, fallback,
and compatibility contracts. See the [effect library guide](docs/guides/effect-library.md) for
target-aware discovery, bundled qualification limits, and imported-effect admission. The standalone
[Wings ship-trail composition](docs/examples/wings-ship-trail.java) shows how a game supplies its own
authoritative transform without introducing a Wings dependency.

Release maintainers should follow the guarded [release procedure](docs/guides/releasing.md) and
[Sonatype Central compliance contract](docs/sonatype-central-compliance.md). Maven Central
publication always requires explicit authorization.

## License

Copyright 2026 Teemu Jääskeläinen.

Licensed under the [Apache License 2.0](LICENSE). `libGDX` is used descriptively; this independent
third-party project is not affiliated with, endorsed by, or sponsored by the libGDX project.
