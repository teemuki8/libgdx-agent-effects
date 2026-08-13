# MCP Render Dispatch Design

**Status:** Approved bug-fix scope for GitHub issue #1.

## Problem

`EffectsToolHandler` subscribes on a virtual-thread scheduler and invokes the synchronous
`EffectsBackend` there. The fixture backend owns render-thread-confined libGDX objects, so every
wired MCP compile, preview, or compare call crosses the GL ownership boundary.

## Approaches considered

1. **Asynchronous backend contract (selected).** Return `CompletionStage` results from
   `EffectsBackend`. The application-owned backend decides how to enter its render thread, and MCP
   composes the stage without blocking. This keeps protocol and MCP independent of libGDX.
2. Store a generic render dispatcher beside the backend in `EffectsProtocolService`. This keeps
   backend methods synchronous but splits one lifecycle contract across two independently mutable
   service fields.
3. Inject a render executor into `EffectsToolHandler`. This puts application rendering policy in
   the transport adapter and makes it easier to wire the wrong executor.

## Design

`EffectsBackend.compile`, `preview`, and `compare` return `CompletionStage` values. The fixture
backend captures its owner thread at construction: owner-thread calls complete inline, while calls
from MCP post one bounded operation through `Gdx.app.postRunnable`. `EffectsToolHandler` composes
the returned stage into its `Mono`; it never blocks waiting for GL work.

Input/schema failures remain `INVALID_QUERY`. `EffectsException` failures become typed MCP errors
whose code is the stable exception kind. Unexpected backend failures become `INTERNAL_ERROR` with
no stack trace in the result.

## Verification

Unit tests cover owner-thread dispatch, no-backend behavior, unknown effects, and typed backend
failure. A real LWJGL3 fixture drives at least one handler-to-backend call under Xvfb. The MCP,
fixture, and repository check gates must pass.
