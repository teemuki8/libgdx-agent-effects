# MCP Render Dispatch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use test-driven development and systematic debugging to implement this plan task-by-task.

**Goal:** Make wired MCP effect operations execute through an application-owned render-thread handoff without blocking the MCP or render thread.

**Architecture:** Change `EffectsBackend` results to `CompletionStage`, compose them in the MCP
handler, and let the fixture backend post GL work to its captured owner thread.

**Tech Stack:** Java 25, Reactor, libGDX/LWJGL3, JUnit 5, Gradle.

## Global Constraints

- Preserve module direction: `effects-mcp -> effects-protocol -> effects-core <- effects-libgdx`.
- Keep all GL work and disposal on the application render thread.
- Preserve closed MCP schemas and stable typed errors.
- Run native fixture coverage only under Xvfb on Linux.

---

### Task 1: Asynchronous render-owned backend dispatch

**Files:**
- Modify: `effects-protocol/src/main/java/io/github/teemuki8/libgdx/agent/effects/protocol/EffectsBackend.java`
- Modify: `effects-mcp/src/main/java/io/github/teemuki8/libgdx/agent/effects/mcp/EffectsToolHandler.java`
- Create: `effects-mcp/src/test/java/io/github/teemuki8/libgdx/agent/effects/mcp/EffectsToolHandlerTest.java`
- Modify: `effects-fixtures/src/main/java/io/github/teemuki8/libgdx/agent/effects/fixtures/EffectsFixtureBackend.java`
- Modify: `effects-fixtures/src/test/java/io/github/teemuki8/libgdx/agent/effects/fixtures/EffectsFixtureBackendTest.java`

**Interfaces:**
- Consumes: declared effects from `EffectsProtocolService` and application render dispatch through `Gdx.app.postRunnable`.
- Produces: `CompletionStage<CompileResult>`, `CompletionStage<PreviewResult>`, and `CompletionStage<CompareResult>`.

- [ ] **Step 1: Add and run the owner-thread regression**

Assert that a wired backend observes its owner thread when invoked through `EffectsToolHandler`.
Run `./gradlew :effects-mcp:test --tests '*EffectsToolHandlerTest' --rerun-tasks`; expect the thread assertion to fail on a virtual thread.

- [ ] **Step 2: Make the backend contract asynchronous**

Change each `EffectsBackend` method to return `CompletionStage<Results.*Result>` and compose that
stage from `EffectsToolHandler` without calling `block`, `join`, or `get`.

- [ ] **Step 3: Preserve stable backend failures**

Add a test backend that completes exceptionally with `EffectsException(WRONG_THREAD, ...)` and
assert an MCP error code of `WRONG_THREAD`; map other failures to `INTERNAL_ERROR`.

- [ ] **Step 4: Schedule fixture GL operations**

Capture the fixture backend owner thread. Complete inline on that thread; otherwise post the
operation with `Gdx.app.postRunnable` and complete a `CompletableFuture`.

- [ ] **Step 5: Verify focused and native paths**

Run the MCP, fixture, and check gates from the issue, followed by `git diff --check`.
