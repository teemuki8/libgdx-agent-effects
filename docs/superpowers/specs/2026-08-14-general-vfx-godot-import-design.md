# General VFX Runtime and Godot Import Design

**Date:** 2026-08-14

**Status:** Approved in conversation; pending repository review

**Scope:** General bounded visual-effects runtime plus the first Godot `canvas_item` importer

## Objective

Expand `libgdx-agent-effects` from a fullscreen-effect library into a bounded general-purpose
visual-effects toolkit. Fullscreen post-processing remains supported as one effect family alongside
sprite and mesh materials, trails, ribbons, beams, lightning, CPU and GPU particles, 2D and 3D
decals, distortion fields, and multipass post-processing.

The first implementation slice adds the architectural foundation and a Godot 4 `canvas_item`
shader importer. The importer aims for the closest safely achievable rendered result, reports every
known approximation or unsupported semantic, and uses reference-image comparison rather than
successful compilation alone as visual-fidelity evidence.

## Non-goals and boundaries

- The library does not own the application loop, gameplay state, cameras, assets, input, GL context,
  render thread, or application-wide disposal.
- Visual effects never modify authoritative gameplay or multiplayer state.
- The library does not replace libGDX graphics primitives. It continues to use `ShaderProgram`,
  `Mesh`, textures, framebuffers, batches, cameras, and GL20/GL30 access.
- The public model accepts no arbitrary code execution, reflection, scripts, class names, network
  destinations, caller-selected filesystem paths, or unresolved includes.
- The first importer handles Godot 4 `canvas_item` shader source. Godot particle resources and
  emitter configurations are separate work units in the same master plan because their target
  runtime definitions must exist first.
- `STRUCTURALLY_EQUIVALENT` never means pixel-identical. Only reference-image evidence may produce
  `VISUALLY_QUALIFIED`.

## Architecture and ownership

The implementation uses cooperating modules instead of expanding the existing
`EffectDescription` into a monolithic type.

### `effects-core`

`effects-core` remains JDK-only and contains:

- immutable effect definitions;
- curves, gradients, blend modes, material state, asset keys, and target capabilities;
- bounded immutable render snapshots;
- import results, source mappings, approximation diagnostics, and fidelity classifications; and
- common configured and hard limits.

It contains no libGDX, GL, filesystem, transport, networking, or mutable simulation dependencies.

### `effects-runtime`

`effects-runtime` depends only on `effects-core` and contains explicitly stepped visual state:

- deterministic CPU particle simulation;
- trail and ribbon sample history;
- beam and lightning generation;
- decal lifetime management; and
- seeded bounded effect instances and immutable snapshot production.

An instance reads no game objects. The application supplies explicit anchors, scalar parameters,
and bounded visual events, calls `advance`, and decides when to snapshot and dispose it.

### `effects-import`

`effects-import` depends only on `effects-core` and contains:

- the bounded Godot shader lexer and parser;
- semantic analysis and feature inventory;
- Godot-to-neutral semantic translation;
- target GLSL generation; and
- stable import diagnostics and source mappings.

It performs no GL work and loads no source-selected resources.

### `effects-libgdx`

`effects-libgdx` depends on `effects-core` and `effects-runtime` and contains:

- sprite and mesh material renderers;
- trail, ribbon, beam, and lightning mesh renderers;
- 2D and 3D decal renderers;
- CPU-particle batch rendering;
- GL3 ping-pong GPU-particle simulation;
- GL2 deterministic CPU fallback rendering;
- distortion-buffer and multipass framebuffer execution; and
- render-thread compilation, deterministic previews, and pixel capture.

### Protocol, MCP, and fixtures

The existing dependency direction remains:

```text
effects-mcp -> effects-protocol -> effects-core <- effects-libgdx
                                      ^                ^
                                      |                |
                                effects-import   effects-runtime
```

Protocol requests and results use core-owned values. Application or fixture wiring supplies import
and rendering backends without making protocol depend directly on parsers or GL. `effects-fixtures`
depends on the complete stack and qualifies real rendering under LWJGL3 and Xvfb.

### Application ownership

For a Wings engine trail, Wings supplies engine position, direction, velocity, and thrust. The
effects runtime owns only the bounded visual trail instance. Wings explicitly advances it and calls
the libGDX renderer on the render thread. Wings continues to own simulation, networking, cameras,
assets, the loop, and disposal orchestration.

## Public effect families

The public model uses a closed family of definitions:

- `Material2dDefinition`
- `Material3dDefinition`
- `ParticleDefinition`
- `TrailDefinition`
- `BeamDefinition`
- `LightningDefinition`
- `DecalDefinition`
- `DistortionFieldDefinition`
- `PostProcessGraphDefinition`

Shared immutable values describe shader stages, typed parameters, gradients, curves, texture asset
keys, blend/depth/cull state, configured capability fallbacks, and hard bounds. Asset keys resolve
only through application-registered resources; they are not filesystem paths.

## Runtime lifecycle and data flow

Every stateful effect follows this lifecycle:

```text
EffectDefinition
      |
      v create(seed, capabilities)
EffectInstance
      |
      v receive bounded anchors and events
advance(fixedDelta)
      |
      v
Immutable EffectSnapshot
      |
      v on the render thread
Family-specific libGDX renderer
```

Effect instances are visual-only by default and may diverge between multiplayer clients. Supplying
identical definitions, seeds, inputs, and fixed steps produces deterministic CPU snapshots for
tests, captures, replays, and effects that deliberately need synchronization.

### Materials

2D and 3D materials contain shader stages, blend/depth/cull state, declared textures, and typed
parameters. Imported shaders attach to other effect families through these materials.

### Particles

A particle definition declares bounded emitter count and capacity, spawn rate and bursts, lifetime,
spawn shape, initial velocity, acceleration, drag, rotation, size curve, color and alpha gradients,
atlas region, blend mode, modifiers from a closed union, and material.

CPU and GPU backends consume the same definition. GPU execution reports every unsupported modifier
or approximation. Gameplay collision and arbitrary callbacks are excluded; applications submit
explicit visual events instead.

GL3 GPU simulation uses bounded ping-pong state textures and fragment passes rather than requiring
compute shaders. GL2 and unsupported devices use the deterministic CPU backend. Capability results
state which backend is active and whether its behavior or appearance differs.

### Trails and ribbons

The application supplies one or more moving anchors. The runtime stores a bounded ring of samples
according to configured time and distance thresholds. Definitions control lifetime, width curve,
color gradient, smoothing, joins, caps, texture repetition, UV scrolling, and material. This is the
primary Wings engine-trail implementation.

### Beams

A beam consumes start and end anchors and produces a bounded strip. Its definition controls width
and color curves, segmentation, pulse, scrolling texture, lifetime, and material. Continuous lasers
and short-lived projectile streaks share this family.

### Lightning

Lightning consumes endpoints and uses a seed to produce bounded displaced segments and branches.
Regeneration frequency, roughness, branch probability, glow layers, and fade are declarative and
capped.

### Decals

The implementation includes 2D world-space marks and 3D oriented-quad decals through libGDX decal
batching. Definitions control transform, size, lifetime, fade, tint, blend, ordering, and material.
Applications supply resolved placement and optional surface normals. The generic library does not
inspect or clip across arbitrary application mesh data.

### Distortion fields

Particles, sprites, trails, beams, or bounded meshes may render vectors into a distortion buffer. A
following framebuffer pass offsets samples from an application-provided scene texture. The
application explicitly supplies and owns scene capture; the library never intercepts its loop.

### Post-processing

A closed directed acyclic pass graph generalizes the current fullscreen renderer. Inputs and
outputs are declared, cycles are rejected, pass and texture dimensions are bounded, stable ordering
is guaranteed, and intermediate framebuffers are pooled and explicitly disposed.

## Shader semantic contract

Materials bind against stable semantics instead of engine-specific variable names:

- position and UV;
- vertex color;
- normalized age;
- velocity and direction;
- world and screen position;
- time and resolution; and
- source, depth, distortion, and auxiliary textures.

The Godot importer maps `canvas_item` concepts such as `UV`, `COLOR`, `TEXTURE`,
`TEXTURE_PIXEL_SIZE`, `TIME`, `SCREEN_UV`, screen texture inputs, vertex position, and common matrix
inputs into these semantics. Missing inputs cause an explicit approximation or rejection; the
translator never fabricates a silent substitute.

## Godot `canvas_item` importer

The importer targets Godot 4 shader source and uses a real lexer, parser, typed AST, and semantic
translator. Regular-expression rewriting is insufficient for nested syntax, functions, source
locations, or safe bounded translation.

### Translation pipeline

```text
Bounded source text
      |
      v
Lexer with source spans
      |
      v
Godot shader AST
      |
      v
Semantic analysis and feature inventory
      |
      v
Canvas-item semantic mapping
      |
      v
Target-neutral material definition
      |
      v
GLSL ES 100 and/or GLSL ES 300 generation
      |
      v
libGDX compilation and deterministic preview
      |
      v
Optional reference-image comparison
```

### Supported concepts

The first importer slice handles:

- `shader_type canvas_item`;
- uniforms, hints, defaults, and bounded arrays where representable;
- constants, structs, and user functions;
- `vertex()` and `fragment()` processors;
- arithmetic, branching, statically bounded source structure, swizzles, and constructors;
- texture sampling and common shader built-ins;
- representable render modes; and
- the canvas-item semantics listed in the shader semantic contract.

Godot resource references are never resolved from source paths. Texture bytes or registered asset
keys must be supplied explicitly. Features tied to Godot canvas lighting, signed-distance-field
buffers, editor-global uniforms, unknown render modes, or unavailable screen/depth data are mapped
to explicit semantic inputs where possible and otherwise approximated or rejected.

### Import result and fidelity

Every import returns an immutable bounded result containing:

- the generated material definition;
- generated shader source for each requested target profile;
- inferred parameters and required textures;
- required runtime semantics and framebuffer passes;
- source-to-generated line mappings;
- stable ordered source-located diagnostics;
- feature-by-feature mapping decisions;
- compatible target profiles;
- qualification status; and
- fidelity classification.

Fidelity classifications are:

- `STRUCTURALLY_EQUIVALENT`: every recognized semantic has a direct mapping, without a pixel claim;
- `VISUALLY_QUALIFIED`: output passed configured reference-image tolerances;
- `APPROXIMATED`: known substitutions were required;
- `UNQUALIFIED`: generation succeeded but lacks reference-image evidence; and
- `UNSUPPORTED`: safe meaningful translation was not possible.

Each approximation has a stable code, source span, explanation, expected visual impact, and
suggested application input or manual remedy.

## Visual comparison fixtures

Importer fixtures contain the original Godot shader, a deterministic texture and parameter set, a
reference PNG rendered with a documented Godot version, generated libGDX output, configured pixel
tolerances and ignored regions, and the resulting structured comparison.

Reference images are stored with provenance so ordinary verification does not require installing
or launching Godot. A separate maintainer task may deliberately regenerate them with Godot.

## Failure and security behavior

The importer rejects:

- oversized input or generated output;
- excessive tokens, nesting, functions, uniforms, arrays, loops, or statements;
- unsupported shader types;
- unresolved includes or caller-selected paths;
- malformed syntax with bounded source-located diagnostics;
- unknown protocol fields and unsupported versions;
- target profiles incapable of supplying required semantics; and
- generated pass, texture, particle, trail, geometry, or diagnostic requirements beyond limits.

Partial results are returned only when they remain valid and every limitation is explicit. The
importer never silently removes source code to make a shader compile. Raw driver logs remain
secondary evidence behind the existing bounded structured shader diagnostics.

## Verification

Every effect family requires focused coverage for:

- valid construction and defensive copying;
- invalid lifecycle and render-thread access;
- configured and hard-limit enforcement;
- deterministic ordering and seeded behavior;
- bounded state after prolonged execution;
- resource disposal and host GL-state restoration;
- unsupported-capability fallback;
- closed protocol decoding and version rejection;
- real LWJGL3 rendering under Xvfb; and
- deterministic preview and pixel comparison.

Family-specific verification covers:

- trails: capacity, resampling, joins, curves, sharp turns, and expired-point eviction;
- particles: spawn counts, bursts, lifetime, capacity pressure, CPU determinism, GPU bounds, and
  fallback reporting;
- beams and lightning: stable endpoints, segment and branch caps, seeded regeneration, and
  zero-length handling;
- decals: lifetime, transforms, ordering, blending, and batch-state restoration;
- distortion: pass ordering, required scene input, displacement bounds, and framebuffer restoration;
- post-processing: graph validation, cycle rejection, texture pooling, pass limits, and multi-input
  rendering; and
- imports: parser bounds, source maps, semantic mappings, approximation diagnostics, target
  generation, real compilation, and reference-image comparison.

Broad changes pass the repository full gate under Xvfb on Linux:

```bash
.agents/skills/libgdx-agent-effects-dev/scripts/verify.sh full
```

This wraps the clean repository checks and warning-free Javadocs and must not be replaced with the
developer's active desktop display.

## Master implementation sequence

All requested families are committed scope. Their order reflects dependencies, not an undefined
future roadmap.

1. Add the architecture ADR and module scaffolding.
2. Add shared effect definitions, capabilities, curves, gradients, materials, asset keys, fidelity
   results, and limits.
3. Implement the Godot `canvas_item` parser, translator, target GLSL generation, diagnostics,
   protocol and MCP surfaces, and reference fixtures.
4. Add explicitly stepped runtime instances and immutable snapshots.
5. Add sprite and mesh material renderers.
6. Add trails and ribbons.
7. Add beams and lightning.
8. Add deterministic CPU particles.
9. Add GL3 ping-pong GPU particles and the GL2 CPU fallback.
10. Add 2D and 3D decals.
11. Add distortion fields and the explicit scene-capture contract.
12. Add the bounded multipass post-processing graph.
13. Add native libGDX particle-format compatibility adapters.
14. Add a cross-family showcase, Wings integration example, guides, and full qualification.

The first implementation after the master plan covers steps 1 through 3. Imported materials are
initially qualified on deterministic canvas quads. Trails, particles, beams, lightning, and decals
consume the same imported materials as their planned runtimes land; the importer slice does not
claim those runtimes already exist.

## Importer-slice completion criteria

The first implementation slice is complete only when:

- bounded Godot source parses into a typed AST;
- supported `canvas_item` semantics generate bounded target GLSL;
- generated output compiles through real libGDX GL;
- at least one direct mapping and one approximation have deterministic fixtures;
- reference-image comparison produces structured fidelity evidence;
- Java API, JSON protocol, MCP catalog, fixture, guides, ADR, dependency locks, and Javadocs agree;
  and
- focused gates and the full Xvfb gate pass.
