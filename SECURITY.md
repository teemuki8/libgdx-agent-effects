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

Shader and effect sources are a closed declarative schema. Effect descriptions, uniforms, pass
graphs, and pixel comparisons do not accept expressions, method names, arbitrary code, or
executable shader paths chosen by the caller. GLSL source is passed to the driver for compilation
only after explicit length and pass-count bounds; the library does not execute arbitrary JVM code.

Pixel comparison accepts only bounded scalar tolerances and explicit region masks over captured
framebuffer output. It performs no DOM, scene-graph, accessibility, widget-object, reflection, or
arbitrary object traversal.

Do not publish credentials, tokens, secrets, private user data, or filesystem information as effect
names, uniform values, diagnostics, or MCP results.

Reports should include affected version, reproducible input, impact, and whether a configured limit
was bypassed. Do not include live secrets in a report.
