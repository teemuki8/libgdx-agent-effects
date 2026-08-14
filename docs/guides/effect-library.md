# Effect library guide

The optional `effects-library` module is a JDK-only catalog over the immutable definitions in
`effects-core`. Add it with the released version used by the rest of the effects stack:

```kotlin
implementation("io.github.teemuki8:agent-effects-library:<version>")
```

It does not own libGDX, a render loop, assets, storage, or network access. Applications decide which
catalogs to construct and where, if anywhere, catalog metadata is persisted.

## Search bundled effects

`BuiltInEffectCatalog` contains six original Apache-2.0 entries: `damage-pulse`, `neon-edges`,
`ship-trail`, `energy-beam`, `arc-lightning`, and `sparks`. Search always includes explicit target
capabilities:

```java
EffectCatalog catalog = BuiltInEffectCatalog.create(
        CatalogLimits.developmentDefaults(), EffectsLimits.developmentDefaults());
EffectCapabilities target = new EffectCapabilities(
        3, 2, 8192, true, EffectCapabilities.Profile.DESKTOP_OPENGL);
EffectCatalogSearchResult trails = catalog.search(
        new EffectCatalogQuery(target, EffectFamily.TRAIL, List.of("space"), 20));
EffectDefinition definition = trails.matches().getFirst().variant().definition();
```

The catalog selects one compatible variant per entry and returns matches in stable entry-ID order.
An exact `find(id, target)` follows the same compatibility selection. Incompatible entries are
absent from both operations; callers cannot use catalog discovery to enumerate hidden metadata.

The six bundled variants are qualified only for desktop OpenGL, with a minimum OpenGL 2.0 target.
They are not qualified for mobile OpenGL ES or WebGL and therefore do not appear for those target
profiles. Applications should add variants only after qualifying them on each target they declare.

## Assets and local catalogs

Entries refer only to logical `AssetKey` values. The consumer resolves those keys to textures,
captures, atlas regions, or other application-owned resources and retains responsibility for
loading and disposal. The library does not embed paths, open files, download content, or persist a
catalog.

Use `InMemoryEffectCatalog` for an application-owned set of entries. Its constructor validates the
configured catalog/effect limits and rejects duplicate IDs. There are no loaders or remote-pack
registries; persistence and license review remain application concerns.

## Admit an imported shader

The supported shader migration path is the documented Godot 4 `canvas_item` importer. There is no
Unity or Unreal shader importer. Import output is evidence, not automatic registration:

1. Import bounded source text with `effects-import`.
2. Compile and render the generated target on the application render thread with
   `ShaderImportQualifier` from `effects-libgdx`.
3. Pass the immutable import result, qualification result, and the capabilities actually qualified
   to `QualifiedShaderCatalogEntry.create`.
4. Add the returned entry to an application-owned catalog.

Admission requires a supported imported material, a compiled structured diagnostic, a rendered
preview, exactly one generated shader matching the qualified target, and explicit non-unknown
capabilities. `APPROXIMATED` and `UNQUALIFIED` fidelity remain admissible evidence; unsupported,
failed, previewless, or target-mismatched results are rejected and never appear in the catalog.
The caller supplies provenance, license, and attribution metadata and remains responsible for its
accuracy.

## MCP discovery

An application may register any core `EffectCatalog` with `EffectsProtocolService.catalog`. The
closed `effect_catalog_search` and `effect_catalog_get` tools accept flat, explicit graphics
capabilities and expose only compatible matches. Without application registration they return
`NOT_AVAILABLE`; a missing or incompatible exact lookup returns the same generic `UNKNOWN_EFFECT`
result.
