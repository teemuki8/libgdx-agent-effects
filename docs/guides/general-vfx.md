# General VFX guide

The general-VFX API adds high-level, bounded visual state while leaving libGDX and the application
in control of graphics primitives and orchestration. Applications own their loop, render thread,
cameras, batches, textures, models, scene captures, gameplay state, and disposal order.

## Families

- `Material2dDefinition` and `Material3dDefinition` describe registered shaders, uniforms, textures,
  blend, depth, and culling policies.
- `TrailDefinition` plus `TrailInstance` samples a named anchor into an oldest-to-newest bounded
  ribbon. `TrailRenderer` owns only its generated mesh.
- `BeamInstance` and `LightningInstance` produce bounded segment snapshots. Lightning uses a
  repository-stable seeded generator and changes only at its declared regeneration interval.
- `CpuParticleInstance` is fixed-step and preallocated. Capacity pressure explicitly drops newest
  particles or evicts oldest particles. `GpuParticleInstance` uses two bounded GL3 float state
  framebuffers; `ParticleBackendSelector` always chooses the deterministic CPU implementation on
  GL2 or for unsupported modifiers.
- `DecalInstance` produces stable order-then-spawn-id snapshots with lifetime fading. Separate 2D
  and 3D adapters consume application-resolved transforms and normals without inspecting meshes.
- `PostProcessGraphDefinition` validates a bounded DAG and stable topological order.
  `PostProcessGraphRenderer` pools its own intermediate framebuffers and consumes explicitly named,
  application-owned `SceneCapture` inputs. `DistortionRenderer` is its one-pass scene/vector-field
  specialization.

## Lifecycle

Mutable visual state is always explicit:

```java
instance.setAnchor(new EffectAnchor("ship", x, y, z));
instance.advance(deltaSeconds);
TrailSnapshot snapshot = instance.snapshot();
renderer.render(snapshot, registeredAssets);
```

No effect instance starts a thread, reads a clock, discovers a game object, or schedules itself.
Create and use GL adapters on the application's render thread. Close instances and adapters in the
application's disposal orchestration; adapters never dispose caller textures, cameras, meshes,
batches, render contexts, or captures.

## Bounds and evidence

`RuntimeLimits`, `EffectsLimits`, and each definition's capacity are enforced before allocation or
execution. Snapshots report dropped or evicted elements. Particle backend evidence reports the
selected backend and fallback reason. Pass-graph evidence reports execution order, current pool
size, framebuffer evictions, and missing inputs. Protocol and MCP operations return only bounded
summaries of application-declared names.

## Qualified effect catalog

The optional `effects-library` module provides an immutable in-memory `EffectCatalog` and six
bundled definitions that reuse these same general-VFX types. Catalog searches and exact lookups
require explicit `EffectCapabilities`; incompatible variants are absent rather than returned with a
warning. The bundled variants are qualified only for desktop OpenGL and must not be treated as
mobile OpenGL ES or WebGL-qualified content.

Catalog definitions contain logical `AssetKey` values, never paths or loaded resources. The
application resolves those keys and owns all asset loading, persistence, and registration. See the
[effect library guide](effect-library.md) for direct Java and MCP discovery.

## Compatibility limits

The Godot importer translates the documented `canvas_item` shader subset; there is no Unity or
Unreal shader importer. The libGDX 2D particle
and Flame importers translate bounded source text plus explicit source-name to `AssetKey` mappings.
They never open paths or instantiate engine runtime classes. Unsupported fields produce
source-located approximation diagnostics. Compilation or import success is not a claim of universal
visual equivalence; qualify important effects in the target game, camera, blend pipeline, and target
GL profile.

The deterministic cross-family native fixture is `GeneralVfxFixtureTest`. It renders stable evidence
for materials, trails, beams, lightning, CPU particles, selected GPU-or-fallback particles, decals,
distortion, and multipass composition under Xvfb.
