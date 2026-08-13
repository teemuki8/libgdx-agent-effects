# ADR 0001: Render-thread confinement and hand-rolled FrameBuffer passes

**Status:** Accepted

**Date:** 2026-08-13

## Context

`libgdx-agent-effects` must give a live libGDX game, a Java test, and a coding agent the same
bounded contract for compiling a fullscreen effect shader, rendering it deterministically
off-screen, and comparing framebuffer output against a reference. Rendering is the one layer of the
teemuki8 stack with no declarative or verifiable abstraction.

Two tempting shortcuts exist. The first is to offload effect rendering to a third-party effects
library (for example libGDX post-processing or a scene2d-ish effect framework). The second is to
let the library own the application loop, an `ApplicationListener`, input, assets, or GL resource
disposal. Both break the stack's "wrap, don't replace" rule: libGDX and the application remain the
owner of the render loop, the render thread, and all GL resources, and the library must never fork
or patch them.

libGDX's own GL contract is also strict: `ShaderProgram` compilation, `FrameBuffer` use, drawing,
and framebuffer capture are only valid on the application's render thread. Touching GL resources
from any other thread is undefined behavior.

## Decision

All GL work — `ShaderProgram` compilation, `FrameBuffer` render passes, drawing, and framebuffer
capture — is confined to the application's render thread. The layer never owns the loop, the
`ApplicationListener`, input, assets, or disposal.

FrameBuffer passes are hand-rolled on the libGDX primitives `ShaderProgram`, `FrameBuffer`, and
`Pixmap` rather than depending on a third-party effects library. The implementation:

- wraps libGDX primitives and exposes a bounded, explicit API over them;
- keeps every allocation and draw step visible in the code, so bounds (render size, texture
  dimensions) are enforced before any GL allocation;
- never forks, patches, or re-enters libGDX internals;
- fails fast with a typed `WRONG_THREAD` failure when a non-render thread touches the layer.

## Consequences

- No third-party effects dependency to audit, version-pin, or license; the full effect path stays
  on libGDX's own primitives.
- Render-thread confinement is an enforced, testable contract (`WRONG_THREAD`), not a convention.
- Hand-rolled passes are more code than a library call, but each step is bounded and explicit,
  which is the point: deterministic preview and pixel comparison require known geometry, known
  passes, and no hidden state.
- Later multi-pass graphs (deferred in v0.1) extend the same hand-rolled pattern; they add passes,
  not a dependency.
