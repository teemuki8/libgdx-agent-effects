# libGDX Agent Effects agent guide

Read this file before repository work. For task-specific workflow and verification, use the
repository skill at
[`.agents/skills/libgdx-agent-effects-dev/SKILL.md`](.agents/skills/libgdx-agent-effects-dev/SKILL.md).

## Skills

- Use `$libgdx-agent-effects-dev` for every implementation, review, architecture, protocol, MCP,
  fixture, documentation, dependency, or release task in this repository.
- Use `$karpathy-guidelines` when writing, reviewing, or refactoring code. Keep changes surgical,
  state assumptions, and define observable success before editing.
- Use `$github:github` for GitHub issue or pull-request orientation. Route unresolved review work
  to `$github:gh-address-comments`, failing Actions work to `$github:gh-fix-ci`, and an explicitly
  requested commit/push/draft-PR workflow to `$github:yeet`.
- Use `$skill-creator` when changing the repository skill itself. Keep its metadata aligned and run
  the skill validator before completion.

If an installed skill is unavailable in the active environment, follow its repository-equivalent
workflow rather than blocking ordinary development.

## Always-on contract

- This is a third-party Java 25 library. It does not own or patch libGDX, the application loop,
  render thread, input, assets, GL resources, or disposal.
- Preserve module direction: `effects-mcp -> effects-protocol -> effects-core <- effects-libgdx`.
  `effects-fixtures` may depend on the full stack but is never published.
- Keep `effects-core` JDK-only. Do not add libGDX, Jackson, MCP, transport, filesystem, shell, or
  networking dependencies to core.
- All GL work — `ShaderProgram` compilation, `FrameBuffer` use, drawing, and framebuffer capture —
  is confined to the application's render thread. Never touch GL resources from other threads.
- Expose only effects and shaders explicitly declared by application code. Never add reflection,
  arbitrary class loading or serialization, class-name input, expressions, scripts, bytecode
  inspection, or caller-selected network/filesystem access.
- Public values and evidence must be immutable, deterministic, deeply bounded, and safe across
  thread and trust boundaries. Shader source length, uniform count, pass count, texture sizes,
  diagnostic log lengths, and pixel-diff tolerances all need configured limits with explicit
  truncation or eviction evidence.
- Structured diagnostics are the primary interface to shader compilation: map errors to source
  lines and report active uniforms/attributes. Never treat the raw driver log string as the
  contract; parse it into a bounded, typed result.
- MCP is local stdio in an application-owned development launcher. Do not add a listener or imply
  that a separate JVM can inspect a live game without an explicit transport design and ADR.

## Change workflow

1. Inspect `git status --short` and preserve unrelated user changes.
2. Read the repository skill, then load only the docs and ADRs it routes to for the task.
3. Establish the public behavior and security boundary before editing. Add an ADR before a lasting
   architectural change.
4. Begin behavior changes with a focused test. For public features, trace one vertical slice through
   Java API, protocol, MCP, and the real fixture as applicable.
5. Make the smallest coherent change and update affected guides, protocol examples, or release notes.
6. Run a focused verification gate while iterating, then the relevant end-to-end gate from the skill.

## Definition of done

- Focused tests cover success, invalid lifecycle/thread use, hard bounds, truncation/eviction, stable
  ordering, and structured failure where relevant.
- Public Java records and methods validate and defensively copy inputs and have warning-free Javadocs.
- Protocol JSON and MCP inputs remain closed and reject unknown fields and unsupported versions.
- Linux-native integration changes pass the real LWJGL3 fixture under Xvfb.
- On Linux, treat `xvfb-run` as a repository verification prerequisite. Do not substitute the
  developer's active desktop display for the isolated fixture or full gate.
- Broad or release-facing work passes `./gradlew clean check javadoc --warning-mode=fail` (under Xvfb
  on Linux).
- Publishing to Maven Central is irreversible and requires explicit user authorization plus current
  Sonatype/public-artifact verification. Never infer publishing permission from ordinary release work.
