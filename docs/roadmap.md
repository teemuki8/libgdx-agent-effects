# Roadmap

## v0.1.0

The first publishable increment. Deliverables, in dependency order:

1. `effects-core` — JDK-only declarative effect/pass-graph schema, uniform and parameter model,
   bounded limits, and the structured shader-diagnostic model (errors mapped to source lines, active
   uniforms/attributes).
2. `effects-protocol` — bounded JSON commands, results, and errors over the core schema.
3. `effects-libgdx` — render-thread shader compilation into structured diagnostics, FrameBuffer
   render-pass execution, and deterministic headless preview.
4. `effects-mcp` — closed stdio MCP tool catalog for agents.
5. `effects-fixtures` — real LWJGL3 qualification fixture under Xvfb.

Each increment lands with its ADR, a vertical slice through Java API, protocol, MCP, and the fixture,
and pixel-comparison evidence where applicable.

## Later

- `effects-agent-runtime` — correlate an "active pass/effect" with `agent-runtime` typed state,
  mirroring the `harness-agent-runtime` adapter.
- Hot reload of GLSL and effect sources for the development loop.
- A stock-effect set (bloom, blur, vignette) on top of the pass-graph schema.
