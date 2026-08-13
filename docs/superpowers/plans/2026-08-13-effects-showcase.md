# Interactive Effects Showcase Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an unpublished LWJGL3 before/after desktop application that renders six real shader presets through the public effects API.

**Architecture:** `effects-showcase` owns the window, input, source-scene generation, UI resources, file output, and GL display textures. Immutable preset declarations construct bounded `EffectDescription` values consumed by `PreviewRenderer`; published modules remain unchanged.

**Tech Stack:** Java 25, libGDX 1.14.2, LWJGL3, JUnit 6.1.2, Gradle 9.6.1, Xvfb.

## Global Constraints

- Keep `effects-showcase` outside `publishedModules` and preserve module direction.
- All GL creation, rendering, capture, texture replacement, and disposal stays on the render thread.
- Use only a deterministic built-in `RgbaImage`; no arbitrary file loading or live-game capture.
- Presets are bounded immutable Java declarations with stable order and unique slugs.
- Export only to application-selected `showcase-output/`; do not add filesystem authority to the library.
- Do not change the public Java API, JSON protocol, or MCP schema.

---

### Task 1: Deterministic showcase model

**Files:**
- Modify: `settings.gradle.kts`
- Create: `effects-showcase/build.gradle.kts`
- Create: `effects-showcase/src/main/java/io/github/teemuki8/libgdx/agent/effects/showcase/BuiltInScene.java`
- Create: `effects-showcase/src/main/java/io/github/teemuki8/libgdx/agent/effects/showcase/ShowcasePreset.java`
- Create: `effects-showcase/src/main/java/io/github/teemuki8/libgdx/agent/effects/showcase/ShowcasePresets.java`
- Create: `effects-showcase/src/main/java/io/github/teemuki8/libgdx/agent/effects/showcase/ShowcaseState.java`
- Test: matching classes under `effects-showcase/src/test/java/.../showcase/`

**Interfaces:**
- Produces: `BuiltInScene.create(): RgbaImage`, `ShowcasePresets.all(): List<ShowcasePreset>`, and mutable application-owned `ShowcaseState` with selection, pause, reset, clamped intensity, and wrapping time.

- [ ] Write tests asserting a deterministic 320 by 240 scene, six ordered names, unique slugs, valid bounded effects, reset defaults, intensity clamp, and time wrap.
- [ ] Run `./gradlew :effects-showcase:test --tests '*BuiltInSceneTest' --tests '*ShowcasePresetsTest' --tests '*ShowcaseStateTest'` and verify compilation fails because the production types do not exist.
- [ ] Implement the minimal scene, preset catalog, shaders, and state needed by the tests.
- [ ] Rerun the focused tests and require success with Java `-Werror`.
- [ ] Commit the model slice.

### Task 2: Real GL preset qualification

**Files:**
- Create: `effects-showcase/src/test/java/io/github/teemuki8/libgdx/agent/effects/showcase/ShowcaseGdxHost.java`
- Create: `effects-showcase/src/test/java/io/github/teemuki8/libgdx/agent/effects/showcase/ShowcaseRenderingTest.java`

**Interfaces:**
- Consumes: `ShowcasePreset.effect(source, time, intensity)` and `PreviewRenderer.render(effect)`.
- Produces: Xvfb proof that every preset compiles, renders, differs from the source, and honors time/intensity where declared.

- [ ] Write the real-GL test for all six presets plus animated and intensity variants.
- [ ] Run it under Xvfb and verify failure before the rendering contract is complete.
- [ ] Adjust only preset shader declarations and model metadata necessary to pass.
- [ ] Rerun the Xvfb rendering test and require success.
- [ ] Commit the qualified preset slice.

### Task 3: Interactive desktop comparator

**Files:**
- Create: `effects-showcase/src/main/java/io/github/teemuki8/libgdx/agent/effects/showcase/EffectsShowcaseApplication.java`
- Create: `effects-showcase/src/main/java/io/github/teemuki8/libgdx/agent/effects/showcase/DesktopLauncher.java`
- Create: `effects-showcase/src/test/java/io/github/teemuki8/libgdx/agent/effects/showcase/EffectsShowcaseSmokeTest.java`

**Interfaces:**
- Consumes: source scene, preset catalog/state, `PreviewRenderer`, and `PreviewPngWriter`.
- Produces: a 1180 by 700 before/after workbench, mouse/keyboard controls, structured failure status, deterministic PNG export, and `--smoke <frames>` bounded launch mode.

- [ ] Write a smoke test that launches the actual application under LWJGL3, renders bounded frames, and exits without failure.
- [ ] Run it under Xvfb and verify failure because the application/launcher do not exist.
- [ ] Implement render-thread UI resources, source/processed panes, preset rail, sliders, shortcuts, status, safe texture replacement, export, and disposal.
- [ ] Rerun the smoke and real-GL preset tests under Xvfb.
- [ ] Commit the desktop application slice.

### Task 4: Integration, documentation, and full verification

**Files:**
- Modify: `README.md`
- Modify: `CHANGELOG.md`
- Create: `docs/guides/showcase.md`
- Create: `effects-showcase/gradle.lockfile`

**Interfaces:**
- Produces: documented `./gradlew :effects-showcase:run` launch path and a reproducible unpublished module.

- [ ] Add concise launch, controls, preset, export, and boundary documentation.
- [ ] Generate and review the showcase dependency lock.
- [ ] Run `xvfb-run -a ./gradlew :effects-showcase:test --rerun-tasks --warning-mode=fail`.
- [ ] Run `.agents/skills/libgdx-agent-effects-dev/scripts/verify.sh full` and `git diff --check`.
- [ ] Inspect the Gradle project/publication model to prove `effects-showcase` has no Maven publication.
- [ ] Commit the verified integration.

## Self-review

- Spec coverage: module boundary, six presets, built-in sampler source, controls, export ownership, structured failure, smoke mode, Xvfb rendering, docs, locks, and publication exclusion are each assigned to a task.
- Placeholder scan: no unresolved implementation placeholders remain.
- Type consistency: later tasks consume only `BuiltInScene`, `ShowcasePreset`, `ShowcasePresets`, and `ShowcaseState` interfaces produced by Task 1.
