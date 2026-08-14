# General VFX Program Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the approved bounded general-purpose VFX runtime, every requested effect family, and engine-import compatibility without replacing libGDX's graphics primitives.

**Architecture:** Immutable contracts remain in JDK-only `effects-core`; explicitly stepped visual simulation lives in `effects-runtime`; engine translators live in `effects-import`; and render-thread GL implementations live in `effects-libgdx`. Protocol and MCP expose closed operations over core-owned records, while real LWJGL3 fixtures qualify every public vertical slice under Xvfb.

**Tech Stack:** Java 25, Gradle Kotlin DSL, libGDX 1.14.2, Jackson 2.22.1, MCP SDK 2.0.0, JUnit Jupiter 6.1.2, LWJGL3, OpenGL ES 2/3-compatible GLSL.

## Global Constraints

- Preserve module direction: `effects-mcp -> effects-protocol -> effects-core <- effects-libgdx`; new modules depend inward on `effects-core`, never the reverse.
- Keep `effects-core`, `effects-runtime`, and `effects-import` free of libGDX, GL, Jackson, MCP, filesystem, shell, and networking dependencies.
- Applications retain ownership of the loop, render thread, cameras, assets, gameplay state, and disposal orchestration.
- All GL compilation, allocation, drawing, capture, and disposal remain on the application's render thread.
- Every public value is immutable, defensively copied, deterministically ordered, and bounded by configured plus hard limits.
- Never accept arbitrary paths, includes, reflection, scripts, class names, expressions, or caller-selected network/filesystem access.
- CPU effect simulation is deterministic for identical definitions, seeds, inputs, and fixed steps.
- GL3 GPU particles require a disclosed deterministic CPU fallback for GL2 and unsupported devices.
- Public changes run vertically through Java, protocol, MCP, and real fixtures where applicable.
- Linux native verification uses Xvfb through `.agents/skills/libgdx-agent-effects-dev/scripts/verify.sh`; never use the active desktop display as a substitute.
- Maven Central publication, signing, tags, and public releases are outside this plan and require separate explicit authorization.

---

### Task 1: Establish the general-VFX module and architecture boundary

**Files:**
- Create: `docs/adr/0003-general-vfx-runtime-and-import-boundary.md`
- Create: `effects-runtime/build.gradle.kts`
- Create: `effects-runtime/gradle.lockfile`
- Create: `effects-runtime/src/main/java/io/github/teemuki8/libgdx/agent/effects/runtime/package-info.java`
- Create: `effects-import/build.gradle.kts`
- Create: `effects-import/gradle.lockfile`
- Create: `effects-import/src/main/java/io/github/teemuki8/libgdx/agent/effects/importer/package-info.java`
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`
- Modify: `README.md`
- Modify: `docs/roadmap.md`

**Interfaces:**
- Consumes: the dependency and ownership rules in `docs/adr/0001-render-thread-hand-rolled-passes.md` and `docs/adr/0002-bounded-declarative-effect-model.md`.
- Produces: published `agent-effects-runtime` and `agent-effects-import` modules that each depend only on `effects-core`.

- [ ] **Step 1: Write dependency-boundary checks**

Add a root verification task that inspects resolved project dependencies and asserts that
`effects-core`, `effects-runtime`, and `effects-import` do not depend on libGDX, Jackson, MCP, or
each other in a cycle.

- [ ] **Step 2: Run the boundary check and observe missing modules**

Run: `./gradlew verifyModuleBoundaries`

Expected: FAIL because `effects-runtime` and `effects-import` are not included yet.

- [ ] **Step 3: Add modules, publication metadata, locks, package docs, and ADR**

The ADR must record that the library owns high-level effect definitions and bounded visual state,
while libGDX continues to own low-level graphics primitives and the application owns orchestration.

- [ ] **Step 4: Verify module structure**

Run: `.agents/skills/libgdx-agent-effects-dev/scripts/verify.sh check`

Expected: PASS with warning-free Javadocs for both new empty published modules.

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts build.gradle.kts README.md docs/roadmap.md docs/adr/0003-general-vfx-runtime-and-import-boundary.md effects-runtime effects-import
git commit -m "Establish general VFX module boundaries"
```

### Task 2: Add shared effect, capability, material, curve, and fidelity contracts

**Files:**
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/EffectDefinition.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/Material2dDefinition.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/Material3dDefinition.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/BlendMode.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/AssetKey.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/FloatCurve.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/ColorGradient.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/EffectCapabilities.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/FidelityClassification.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/ImportDiagnostic.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/SourceSpan.java`
- Modify: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/EffectsLimits.java`
- Test: `effects-core/src/test/java/io/github/teemuki8/libgdx/agent/effects/core/GeneralEffectModelTest.java`
- Test: `effects-core/src/test/java/io/github/teemuki8/libgdx/agent/effects/core/EffectsLimitsTest.java`

**Interfaces:**
- Consumes: existing `ShaderSource`, `UniformBinding`, and `EffectsException`.
- Produces: `sealed interface EffectDefinition`, immutable material records, bounded piecewise-linear curves and gradients, registered `AssetKey` identifiers, `EffectCapabilities`, source spans, and fidelity diagnostics.

- [ ] **Step 1: Write failing immutable-model and hard-limit tests**

Tests construct curves and gradients with mutable input lists, mutate the originals, and assert the
records remain unchanged. They also assert rejection of duplicate/non-increasing stops, non-finite
values, invalid asset keys, excess definition nodes, and oversized generated shader text.

- [ ] **Step 2: Run focused core tests and confirm missing types**

Run: `.agents/skills/libgdx-agent-effects-dev/scripts/verify.sh core`

Expected: FAIL at test compilation because the new contracts do not exist.

- [ ] **Step 3: Implement the smallest closed contracts**

Use signatures equivalent to:

```java
public sealed interface EffectDefinition permits Material2dDefinition, Material3dDefinition {
    String name();
    EffectDefinition validate(EffectsLimits limits);
}

public record Material2dDefinition(
        String name, ShaderSource shader, BlendMode blendMode,
        List<UniformBinding> uniforms, List<AssetKey> textures)
        implements EffectDefinition { }
```

Extend `EffectsLimits` with explicit maxima for tokens, AST depth, generated characters, functions,
statements, arrays, curve stops, gradient stops, definition nodes, runtime instances, particles,
trail points, beam segments, lightning branches, decals, and framebuffer pixels.

- [ ] **Step 4: Run focused core verification**

Run: `.agents/skills/libgdx-agent-effects-dev/scripts/verify.sh core`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add effects-core
git commit -m "Add shared general VFX contracts"
```

### Task 3: Implement and qualify the Godot `canvas_item` importer vertical slice

**Files:**
- Create and modify the exact importer, core, protocol, MCP, fixture, and documentation files listed in `docs/superpowers/plans/2026-08-14-godot-canvas-importer.md`.

**Interfaces:**
- Consumes: `Material2dDefinition`, `EffectsLimits`, `SourceSpan`, `ImportDiagnostic`, and `FidelityClassification` from Task 2.
- Produces: `GodotCanvasImporter#importShader(ImportRequest)`, closed protocol/MCP import operations, target GLSL, source maps, and Xvfb-qualified reference evidence.

- [ ] **Step 1: Execute every checked step in the importer-slice plan**

Run each red-green-refactor cycle in
`docs/superpowers/plans/2026-08-14-godot-canvas-importer.md` in order. Do not collapse parser,
translation, protocol, and real-GL qualification into one unreviewable commit.

- [ ] **Step 2: Verify the complete importer slice**

Run: `.agents/skills/libgdx-agent-effects-dev/scripts/verify.sh full`

Expected: PASS under Xvfb, including generated-shader compilation and stored reference comparison.

### Task 4: Add the explicit runtime instance and snapshot lifecycle

**Files:**
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/EffectSnapshot.java`
- Create: `effects-runtime/src/main/java/io/github/teemuki8/libgdx/agent/effects/runtime/EffectInstance.java`
- Create: `effects-runtime/src/main/java/io/github/teemuki8/libgdx/agent/effects/runtime/EffectEvent.java`
- Create: `effects-runtime/src/main/java/io/github/teemuki8/libgdx/agent/effects/runtime/EffectAnchor.java`
- Create: `effects-runtime/src/main/java/io/github/teemuki8/libgdx/agent/effects/runtime/EffectRuntime.java`
- Test: `effects-runtime/src/test/java/io/github/teemuki8/libgdx/agent/effects/runtime/EffectRuntimeTest.java`

**Interfaces:**
- Consumes: closed `EffectDefinition` variants and runtime limits.
- Produces: `EffectInstance#advance(float)`, `EffectInstance#submit(EffectEvent)`, `EffectInstance#setAnchor(EffectAnchor)`, and immutable `EffectSnapshot snapshot()`.

- [ ] **Step 1: Write lifecycle, fixed-step, capacity, and close tests**

Assert rejection of non-finite/negative deltas, calls after close, unknown anchor names, excess queued
events, and state above configured capacity. Assert identical snapshots for identical seeds and
input sequences.

- [ ] **Step 2: Run runtime tests and confirm missing API failure**

Run: `./gradlew :effects-runtime:test --tests '*EffectRuntimeTest'`

Expected: FAIL at compilation.

- [ ] **Step 3: Implement explicit stepping without threads or timers**

```java
public interface EffectInstance extends AutoCloseable {
    void setAnchor(EffectAnchor anchor);
    void submit(EffectEvent event);
    void advance(float deltaSeconds);
    EffectSnapshot snapshot();
    @Override void close();
}
```

- [ ] **Step 4: Verify runtime and core**

Run: `./gradlew :effects-core:check :effects-runtime:check javadoc --warning-mode=fail`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add effects-core effects-runtime
git commit -m "Add explicit visual effect runtime lifecycle"
```

### Task 5: Add sprite and mesh material renderers

**Files:**
- Create: `effects-libgdx/src/main/java/io/github/teemuki8/libgdx/agent/effects/libgdx/Material2dRenderer.java`
- Create: `effects-libgdx/src/main/java/io/github/teemuki8/libgdx/agent/effects/libgdx/Material3dShaderProvider.java`
- Create: `effects-libgdx/src/main/java/io/github/teemuki8/libgdx/agent/effects/libgdx/RegisteredAssetResolver.java`
- Test: `effects-libgdx/src/test/java/io/github/teemuki8/libgdx/agent/effects/libgdx/Material2dRendererTest.java`
- Test: `effects-libgdx/src/test/java/io/github/teemuki8/libgdx/agent/effects/libgdx/Material3dShaderProviderTest.java`

**Interfaces:**
- Consumes: material definitions and application-registered asset keys.
- Produces: render-thread-confined renderers that bind stable shader semantics without owning batches, cameras, textures, or model instances.

- [ ] **Step 1: Write real-GL material and host-state tests**

Cover custom 2D vertex layout, texture resolution, uniform binding, missing assets, wrong thread,
compile failure, depth/cull/blend restoration, and application-owned texture non-disposal.

- [ ] **Step 2: Run the libGDX gate and confirm failure**

Run: `.agents/skills/libgdx-agent-effects-dev/scripts/verify.sh libgdx`

Expected: FAIL because the renderer types are absent.

- [ ] **Step 3: Implement render-thread adapters over libGDX primitives**

Never call `Batch.begin/end` or `ModelBatch.begin/end` internally; require the documented host state
or expose an explicit one-call render boundary that snapshots and restores every changed state.

- [ ] **Step 4: Verify under Xvfb**

Run: `.agents/skills/libgdx-agent-effects-dev/scripts/verify.sh libgdx`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add effects-libgdx
git commit -m "Add general material render adapters"
```

### Task 6: Add trails and ribbons

**Files:**
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/TrailDefinition.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/TrailSnapshot.java`
- Create: `effects-runtime/src/main/java/io/github/teemuki8/libgdx/agent/effects/runtime/TrailInstance.java`
- Create: `effects-libgdx/src/main/java/io/github/teemuki8/libgdx/agent/effects/libgdx/TrailRenderer.java`
- Test: `effects-runtime/src/test/java/io/github/teemuki8/libgdx/agent/effects/runtime/TrailInstanceTest.java`
- Test: `effects-libgdx/src/test/java/io/github/teemuki8/libgdx/agent/effects/libgdx/TrailRendererTest.java`

**Interfaces:**
- Consumes: named anchors, material, width curve, color gradient, sample interval/distance, point limit, lifetime, join, cap, and UV policy.
- Produces: bounded oldest-to-newest trail snapshots and render-thread ribbon meshes.

- [ ] **Step 1: Write sampling, expiry, sharp-turn, and capacity tests**

Use a deterministic right-angle path. Assert stable sample positions, oldest-point eviction, no NaNs
for coincident points, bounded miter length, and zero retained points after expiry.

- [ ] **Step 2: Run focused tests and confirm failure**

Run: `./gradlew :effects-runtime:test --tests '*TrailInstanceTest'`

Expected: FAIL at compilation.

- [ ] **Step 3: Implement the bounded ring and immutable snapshot**

Use fixed-capacity arrays or a fixed-capacity deque; never allocate per frame after construction.

- [ ] **Step 4: Write and pass the Xvfb ribbon pixel test**

Run: `xvfb-run -a ./gradlew :effects-libgdx:test --tests '*TrailRendererTest'`

Expected: PASS with deterministic non-empty ribbon pixels and restored GL state.

- [ ] **Step 5: Commit**

```bash
git add effects-core effects-runtime effects-libgdx
git commit -m "Add bounded trail and ribbon effects"
```

### Task 7: Add beams and lightning

**Files:**
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/BeamDefinition.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/BeamSnapshot.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/LightningDefinition.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/LightningSnapshot.java`
- Create: `effects-runtime/src/main/java/io/github/teemuki8/libgdx/agent/effects/runtime/BeamInstance.java`
- Create: `effects-runtime/src/main/java/io/github/teemuki8/libgdx/agent/effects/runtime/LightningInstance.java`
- Create: `effects-libgdx/src/main/java/io/github/teemuki8/libgdx/agent/effects/libgdx/BeamRenderer.java`
- Test: `effects-runtime/src/test/java/io/github/teemuki8/libgdx/agent/effects/runtime/BeamAndLightningTest.java`
- Test: `effects-libgdx/src/test/java/io/github/teemuki8/libgdx/agent/effects/libgdx/BeamRendererTest.java`

**Interfaces:**
- Consumes: start/end anchors, segment and branch caps, seeded roughness, pulse, lifetime, and material.
- Produces: bounded stable segment snapshots; the beam renderer also renders lightning snapshots because both are strip/segment geometry.

- [ ] **Step 1: Write endpoint, seed, regeneration, zero-length, and cap tests**

- [ ] **Step 2: Run and observe missing types**

Run: `./gradlew :effects-runtime:test --tests '*BeamAndLightningTest'`

Expected: FAIL at compilation.

- [ ] **Step 3: Implement deterministic segment generation**

Use a repository-owned stable PRNG algorithm whose sequence is asserted in tests; do not rely on a
JDK implementation whose sequence contract is unspecified.

- [ ] **Step 4: Qualify the renderer under Xvfb**

Run: `xvfb-run -a ./gradlew :effects-libgdx:test --tests '*BeamRendererTest'`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add effects-core effects-runtime effects-libgdx
git commit -m "Add beam and lightning effects"
```

### Task 8: Add deterministic CPU particles

**Files:**
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/ParticleDefinition.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/ParticleSnapshot.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/ParticleModifier.java`
- Create: `effects-runtime/src/main/java/io/github/teemuki8/libgdx/agent/effects/runtime/CpuParticleInstance.java`
- Create: `effects-libgdx/src/main/java/io/github/teemuki8/libgdx/agent/effects/libgdx/ParticleRenderer.java`
- Test: `effects-runtime/src/test/java/io/github/teemuki8/libgdx/agent/effects/runtime/CpuParticleInstanceTest.java`
- Test: `effects-libgdx/src/test/java/io/github/teemuki8/libgdx/agent/effects/libgdx/ParticleRendererTest.java`

**Interfaces:**
- Consumes: bounded closed emitter and modifier definitions, seed, anchors, and bursts.
- Produces: structure-of-arrays CPU particle state and immutable alive-particle snapshots in stable spawn-id order.

- [ ] **Step 1: Write emission, burst, fixed-step, expiry, capacity, and seed tests**

- [ ] **Step 2: Run and observe failure**

Run: `./gradlew :effects-runtime:test --tests '*CpuParticleInstanceTest'`

Expected: FAIL at compilation.

- [ ] **Step 3: Implement allocation-free steady-state simulation**

Use preallocated primitive arrays and a free-index structure. Capacity pressure follows an explicit
policy (`DROP_NEWEST` or `EVICT_OLDEST`) and exposes counts in the snapshot evidence.

- [ ] **Step 4: Qualify sprite and point-sprite rendering**

Run: `xvfb-run -a ./gradlew :effects-libgdx:test --tests '*ParticleRendererTest'`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add effects-core effects-runtime effects-libgdx
git commit -m "Add deterministic CPU particle effects"
```

### Task 9: Add GL3 ping-pong GPU particles and GL2 fallback

**Files:**
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/ParticleBackendEvidence.java`
- Create: `effects-libgdx/src/main/java/io/github/teemuki8/libgdx/agent/effects/libgdx/GpuParticleInstance.java`
- Create: `effects-libgdx/src/main/java/io/github/teemuki8/libgdx/agent/effects/libgdx/ParticleBackendSelector.java`
- Test: `effects-libgdx/src/test/java/io/github/teemuki8/libgdx/agent/effects/libgdx/GpuParticleInstanceTest.java`
- Test: `effects-libgdx/src/test/java/io/github/teemuki8/libgdx/agent/effects/libgdx/ParticleBackendSelectorTest.java`

**Interfaces:**
- Consumes: `ParticleDefinition`, capabilities, seed, bounded events, and explicit delta.
- Produces: GL3 state-texture simulation or `CpuParticleInstance` fallback plus immutable backend/approximation evidence.

- [ ] **Step 1: Write capability-selection and state-bound tests**

Assert GL2 always selects CPU, GL3 selects GPU only for the supported modifier subset, unsupported
modifiers select CPU or fail according to policy, and state textures never exceed configured pixels.

- [ ] **Step 2: Run and observe missing backend failure**

Run: `xvfb-run -a ./gradlew :effects-libgdx:test --tests '*GpuParticleInstanceTest' --tests '*ParticleBackendSelectorTest'`

Expected: FAIL at compilation.

- [ ] **Step 3: Implement two RGBA state framebuffers with explicit swap**

Pack position/age and velocity/lifetime into documented textures. Snapshot and restore framebuffer,
viewport, active texture, program, blend, depth, and cull state around every update and draw.

- [ ] **Step 4: Run the GL3 fixture and forced-fallback tests**

Run: `.agents/skills/libgdx-agent-effects-dev/scripts/verify.sh fixture`

Expected: PASS with GPU and CPU backend evidence.

- [ ] **Step 5: Commit**

```bash
git add effects-core effects-libgdx effects-fixtures
git commit -m "Add bounded GPU particles with CPU fallback"
```

### Task 10: Add 2D and 3D decals

**Files:**
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/DecalDefinition.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/DecalSnapshot.java`
- Create: `effects-runtime/src/main/java/io/github/teemuki8/libgdx/agent/effects/runtime/DecalInstance.java`
- Create: `effects-libgdx/src/main/java/io/github/teemuki8/libgdx/agent/effects/libgdx/Decal2dRenderer.java`
- Create: `effects-libgdx/src/main/java/io/github/teemuki8/libgdx/agent/effects/libgdx/Decal3dRenderer.java`
- Test: `effects-runtime/src/test/java/io/github/teemuki8/libgdx/agent/effects/runtime/DecalInstanceTest.java`
- Test: `effects-libgdx/src/test/java/io/github/teemuki8/libgdx/agent/effects/libgdx/DecalRendererTest.java`

**Interfaces:**
- Consumes: application-resolved transform, optional normal, size, material, lifetime, fade, tint, blend, and order.
- Produces: immutable ordered decal snapshots and adapters over sprite/mesh or `DecalBatch` primitives without inspecting application meshes.

- [ ] **Step 1: Write lifetime, order, capacity, and transform tests**

- [ ] **Step 2: Run and observe missing types**

Run: `./gradlew :effects-runtime:test --tests '*DecalInstanceTest'`

Expected: FAIL at compilation.

- [ ] **Step 3: Implement bounded decal lifetime state**

- [ ] **Step 4: Pass 2D and 3D Xvfb rendering/state-restoration tests**

Run: `xvfb-run -a ./gradlew :effects-libgdx:test --tests '*DecalRendererTest'`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add effects-core effects-runtime effects-libgdx
git commit -m "Add bounded 2D and 3D decals"
```

### Task 11: Add distortion fields and bounded multipass graphs

**Files:**
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/DistortionFieldDefinition.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/PostProcessGraphDefinition.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/RenderPassDefinition.java`
- Create: `effects-libgdx/src/main/java/io/github/teemuki8/libgdx/agent/effects/libgdx/SceneCapture.java`
- Create: `effects-libgdx/src/main/java/io/github/teemuki8/libgdx/agent/effects/libgdx/DistortionRenderer.java`
- Create: `effects-libgdx/src/main/java/io/github/teemuki8/libgdx/agent/effects/libgdx/PostProcessGraphRenderer.java`
- Test: `effects-core/src/test/java/io/github/teemuki8/libgdx/agent/effects/core/PostProcessGraphTest.java`
- Test: `effects-libgdx/src/test/java/io/github/teemuki8/libgdx/agent/effects/libgdx/PostProcessGraphRendererTest.java`

**Interfaces:**
- Consumes: application-owned `SceneCapture`, declared pass DAG, bounded inputs/outputs, and distortion-vector producers.
- Produces: stable topological execution, pooled intermediate framebuffers, and explicit missing-input/eviction/state evidence.

- [ ] **Step 1: Write graph cycle, unknown-input, ordering, and bound tests**

- [ ] **Step 2: Run and confirm failure**

Run: `./gradlew :effects-core:test --tests '*PostProcessGraphTest'`

Expected: FAIL at compilation.

- [ ] **Step 3: Implement immutable DAG validation and stable topological ordering**

- [ ] **Step 4: Implement render-thread framebuffer pooling and distortion composition**

- [ ] **Step 5: Qualify multi-input rendering under Xvfb**

Run: `xvfb-run -a ./gradlew :effects-libgdx:test --tests '*PostProcessGraphRendererTest'`

Expected: PASS with pool/disposal and host-state assertions.

- [ ] **Step 6: Commit**

```bash
git add effects-core effects-libgdx
git commit -m "Add distortion and bounded pass graphs"
```

### Task 12: Add native libGDX particle compatibility adapters

**Files:**
- Create: `effects-import/src/main/java/io/github/teemuki8/libgdx/agent/effects/importer/libgdx/LibgdxParticleImporter.java`
- Create: `effects-import/src/main/java/io/github/teemuki8/libgdx/agent/effects/importer/libgdx/FlameParticleImporter.java`
- Test: `effects-import/src/test/java/io/github/teemuki8/libgdx/agent/effects/importer/libgdx/LibgdxParticleImporterTest.java`
- Test: `effects-import/src/test/java/io/github/teemuki8/libgdx/agent/effects/importer/libgdx/FlameParticleImporterTest.java`
- Test resource: `effects-import/src/test/resources/libgdx/particle/simple.p`
- Test resource: `effects-import/src/test/resources/libgdx/flame/simple.pfx`

**Interfaces:**
- Consumes: bounded source content and explicitly supplied atlas/model asset-key mappings, never paths.
- Produces: neutral `ParticleDefinition` plus feature mappings and approximation diagnostics.

- [ ] **Step 1: Write known-field, unknown-field, bound, and approximation tests**

- [ ] **Step 2: Run and observe missing importer failure**

Run: `./gradlew :effects-import:test --tests '*LibgdxParticleImporterTest' --tests '*FlameParticleImporterTest'`

Expected: FAIL at compilation.

- [ ] **Step 3: Implement content parsers without instantiating libGDX runtime classes**

- [ ] **Step 4: Verify import module and Javadocs**

Run: `./gradlew :effects-import:check :effects-import:javadoc --warning-mode=fail`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add effects-import
git commit -m "Add libGDX particle compatibility importers"
```

### Task 13: Carry all effect families through protocol and MCP

**Files:**
- Modify: `effects-protocol/src/main/java/io/github/teemuki8/libgdx/agent/effects/protocol/Requests.java`
- Modify: `effects-protocol/src/main/java/io/github/teemuki8/libgdx/agent/effects/protocol/Results.java`
- Modify: `effects-protocol/src/main/java/io/github/teemuki8/libgdx/agent/effects/protocol/EffectsBackend.java`
- Modify: `effects-protocol/src/main/java/io/github/teemuki8/libgdx/agent/effects/protocol/EffectsProtocolService.java`
- Modify: `effects-mcp/src/main/java/io/github/teemuki8/libgdx/agent/effects/mcp/EffectsToolCatalog.java`
- Modify: `effects-mcp/src/main/java/io/github/teemuki8/libgdx/agent/effects/mcp/EffectsToolHandler.java`
- Modify: `effects-protocol/src/test/java/io/github/teemuki8/libgdx/agent/effects/protocol/EffectsJsonTest.java`
- Modify: `effects-mcp/src/test/java/io/github/teemuki8/libgdx/agent/effects/mcp/EffectsToolCatalogTest.java`
- Modify: `effects-mcp/src/test/java/io/github/teemuki8/libgdx/agent/effects/mcp/EffectsToolHandlerTest.java`

**Interfaces:**
- Consumes: every closed core definition, snapshot/evidence summary, importer result, and application-owned backend operation.
- Produces: versioned closed JSON requests/results and catalog tools that name registered effects and presets instead of accepting arbitrary executable objects or paths.

- [ ] **Step 1: Write JSON round-trip, unknown-field, version, and catalog-schema tests**

- [ ] **Step 2: Run protocol and MCP tests and confirm failure**

Run: `./gradlew :effects-protocol:test :effects-mcp:test`

Expected: FAIL until all closed variants and handlers are registered.

- [ ] **Step 3: Add bounded registry/backend operations and exact schemas**

Do not accept raw caller-selected filenames. Import tools accept bounded source text and target
profiles; preview tools address application-registered definitions by stable name.

- [ ] **Step 4: Verify protocol and MCP gates**

Run: `.agents/skills/libgdx-agent-effects-dev/scripts/verify.sh protocol`

Run: `.agents/skills/libgdx-agent-effects-dev/scripts/verify.sh mcp`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add effects-protocol effects-mcp
git commit -m "Expose general VFX through closed agent tools"
```

### Task 14: Add cross-family showcase, Wings example, and final qualification

**Files:**
- Modify: `effects-showcase/src/main/java/io/github/teemuki8/libgdx/agent/effects/showcase/EffectsShowcase.java`
- Create: `effects-showcase/src/main/java/io/github/teemuki8/libgdx/agent/effects/showcase/GeneralVfxScene.java`
- Create: `effects-fixtures/src/test/java/io/github/teemuki8/libgdx/agent/effects/fixtures/GeneralVfxFixtureTest.java`
- Create: `docs/guides/general-vfx.md`
- Create: `docs/guides/godot-import.md`
- Create: `docs/examples/wings-ship-trail.java`
- Modify: `README.md`
- Modify: `docs/roadmap.md`
- Modify: release notes for the target release only when a release version has been explicitly selected.

**Interfaces:**
- Consumes: every effect family, backend evidence, importer result, and application-owned lifecycle.
- Produces: one deterministic showcase scene, stored evidence for every family, and a non-owning Wings ship-trail composition example.

- [ ] **Step 1: Write the fixture test before adding the showcase scene**

Assert non-empty bounded renders for material, trail, beam, lightning, CPU particle, selected GPU or
fallback particle, decal, distortion, and post-processing cases. Assert stable artifact names and
pixel evidence.

- [ ] **Step 2: Run the fixture and confirm missing scene failure**

Run: `.agents/skills/libgdx-agent-effects-dev/scripts/verify.sh fixture`

Expected: FAIL because `GeneralVfxScene` and its registered effects do not exist.

- [ ] **Step 3: Implement the scene and Wings integration example**

The example supplies anchors and calls `advance`, `snapshot`, and render explicitly. It must not add
a dependency on Wings or imply that the effects library owns multiplayer/gameplay state.

- [ ] **Step 4: Run the full gate**

Run: `.agents/skills/libgdx-agent-effects-dev/scripts/verify.sh full`

Expected: PASS under Xvfb with clean checks, tests, native fixtures, publication archives, and
warning-free Javadocs.

- [ ] **Step 5: Audit the final diff and commit**

```bash
git diff --check
git status --short
git add README.md docs effects-showcase effects-fixtures
git commit -m "Qualify the general VFX toolkit"
```
