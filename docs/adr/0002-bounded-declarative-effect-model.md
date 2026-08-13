# ADR 0002: Bounded declarative effect model with structured diagnostics

**Status:** Accepted

**Date:** 2026-08-13

## Context

Today a game draws with the default-shader `SpriteBatch`, and an agent authoring a shader gets only
a raw GLSL log string back: no line-mapped errors, no headless preview, no pixel-level assertion
primitive. Raw driver logs are not a contract — their text varies by driver and GPU, they are
unbounded, and they give an agent no structured evidence to act on.

The teemuki8 stack is built on closed, bounded, declarative models: agents declare UI, gameplay,
and typed state through immutable values, and every string, collection, and result has a configured
limit with explicit truncation. Effects and shaders are the one layer missing that model.

## Decision

Effects and shaders are a closed declarative model living in the JDK-only `effects-core` module:
`EffectDescription` (a named effect with vertex+fragment GLSL source and a bounded set of uniform
bindings), `ShaderSource`, `UniformValue`, `UniformBinding`, and the hard bounds in `EffectsLimits`.
The model is immutable, validates and defensively copies on construction, and rejects unknown or
oversized input. Keeping it JDK-only means it is unit-testable with no GL context and serializable
by `effects-protocol` without pulling libGDX into the protocol module.

Structured diagnostics (`ShaderDiagnostic`) are the primary interface to shader compilation: a
compile result, severity-tagged messages with line numbers (driver-reported, best-effort mapping),
active uniforms and attributes, and a bounded info log. The raw driver log string is never the
contract — it is parsed into a bounded, typed result by `ShaderDiagnosticParser`, with truncation
and stable ordering guaranteed.

## Consequences

- Agents and tests consume deterministic, typed evidence (`ShaderDiagnostic`, `PixelComparisonResult`)
  instead of parsing driver text.
- The same model drives the Java API, the bounded JSON protocol, the closed MCP tool catalog, and
  the real-GL fixture, so semantics do not drift between access layers.
- Bounds are enforced at the model boundary: shader source length, uniform count, pass count,
  texture sizes, diagnostic log length, render size, and region count all have configured limits
  with explicit truncation or rejection.
- The render-thread layer only produces raw driver strings and pixel buffers and feeds them into
  core; it never defines the contract itself.
