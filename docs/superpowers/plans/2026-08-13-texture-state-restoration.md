# Texture State Restoration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use test-driven development and systematic debugging to implement this plan task-by-task.

**Goal:** Prevent preview sampler binding from changing the host application's active GL texture selector.

**Architecture:** Snapshot `GL_ACTIVE_TEXTURE` immediately before sampler work and restore the
exact prior enum in the existing render cleanup path.

**Tech Stack:** Java 25, libGDX/LWJGL3, JUnit 5, Gradle, Xvfb.

## Global Constraints

- Keep every GL query and mutation on the render thread.
- Restore only the state this code mutates.
- Preserve deterministic multi-sampler unit assignment.

---

### Task 1: Restore the active texture selector

**Files:**
- Modify: `effects-libgdx/src/main/java/io/github/teemuki8/libgdx/agent/effects/libgdx/PreviewRenderer.java`
- Modify: `effects-libgdx/src/test/java/io/github/teemuki8/libgdx/agent/effects/libgdx/PreviewRendererTest.java`

**Interfaces:**
- Consumes: the host's current `GL_ACTIVE_TEXTURE` enum.
- Produces: unchanged host active-texture state after success or failure.

- [x] **Step 1: Add and run the two-sampler regression**

Render with `u_a` and `u_b`, query `GL_ACTIVE_TEXTURE`, and assert the hand-set prior unit remains
active. Run the focused test under Xvfb and expect the assertion to fail with the last sampler unit.

- [x] **Step 2: Implement exact restoration**

Use a direct one-int buffer with `glGetIntegerv(GL_ACTIVE_TEXTURE, ...)`, then call
`glActiveTexture(previous)` in cleanup before returning control to the host.

- [x] **Step 3: Verify focused and affected gates**

Run the libGDX, fixture, and check gates from the issue, followed by `git diff --check`.
