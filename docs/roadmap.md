# Roadmap

## v0.1.0

The first publishable increment. Deliverables, in dependency order:

1. `effects-core` — JDK-only declarative effect/pass-graph schema, uniform and parameter model,
   bounded limits, and the structured shader-diagnostic model (errors mapped to source lines, active
   uniforms/attributes).
2. `effects-protocol` — bounded JSON commands, results, and errors over the core schema.
3. `effects-import` — bounded JDK-only Godot `canvas_item` parsing and GLSL ES translation.
4. `effects-libgdx` — render-thread shader compilation into structured diagnostics, FrameBuffer
   render-pass execution, and deterministic headless preview.
5. `effects-mcp` — closed stdio MCP tool catalog for agents, including non-persisting shader import.
6. `effects-fixtures` — real LWJGL3 qualification fixture under Xvfb.

Each increment lands with its ADR, a vertical slice through Java API, protocol, MCP, and the fixture,
and pixel-comparison evidence where applicable.

## General-VFX program

The approved implementation sequence after the importer is an explicit JDK-only runtime lifecycle,
sprite/mesh materials, trails and ribbons, beams and lightning, deterministic CPU particles, GL3
GPU particles with a disclosed GL2 CPU fallback, 2D/3D decals, distortion and bounded multipass
graphs, native libGDX particle compatibility, cross-family protocol/MCP operations, and a Wings
ship-trail example with qualification evidence. The detailed bounded contracts and verification
gates are in `docs/superpowers/plans/2026-08-14-general-vfx-program.md`.

The runtime lifecycle foundation is now implemented: applications create seeded instances with a
closed anchor set, submit bounded visual events, advance an explicit fixed step, consume immutable
snapshots, and release capacity through `close()`. It owns no loop, timer, thread, game object, or GL
resource.
