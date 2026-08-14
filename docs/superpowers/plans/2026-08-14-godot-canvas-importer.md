# Godot Canvas Shader Importer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Import bounded Godot 4 `canvas_item` shader source into libGDX-compatible 2D material GLSL with structured feature mappings, approximation diagnostics, real-GL compilation, reference-image evidence, and closed Java/protocol/MCP access.

**Architecture:** `effects-import` performs lexing, parsing, semantic analysis, and target generation without GL or external dependencies. Import-facing immutable records live in `effects-core`; `effects-libgdx` adapts generated material output to the existing fullscreen qualification path; protocol and MCP call a separately wired import backend so their dependency direction remains unchanged.

**Tech Stack:** Java 25, Gradle Kotlin DSL, libGDX 1.14.2, Jackson 2.22.1, MCP SDK 2.0.0, JUnit Jupiter 6.1.2, LWJGL3/Xvfb, GLSL ES 100 and GLSL ES 300.

## Global Constraints

- Accept bounded source content, never filenames, includes, or filesystem paths.
- Parse with a lexer and recursive-descent parser; do not translate source with regular expressions.
- Preserve exact source spans and deterministic diagnostic ordering.
- Reject unknown shader types, malformed syntax, unsafe/unavailable semantics, and every configured or hard-limit violation.
- Never silently delete source constructs to make generated GLSL compile.
- Keep `effects-core` and `effects-import` JDK-only.
- Generated output must remain within `ImportLimits.maxGeneratedChars()` before it crosses a trust boundary.
- `STRUCTURALLY_EQUIVALENT` is not a pixel-equivalence claim; only a passing pixel comparison yields `VISUALLY_QUALIFIED`.
- GL compilation, preview, comparison, and disposal run only on the application render thread.
- MCP schemas remain closed and reject unknown fields, duplicate/unsupported target profiles, and oversized source.
- Every task follows test-first red-green-refactor and ends with an independently reviewable commit.

---

### Task 1: Add importer modules, ADR, and immutable import contracts

**Files:**
- Create: `docs/adr/0003-general-vfx-runtime-and-import-boundary.md`
- Create: `effects-import/build.gradle.kts`
- Create: `effects-import/gradle.lockfile`
- Create: `effects-import/src/main/java/io/github/teemuki8/libgdx/agent/effects/importer/package-info.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/EffectDefinition.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/AssetKey.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/BlendMode.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/Material2dDefinition.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/Material3dDefinition.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/ShaderTargetProfile.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/ShaderSemantic.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/SourceSpan.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/ImportLimits.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/ImportDiagnosticSeverity.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/ImportDiagnostic.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/FeatureMapping.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/FidelityClassification.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/GeneratedShader.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/SourceMapping.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/ShaderImportRequest.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/ShaderImportResult.java`
- Create: `effects-core/src/test/java/io/github/teemuki8/libgdx/agent/effects/core/ShaderImportModelTest.java`
- Modify: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/EffectsException.java`
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`

**Interfaces:**
- Consumes: existing `ShaderSource`, `UniformBinding`, `EffectsLimits`, and `EffectsException`.
- Produces:

```java
public sealed interface EffectDefinition permits Material2dDefinition, Material3dDefinition {
    String name();
    EffectDefinition validate(EffectsLimits limits);
}

public enum ShaderTargetProfile { GLSL_ES_100, GLSL_ES_300 }

public record ShaderImportRequest(
        String name, String source, List<ShaderTargetProfile> targets) { }

public record ShaderImportResult(
        String name,
        Material2dDefinition material,
        List<GeneratedShader> generatedShaders,
        List<ShaderSemantic> requiredSemantics,
        List<FeatureMapping> featureMappings,
        List<ImportDiagnostic> diagnostics,
        FidelityClassification fidelity) { }
```

`material` is nullable only for `UNSUPPORTED`; all list components are non-null immutable copies.
`SourceSpan` stores one-based start/end line and column plus zero-based start/end offsets.

- [ ] **Step 1: Write failing model-validation tests**

Cover nulls, blanks, invalid source spans, duplicate targets, invalid asset keys, non-deterministic
input list mutation, diagnostics beyond limits, generated shader text beyond limits, and the rule
that successful classifications require a material.

- [ ] **Step 2: Run focused core tests and confirm missing types**

Run: `./gradlew :effects-core:test --tests '*ShaderImportModelTest'`

Expected: FAIL at test compilation because the import contracts do not exist.

- [ ] **Step 3: Implement bounded immutable records**

Use explicit `validate(ImportLimits)` methods. `ImportLimits.developmentDefaults()` returns exact
finite defaults for source chars, generated chars, tokens, AST depth, declarations, functions,
parameters, statements, expression nodes, array elements, diagnostics, and feature mappings.

Add `INVALID_IMPORT` and `UNSUPPORTED_FEATURE` to `EffectsException.Kind` without changing existing
kind names.

- [ ] **Step 4: Add modules and dependency metadata**

`effects-import` declares only:

```kotlin
dependencies {
    api(project(":effects-core"))
}
```

Add `effects-import` to `settings.gradle.kts`, `publishedModules`, and `artifactNames`. Runtime
module creation remains in the master plan's runtime-lifecycle task. Generate locks without
changing unrelated dependency versions.

- [ ] **Step 5: Write ADR 0003 and package Javadocs**

Record the approved high-level-runtime/libGDX-primitives boundary, explicit application lifecycle,
new module direction, no arbitrary resource paths, and render-thread confinement.

- [ ] **Step 6: Run core and check gates**

Run: `.agents/skills/libgdx-agent-effects-dev/scripts/verify.sh core`

Run: `.agents/skills/libgdx-agent-effects-dev/scripts/verify.sh check`

Expected: both PASS.

- [ ] **Step 7: Commit**

```bash
git add settings.gradle.kts build.gradle.kts docs/adr/0003-general-vfx-runtime-and-import-boundary.md effects-core effects-import
git commit -m "Add bounded shader import contracts"
```

### Task 2: Implement the bounded Godot shader lexer

**Files:**
- Create: `effects-import/src/main/java/io/github/teemuki8/libgdx/agent/effects/importer/godot/GodotTokenKind.java`
- Create: `effects-import/src/main/java/io/github/teemuki8/libgdx/agent/effects/importer/godot/GodotToken.java`
- Create: `effects-import/src/main/java/io/github/teemuki8/libgdx/agent/effects/importer/godot/GodotLexer.java`
- Create: `effects-import/src/main/java/io/github/teemuki8/libgdx/agent/effects/importer/godot/GodotImportException.java`
- Test: `effects-import/src/test/java/io/github/teemuki8/libgdx/agent/effects/importer/godot/GodotLexerTest.java`

**Interfaces:**
- Consumes: source string and `ImportLimits`.
- Produces:

```java
final class GodotLexer {
    GodotLexer(String source, ImportLimits limits);
    List<GodotToken> lex();
}

record GodotToken(GodotTokenKind kind, String lexeme, SourceSpan span) { }
```

Token kinds cover identifiers, integer/float literals, boolean literals, operators, punctuation,
keywords, preprocessor markers, and EOF. Comments and whitespace are skipped while positions remain
exact.

- [ ] **Step 1: Write lexer success tests**

Use one source containing `shader_type`, `render_mode`, uniforms with hints/defaults, comments,
scientific/hex numeric literals, swizzles, compound operators, a function, and `fragment()`.
Assert exact token kinds, lexemes, and representative multiline spans.

- [ ] **Step 2: Write lexer rejection and bound tests**

Cover unterminated block comments, malformed numeric literals, unexpected characters, preprocessor
`#include`, source length, token count, and a final EOF span.

- [ ] **Step 3: Run lexer tests and confirm failure**

Run: `./gradlew :effects-import:test --tests '*GodotLexerTest'`

Expected: FAIL at compilation.

- [ ] **Step 4: Implement a single-pass character lexer**

Track offset, one-based line, and one-based column while consuming. Store only bounded lexemes;
never accumulate comments or oversized error excerpts. `#include` produces a stable
`UNRESOLVED_INCLUDE` diagnostic code through `GodotImportException`.

- [ ] **Step 5: Run importer tests and Javadocs**

Run: `./gradlew :effects-import:test :effects-import:javadoc --warning-mode=fail`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add effects-import
git commit -m "Add bounded Godot shader lexer"
```

### Task 3: Implement the typed Godot `canvas_item` AST and parser

**Files:**
- Create: `effects-import/src/main/java/io/github/teemuki8/libgdx/agent/effects/importer/godot/GodotAst.java`
- Create: `effects-import/src/main/java/io/github/teemuki8/libgdx/agent/effects/importer/godot/GodotParser.java`
- Test: `effects-import/src/test/java/io/github/teemuki8/libgdx/agent/effects/importer/godot/GodotParserTest.java`

**Interfaces:**
- Consumes: immutable tokens and `ImportLimits`.
- Produces:

```java
record GodotShaderAst(
        String shaderType,
        List<String> renderModes,
        List<Declaration> declarations,
        List<FunctionDeclaration> functions,
        SourceSpan span) { }

final class GodotParser {
    GodotParser(List<GodotToken> tokens, ImportLimits limits);
    GodotShaderAst parse();
}
```

`GodotAst` contains sealed immutable declaration, statement, and expression variants. Every node
carries `SourceSpan`. The grammar includes uniforms/hints/defaults, constants, structs, bounded
arrays, functions, blocks, declarations, assignments, `if/else`, `for`, `while`, `return`, `break`,
`continue`, calls, indexing, member access, unary/binary/ternary expressions, and constructors.

- [ ] **Step 1: Write parser tests for supported declarations and processors**

Parse a shader with render modes, uniform hints/defaults, a struct, helper function, `vertex()`, and
`fragment()`. Assert typed node variants, stable declaration order, operator precedence, and spans.

- [ ] **Step 2: Write malformed and bounded-AST tests**

Cover missing semicolons/braces, duplicate `shader_type`, non-`canvas_item` type, duplicate processor
functions, excess nesting, declarations, functions, parameters, statements, expression nodes, and
array elements. Diagnostics name the unexpected token and expected grammar category without dumping
the whole source.

- [ ] **Step 3: Run parser tests and confirm failure**

Run: `./gradlew :effects-import:test --tests '*GodotParserTest'`

Expected: FAIL at compilation.

- [ ] **Step 4: Implement recursive-descent declarations/statements and Pratt expressions**

Increment bounds before allocating each node/list entry. Use immutable `List.copyOf` at every AST
boundary. Reject non-`canvas_item` immediately after the shader-type declaration.

- [ ] **Step 5: Run all import tests**

Run: `./gradlew :effects-import:test :effects-import:javadoc --warning-mode=fail`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add effects-import
git commit -m "Parse typed Godot canvas shaders"
```

### Task 4: Translate Godot semantics and generate bounded target GLSL

**Files:**
- Create: `effects-import/src/main/java/io/github/teemuki8/libgdx/agent/effects/importer/godot/GodotCanvasImporter.java`
- Create: `effects-import/src/main/java/io/github/teemuki8/libgdx/agent/effects/importer/godot/GodotSemanticAnalyzer.java`
- Create: `effects-import/src/main/java/io/github/teemuki8/libgdx/agent/effects/importer/godot/GodotGlslGenerator.java`
- Create: `effects-import/src/main/java/io/github/teemuki8/libgdx/agent/effects/importer/godot/GeneratedSourceBuilder.java`
- Test: `effects-import/src/test/java/io/github/teemuki8/libgdx/agent/effects/importer/godot/GodotCanvasImporterTest.java`

**Interfaces:**
- Consumes: `ShaderImportRequest` and `ImportLimits`.
- Produces:

```java
public final class GodotCanvasImporter {
    public GodotCanvasImporter(ImportLimits limits);
    public ShaderImportResult importShader(ShaderImportRequest request);
}
```

The analyzer produces a closed feature inventory and required semantics. The generator emits one
vertex/fragment pair per requested target profile and source mappings for every copied or generated
region.

- [ ] **Step 1: Write a direct-mapping importer test**

Input:

```glsl
shader_type canvas_item;
uniform float intensity : hint_range(0.0, 1.0) = 0.5;
void fragment() {
    vec4 sampled = texture(TEXTURE, UV);
    COLOR = vec4(sampled.rgb * intensity, sampled.a);
}
```

Assert `STRUCTURALLY_EQUIVALENT`, stable mappings for `TEXTURE`, `UV`, and `COLOR`, a declared
`u_source` sampler, a typed intensity uniform, no error diagnostics, bounded ES100/ES300 output,
and fragment assignment to `gl_FragColor` or the ES300 output variable.

- [ ] **Step 2: Write built-in and approximation tests**

Cover `TIME`, `TEXTURE_PIXEL_SIZE`, `SCREEN_UV`, screen texture input, vertex position, representable
blend modes, and `render_mode blend_premul_alpha`. The premultiplied blend fixture maps to the
closest supported blend state and returns `APPROXIMATED` with stable code
`GODOT_BLEND_PREMULTIPLIED_APPROXIMATION` and its source span.

- [ ] **Step 3: Write rejection and generator-bound tests**

Cover `light()`, SDF built-ins, instance/global uniforms, unknown render modes, unavailable depth
input, unresolved semantic names, recursion, unsafe dynamic loop structure, duplicate user symbols,
generated character count, diagnostic count, and feature-mapping count.

- [ ] **Step 4: Run importer tests and confirm failure**

Run: `./gradlew :effects-import:test --tests '*GodotCanvasImporterTest'`

Expected: FAIL at compilation.

- [ ] **Step 5: Implement semantic analysis and explicit built-in mapping**

Use a closed map equivalent to:

```java
Map.entry("UV", ShaderSemantic.UV),
Map.entry("COLOR", ShaderSemantic.VERTEX_COLOR),
Map.entry("TEXTURE", ShaderSemantic.SOURCE_TEXTURE),
Map.entry("TEXTURE_PIXEL_SIZE", ShaderSemantic.SOURCE_TEXEL_SIZE),
Map.entry("TIME", ShaderSemantic.TIME),
Map.entry("SCREEN_UV", ShaderSemantic.SCREEN_UV)
```

Treat read/write direction separately: reading `COLOR` uses vertex color; assigning `COLOR` writes
the fragment output. Do not perform blind identifier replacement.

- [ ] **Step 6: Implement deterministic ES100 and ES300 generation**

ES100 uses `attribute`, `varying`, `texture2D`, and `gl_FragColor`. ES300 uses `in`, `out`,
`texture`, and an explicitly declared fragment output. Both use guarded precision declarations and
derive quad UV from `a_position` when no host UV attribute is supplied.

- [ ] **Step 7: Run import and core gates**

Run: `./gradlew :effects-core:check :effects-import:check :effects-import:javadoc --warning-mode=fail`

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add effects-core effects-import
git commit -m "Translate Godot canvas shaders to bounded GLSL"
```

### Task 5: Compile, preview, and visually qualify imported shaders under real GL

**Files:**
- Create: `effects-libgdx/src/main/java/io/github/teemuki8/libgdx/agent/effects/libgdx/ImportedMaterialAdapter.java`
- Create: `effects-libgdx/src/main/java/io/github/teemuki8/libgdx/agent/effects/libgdx/ShaderImportQualifier.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/ShaderQualificationResult.java`
- Create: `effects-libgdx/src/test/java/io/github/teemuki8/libgdx/agent/effects/libgdx/ShaderImportQualifierTest.java`
- Create: `effects-fixtures/src/test/resources/godot/direct-color.gdshader`
- Create: `effects-fixtures/src/test/resources/godot/direct-color-reference.png`
- Create: `effects-fixtures/src/test/resources/godot/direct-color-reference.provenance.json`
- Create: `effects-fixtures/src/test/resources/godot/premultiplied.gdshader`
- Create: `effects-fixtures/src/test/java/io/github/teemuki8/libgdx/agent/effects/fixtures/GodotCanvasImportFixtureTest.java`
- Modify: `effects-fixtures/build.gradle.kts`

**Interfaces:**
- Consumes: generated profile, supplied `RgbaImage` textures/uniforms, optional reference image, `EffectsLimits`, and `ImportLimits`.
- Produces:

```java
public final class ShaderImportQualifier {
    public ShaderQualificationResult qualify(
            ShaderImportResult imported,
            ShaderTargetProfile target,
            List<UniformBinding> bindings,
            int width,
            int height,
            RgbaImage reference,
            PixelComparisonSpec comparison);
}
```

The result contains structured compile diagnostics, preview image or artifact receipt, optional
pixel-comparison result, and evidence-derived fidelity.

- [ ] **Step 1: Write a failing real-GL qualification test**

Import `direct-color.gdshader`, bind a deterministic 8x8 source texture, compile its ES100 output,
render a 16x16 canvas quad, and compare it with the provenance-backed reference PNG at the declared
tolerance. Assert `VISUALLY_QUALIFIED` only when compilation and comparison both pass.

- [ ] **Step 2: Write failure/state-restoration tests**

Cover compile failure, missing sampler, wrong thread, reference-dimension mismatch, absent reference
yielding `UNQUALIFIED`, approximation remaining `APPROXIMATED`, and restoration of framebuffer,
viewport, program, active texture, blend, depth, and cull state.

- [ ] **Step 3: Run the Xvfb test and confirm missing adapter failure**

Run: `xvfb-run -a ./gradlew :effects-libgdx:test --tests '*ShaderImportQualifierTest'`

Expected: FAIL at compilation.

- [ ] **Step 4: Implement the adapter using existing compiler/preview/comparer contracts**

The adapter creates a temporary validated `EffectDescription`; it does not add generated effects to
the application registry and does not dispose application-supplied image data. Close every compiled
program and framebuffer on all paths.

- [ ] **Step 5: Add reference fixture and provenance**

The provenance JSON records Godot version, renderer, project settings, shader SHA-256, input texture
SHA-256, render dimensions, and capture command. The fixture never regenerates or overwrites the
reference during ordinary tests.

- [ ] **Step 6: Run libGDX and fixture gates**

Run: `.agents/skills/libgdx-agent-effects-dev/scripts/verify.sh libgdx`

Run: `.agents/skills/libgdx-agent-effects-dev/scripts/verify.sh fixture`

Expected: both PASS under Xvfb.

- [ ] **Step 7: Commit**

```bash
git add effects-core effects-libgdx effects-fixtures
git commit -m "Qualify imported Godot shaders under real GL"
```

### Task 6: Expose bounded import through protocol and MCP

**Files:**
- Create: `effects-protocol/src/main/java/io/github/teemuki8/libgdx/agent/effects/protocol/EffectsImportBackend.java`
- Modify: `effects-protocol/src/main/java/io/github/teemuki8/libgdx/agent/effects/protocol/Requests.java`
- Modify: `effects-protocol/src/main/java/io/github/teemuki8/libgdx/agent/effects/protocol/Results.java`
- Modify: `effects-protocol/src/main/java/io/github/teemuki8/libgdx/agent/effects/protocol/EffectsProtocolService.java`
- Modify: `effects-protocol/src/main/java/io/github/teemuki8/libgdx/agent/effects/protocol/EffectsProtocol.java`
- Modify: `effects-mcp/src/main/java/io/github/teemuki8/libgdx/agent/effects/mcp/EffectsToolCatalog.java`
- Modify: `effects-mcp/src/main/java/io/github/teemuki8/libgdx/agent/effects/mcp/EffectsToolHandler.java`
- Modify: `effects-fixtures/src/main/java/io/github/teemuki8/libgdx/agent/effects/fixtures/EffectsFixtureBackend.java`
- Modify: `effects-protocol/src/test/java/io/github/teemuki8/libgdx/agent/effects/protocol/EffectsJsonTest.java`
- Modify: `effects-mcp/src/test/java/io/github/teemuki8/libgdx/agent/effects/mcp/EffectsToolCatalogTest.java`
- Modify: `effects-mcp/src/test/java/io/github/teemuki8/libgdx/agent/effects/mcp/EffectsToolHandlerTest.java`
- Modify: `effects-fixtures/src/test/java/io/github/teemuki8/libgdx/agent/effects/fixtures/EffectsFixtureBackendTest.java`

**Interfaces:**
- Consumes: bounded source, stable import name, and one or two target profiles.
- Produces:

```java
public interface EffectsImportBackend {
    CompletionStage<Results.ImportShaderResult> importGodotCanvas(
            Requests.ImportGodotCanvasRequest request);
}

public record ImportGodotCanvasRequest(
        String name, String source, List<ShaderTargetProfile> targetProfiles) { }
```

MCP adds exactly one tool, `effect_import_godot_canvas`, with closed properties `name`, `source`,
and `targetProfiles`. It returns bounded generated sources, required semantics, feature mappings,
diagnostics, and fidelity; it does not register or persist the imported effect.

- [ ] **Step 1: Write protocol JSON tests**

Round-trip direct and approximated results. Reject unknown fields, missing/blank name/source,
unsupported/duplicate/empty target lists, scalar coercion, oversized source, excessive JSON nesting,
and unsupported schema version.

- [ ] **Step 2: Write MCP catalog and handler tests**

Assert the exact six-tool stable order, `additionalProperties: false`, source `maxLength`, target
enum/array bounds, backend-not-wired `NOT_AVAILABLE`, malformed input `INVALID_QUERY`, bounded
successful structured content, importer exception mapping, and no source echo in error messages.

- [ ] **Step 3: Run protocol and MCP tests and confirm failure**

Run: `./gradlew :effects-protocol:test :effects-mcp:test`

Expected: FAIL because the import backend and tool do not exist.

- [ ] **Step 4: Implement the separate import backend seam and schemas**

Do not add a dependency from protocol/MCP to `effects-import`. Wire the actual importer in the
fixture/application backend. Keep JSON result encoding off the render thread.

- [ ] **Step 5: Run protocol, MCP, and fixture gates**

Run: `.agents/skills/libgdx-agent-effects-dev/scripts/verify.sh protocol`

Run: `.agents/skills/libgdx-agent-effects-dev/scripts/verify.sh mcp`

Run: `.agents/skills/libgdx-agent-effects-dev/scripts/verify.sh fixture`

Expected: all PASS.

- [ ] **Step 6: Commit**

```bash
git add effects-protocol effects-mcp effects-fixtures
git commit -m "Expose bounded Godot shader import tools"
```

### Task 7: Document, audit, and run the full importer gate

**Files:**
- Create: `docs/guides/godot-import.md`
- Modify: `README.md`
- Modify: `docs/roadmap.md`
- Modify: `SECURITY.md`
- Modify: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/package-info.java`
- Modify: `effects-protocol/src/main/java/io/github/teemuki8/libgdx/agent/effects/protocol/package-info.java`
- Modify: `effects-mcp/src/main/java/io/github/teemuki8/libgdx/agent/effects/mcp/package-info.java`

**Interfaces:**
- Consumes: completed Java, protocol, MCP, and fixture slice.
- Produces: exact supported/unsupported syntax, lifecycle, security, fidelity, target-profile, and qualification guidance.

- [ ] **Step 1: Write guide examples from passing fixtures**

Show Java import, MCP request/response, direct mapping, approximation, missing semantic, profile
selection, and qualification. State that only `canvas_item` is supported and that generated output
is not automatically persisted or installed into a game.

- [ ] **Step 2: Update capability and security documentation**

Replace README's fixed five-tool statement with the exact six-tool importer slice. Document parser
bounds, no paths/includes, driver compilation boundary, source-map diagnostics, and why compilation
alone is not visual qualification.

- [ ] **Step 3: Audit the diff and public API**

Run: `git diff --check`

Run: `git status --short`

Search: `rg -n "TODO|TBD|five-tool|arbitrary path|canvas_item" README.md SECURITY.md docs effects-*`

Expected: no placeholders or stale five-tool claims; every changed file traces to the importer slice.

- [ ] **Step 4: Run the complete local gate**

Run: `.agents/skills/libgdx-agent-effects-dev/scripts/verify.sh full`

Expected: PASS under Xvfb with clean tests, native fixtures, publication archives, dependency locks,
and warning-free Javadocs.

- [ ] **Step 5: Commit**

```bash
git add README.md SECURITY.md docs effects-core effects-protocol effects-mcp
git commit -m "Document the Godot canvas importer"
```
