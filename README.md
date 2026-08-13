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
None of them is a dependency of this library; this library depends on `harness-lwjgl3` only for
completed-frame synchronization and framebuffer capture, and reuses the runtime correlation pattern
rather than the runtime artifact.

## Planned scope

Planned v0.1:

- Declarative effect and render-pass-graph schema with bounded uniforms and parameters.
- Structured shader compile diagnostics mapped to source lines, with active uniforms/attributes.
- Deterministic headless preview rendering to a `FrameBuffer`, mirroring the stack's
  `--smoke N --screenshot` convention.
- Tolerance- and region-mask-aware pixel comparison over captured framebuffer output.
- A closed stdio MCP surface for agents, plus a real LWJGL3 fixture under Xvfb.

See `docs/` for the design contract, ADRs, guides, and release notes as they land.

## Modules

| Module | Purpose | Published artifact |
| --- | --- | --- |
| `effects-core` | JDK-only effect/pass-graph schema, diagnostics, bounds | `agent-effects-core` |
| `effects-protocol` | strict bounded JSON commands, results, and errors | `agent-effects-protocol` |
| `effects-libgdx` | render-thread shader compile, passes, deterministic preview | `agent-effects-libgdx` |
| `effects-mcp` | closed stdio MCP tool catalog | `agent-effects-mcp` |
| `effects-fixtures` | deterministic LWJGL3 qualification | not published |

Group: `io.github.teemuki8`. Development version: `0.1.0-SNAPSHOT`.

## Build

Linux repository verification, native LWJGL3 fixture tests, and the bundled headless MCP fixture
require `xvfb-run` (`xorg-x11-server-Xvfb` on Fedora/Nobara, `xvfb` on Debian/Ubuntu).

```bash
./gradlew clean check javadoc --warning-mode=fail
```

Use `.agents/skills/libgdx-agent-effects-dev/scripts/verify.sh full` for the official local gate.

## License

Copyright 2026 Teemu Jääskeläinen.

Licensed under the [Apache License 2.0](LICENSE). `libGDX` is used descriptively; this independent
third-party project is not affiliated with, endorsed by, or sponsored by the libGDX project.
