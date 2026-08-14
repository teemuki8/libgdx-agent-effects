# Security policy

## Supported versions

| Version | Supported |
| --- | --- |
| Latest `0.x` release | Yes |

Security fixes are released on the latest 0.x line while the library is pre-1.0.

## Boundary

MCP is a local development tool over stdio. It compiles and previews only shader and effect sources
explicitly supplied by application code, and provides no shell, filesystem, reflection, script,
arbitrary class loading, network destination, or remote listener.

Stdio input is framed before parsing: each newline-terminated JSON-RPC frame is bounded in raw
bytes, oversized frames are drained through their newline without retention, malformed UTF-8 is
rejected with a strict decoder, and nesting, string, and number tokens are capped before schema
validation. A rejected frame produces one bounded JSON-RPC parse error and does not terminate later
valid requests.

Effect descriptions, uniforms, pass graphs, and pixel comparisons are closed declarative schemas.
The Godot importer accepts shader expressions only as bounded source content: a single-pass lexer,
typed parser, semantic analyzer, and generator enforce source, token, AST, statement, expression,
mapping, diagnostic, and generated-text limits. Preprocessing, includes, filenames, and asset paths
are rejected. Generated GLSL is passed to the driver only on the application render thread; shader
source never becomes JVM code.

Importing is separately wired from compiling and previewing. It neither registers nor persists an
effect. Structured source mappings and diagnostics are bounded before crossing protocol/MCP trust
boundaries, and compilation alone is not evidence of visual equivalence.

Pixel comparison accepts only bounded scalar tolerances and explicit region masks over captured
framebuffer output. It performs no DOM, scene-graph, accessibility, widget-object, reflection, or
arbitrary object traversal.

Do not publish credentials, tokens, secrets, private user data, or filesystem information as effect
names, uniform values, diagnostics, or MCP results.

Reports should include affected version, reproducible input, impact, and whether a configured limit
was bypassed. Do not include live secrets in a report.
