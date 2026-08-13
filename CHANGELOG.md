# Changelog

All notable changes follow Keep a Changelog structure.

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

## [Unreleased]
