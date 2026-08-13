---
name: libgdx-agent-effects-dev
description: Repository workflow for libgdx-agent-effects. Use when implementing, reviewing, debugging, documenting, testing, or releasing changes to effects-core, effects-libgdx, effects-protocol, effects-mcp, effects-fixtures, the public Java API, effect/pass-graph schemas, shader diagnostics, deterministic preview, pixel comparison, JSON commands, MCP tools, libGDX integration, dependencies, or Maven Central artifacts.
---

# libGDX Agent Effects development

Start with the repository-root `AGENTS.md`. Preserve its architecture and security contract.

## Select context

Read only the sources relevant to the task:

- Effect/pass-graph schema, uniforms, bounds, or diagnostics: `docs/design-contract.md`,
  the matching ADRs, and the matching core tests.
- Render-thread shader compile, FrameBuffer passes, or deterministic preview: `docs/adr/`,
  `docs/guides/getting-started.md`, and `effects-libgdx` plus fixture tests.
- Protocol, JSON, or MCP: `docs/guides/agent-tools.md`, `SECURITY.md`, the protocol service and
  tool catalog classes, and their tests.
- Dependency or build changes: `docs/dependency-review.md`, version catalog, dependency locks,
  verification metadata, CI, and publication archive checks.
- Release preparation: `docs/guides/releasing.md`, `docs/sonatype-central-compliance.md`, and the
  latest release notes. Re-check current external requirements before release work.

Source and tests are authoritative when documentation has drifted. Update the affected contract or
guide in the same change.

## Work vertically

1. Inspect repository status and the current public contract.
2. Identify the owning module. Do not route dependencies against the module direction.
3. Add the smallest focused regression or contract test first.
4. Implement immutable validated core behavior before adapters or transports.
5. For a public feature, carry the same semantics through Java API, typed protocol, MCP catalog and
   handler, and `effects-fixtures`; do not implement only one access layer.
6. Exercise invalid lifecycle/thread calls, unknown fields, unsupported versions, bounds,
   truncation/eviction, deterministic ordering, and bounded diagnostics as applicable.
7. Update guides and ADRs when public behavior or architecture changes.

Do not weaken tests to accept nondeterminism. Do not read GL resources off the render thread. Keep
application-owned scheduling and mutation explicit.

## Verify

Run `.agents/skills/libgdx-agent-effects-dev/scripts/verify.sh <gate>` from the repository root:

- `core`, `libgdx`, `protocol`, or `mcp`: focused module tests and Javadocs.
- `fixture`: real LWJGL3 fixture on Linux under Xvfb; compile fixture tests on other systems.
- `check`: repository checks and published-module Javadocs without cleaning.
- `full`: clean end-to-end gate; require `xvfb-run` on Linux and follow hosted CI behavior on other
  systems.

On Linux, install `xvfb-run` before using `fixture` or `full`. Use package
`xorg-x11-server-Xvfb` on Fedora/Nobara or `xvfb` on Debian/Ubuntu. Do not bypass the gate with the
active desktop `DISPLAY`.

Use a focused gate while iterating. Use `check` for cross-module code or public API changes and
`full` for broad, native-integration, dependency, packaging, release-facing, or explicitly requested
verification. Report exactly what ran and any skipped platform-native coverage.

## Release boundary

Treat signing, staging, publishing, tag creation, and public-release changes as separate steps.
Publishing to Maven Central requires explicit user authorization. Verify the authoritative Sonatype
state and public artifacts before reporting publication complete.
