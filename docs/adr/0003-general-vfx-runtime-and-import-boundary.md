# ADR 0003: General VFX runtime and import boundary

**Status:** Accepted

**Date:** 2026-08-14

## Context

The first effects increment compiles and previews one fullscreen shader. Applications also need
materials, trails, ribbons, beams, lightning, particles, decals, distortion, and pass graphs, plus
bounded translation from external shader formats. libGDX supplies the graphics primitives but not
one immutable, deterministic, agent-facing contract for those families.

Making libGDX's mutable particle/editor formats the public model would couple protocol and import
semantics to application-oriented state. Reimplementing textures, meshes, framebuffers, batches,
or GL access would duplicate libGDX and violate the library's third-party boundary.

## Decision

The library owns high-level immutable definitions, explicitly stepped bounded visual state,
structured import diagnostics, and deterministic evidence. It continues to use unmodified libGDX
graphics primitives for rendering.

`effects-core` remains JDK-only and owns public definitions and evidence. `effects-runtime` is a
JDK-only explicit simulation layer depending on core. `effects-import` is a JDK-only parser and
translator layer depending on core. `effects-libgdx` contains all render-thread and GL behavior.
Protocol requests/results use core values, while application-provided backends wire import and GL
implementations without reversing module direction.

Applications own the loop, timing calls, gameplay and multiplayer authority, cameras, assets, GL
context, render thread, and disposal orchestration. An effect instance may retain bounded
visual-only state but never reads or changes game objects directly.

Importers accept bounded source content and application-registered asset keys. They do not resolve
includes, arbitrary filesystem paths, network resources, scripts, reflection, or class names.
Generated shaders compile and render only on the application render thread.

## Consequences

- Fullscreen effects become one family without breaking their existing API.
- CPU simulation can be deterministic and platform-neutral; GPU execution has explicit capability
  and fallback evidence.
- External formats translate into one controlled model rather than leaking engine-specific mutable
  objects through Java, JSON, or MCP.
- The project owns more high-level VFX code, but each family remains independently bounded and
  testable while libGDX continues to provide low-level graphics and resource primitives.
