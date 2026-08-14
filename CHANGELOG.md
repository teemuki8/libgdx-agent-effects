# Changelog

All notable changes follow Keep a Changelog structure.

## [Unreleased]

## [0.2.0] - 2026-08-14

### Added

- `effects-import`: bounded shader migration assistance for Godot 4 `canvas_item` shaders, with
  fidelity diagnostics, deterministic output, and real-GL qualification of translated shaders.
- `effects-core` and `effects-runtime`: immutable definitions, snapshots, and explicit lifecycle for
  sprite/mesh materials, trails and ribbons, beams and seeded lightning, CPU particles, 2D/3D
  decals, distortion fields, and bounded post-process graphs.
- `effects-libgdx`: non-owning render-thread adapters for the wider effect families and functional
  bounded GL3 ping-pong particles with a disclosed deterministic CPU fallback.
- Content-only libGDX 2D particle and Flame compatibility importers with explicit application-owned
  asset mappings.
- Closed protocol and MCP operations for importing shaders and inspecting or snapshotting the wider
  effect model; compilation remains limited to declared shader effects.
- An unpublished interactive LWJGL3 before/after showcase, cross-family native fixtures, and a
  Wings ship-trail composition example built through the public API.

### Changed

- Runtime catch-up work, public inputs, configured limits, diagnostic evidence, and backend
  selection are now explicitly bounded and conservative on unknown or non-desktop GL profiles.
- libGDX adapters restore application-owned GL state and keep all resource work on the render
  thread.
- Cross-platform CI runs native showcase tests only on Linux under Xvfb and compiles them on macOS
  and Windows.

## [0.1.0] - 2026-08-13

### Added

- `effects-core`: JDK-only declarative effect model (`ShaderSource`, closed `UniformValue` union,
  `UniformBinding`, `EffectDescription`), bounded `EffectsLimits`, typed `EffectsException`,
  packed-RGBA `RgbaImage`, structured shader diagnostics (`ShaderDiagnostic` +
  `ShaderDiagnosticParser`), and tolerance/region-mask `PixelComparer`.
- `effects-protocol`: closed bounded Jackson mapper (`EffectsJson`), request/result records, and a
  process-local `EffectsProtocolService` registry.
- `effects-libgdx`: render-thread `EffectCompiler`/`CompiledEffect`, deterministic
  `PreviewRenderer` (fullscreen-quad `FrameBuffer` capture with correct channel order), and
  `PreviewPngWriter`.
- `effects-mcp`: closed five-tool stdio MCP server (`effect_capabilities`, `effect_list`,
  `effect_compile`, `effect_preview`, `effect_compare`) with bounded framing.
- `effects-fixtures`: deterministic LWJGL3 qualification fixture under Xvfb.
