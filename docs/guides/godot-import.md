# Godot canvas shader import

The Godot importer translates explicitly supplied Godot 4 `canvas_item` shader text into bounded
GLSL ES 100 and/or GLSL ES 300 material sources. It is a migration assistant: it reports direct
mappings, approximations, and unsupported features. Successful generation is not by itself a claim
of pixel equivalence.

## Java API

```java
var importer = new GodotCanvasImporter(ImportLimits.developmentDefaults());
var imported = importer.importShader(new ShaderImportRequest(
    "ship-glow",
    shaderSource,
    List.of(ShaderTargetProfile.GLSL_ES_100, ShaderTargetProfile.GLSL_ES_300)));
```

The result contains a `Material2dDefinition`, generated shader pairs, required host semantics,
source-located feature mappings, diagnostics, and a fidelity classification. An unsupported import
has no material or generated sources. Import does not register, save, compile, or install anything
in the game.

The current grammar supports uniforms with hints/defaults, constants, structs, varyings, helper
functions, `vertex()` and `fragment()`, typed local variables, assignments, conditionals, bounded
`for` loops, returns, calls, indexing, member access, and ordinary unary/binary/ternary expressions.
The portable uniform-value adapter covers scalar and vector defaults.

Direct built-in mappings include `VERTEX`, `UV`, `COLOR`, `TEXTURE`, `TEXTURE_PIXEL_SIZE`, `TIME`,
`SCREEN_UV`, and a sampler declared with `hint_screen_texture`. Supported blend modes are mix,
additive, multiply, and subtract. `blend_premul_alpha` is imported as normal alpha blending with a
source-located approximation warning.

The importer rejects non-canvas shaders, `light()`, SDF/depth inputs, unknown render modes,
recursion, `while` loops, loops without a condition, preprocessing/includes, malformed source, and
configured source/token/AST/output/evidence limit violations. It never resolves a filename or an
include.

## MCP

Applications may separately wire an `EffectsImportBackend`. The closed local-stdio tool is:

```json
{
  "name": "effect_import_godot_canvas",
  "arguments": {
    "name": "ship-glow",
    "source": "shader_type canvas_item; void fragment(){ COLOR=vec4(1.0); }",
    "targetProfiles": ["GLSL_ES_100"]
  }
}
```

The schema rejects unknown properties, paths, empty or duplicate targets, unsupported target names,
and source over the protocol limit. If no importer is wired, the tool returns `NOT_AVAILABLE`.

## Compilation and visual qualification

`ShaderImportQualifier` must be created and used on the application's render thread. Supply every
required sampler as a bounded `UniformBinding`; the adapter never looks up an asset itself. The
qualifier compiles and previews through libGDX under the selected profile. With no reference image,
successful compilation is `UNQUALIFIED`. A direct import becomes `VISUALLY_QUALIFIED` only after its
preview passes the supplied `PixelComparisonSpec`; an approximated import remains `APPROXIMATED`.

Reference images intended to prove Godot equivalence should be captured from a pinned Godot version
and renderer and stored with shader, input, project-setting, dimension, and capture provenance.

## Using imported materials in wider effects

`Material2dDefinition` is deliberately independent of geometry. The approved general-VFX program
uses the same material contract for sprites, trail/ribbon meshes, particles, beams, lightning,
decals, and distortion producers. The importer supplies the material; each runtime effect still
supplies its own bounded simulation, geometry, lifecycle, and host semantics.
