# Reusable Effect Library Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an optional, target-aware catalog containing a small set of qualified reusable effects and a safe admission path for successfully imported shaders.

**Architecture:** `effects-core` owns the immutable catalog contract and target matching. A new JDK-only `effects-library` module supplies an in-memory implementation, bundled Apache-2.0 entries, and a qualified-import adapter. Existing protocol and MCP layers register the generic `EffectCatalog` interface; libGDX fixtures remain the authority for native qualification.

**Tech Stack:** Java 25 records and sealed interfaces, Gradle 9.6.1 Kotlin DSL, JUnit 5, Jackson 2.22, MCP Java SDK 2.0, libGDX 1.14.2, LWJGL3/Xvfb.

## Global Constraints

- The catalog is generic and has no Gnosis dependency, types, persistence, or project knowledge.
- Keep `effects-core`, `effects-library`, `effects-import`, and `effects-runtime` JDK-only.
- Preserve application ownership of the loop, render thread, GL resources, assets, and disposal.
- Ordinary search and lookup never return an entry without a variant compatible with the supplied `EffectCapabilities`.
- An imported shader is admitted only after successful real-GL compilation and preview evidence.
- Bundled content is original Apache-2.0 content in the repository; local catalog persistence belongs to the consumer.
- Do not add remote packs, marketplaces, databases, indexing engines, dependency solvers, signatures for local packs, filesystem paths, or network access.
- All public records validate, defensively copy collections, have warning-free Javadocs, and obey configured hard bounds.
- Protocol JSON and MCP schemas remain closed and reject unknown fields.
- Native catalog qualification runs under Xvfb on Linux.

---

### Task 1: Put effect families and capability matching in core

**Files:**
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/EffectFamily.java`
- Delete: `effects-protocol/src/main/java/io/github/teemuki8/libgdx/agent/effects/protocol/EffectFamily.java`
- Modify: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/EffectDefinition.java`
- Modify: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/PostProcessGraphDefinition.java`
- Modify: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/EffectCapabilities.java`
- Modify: `effects-protocol/src/main/java/io/github/teemuki8/libgdx/agent/effects/protocol/EffectsProtocolService.java`
- Modify: `effects-protocol/src/main/java/io/github/teemuki8/libgdx/agent/effects/protocol/Results.java`
- Modify: `effects-protocol/src/test/java/io/github/teemuki8/libgdx/agent/effects/protocol/EffectsJsonTest.java`
- Modify: `effects-mcp/src/test/java/io/github/teemuki8/libgdx/agent/effects/mcp/EffectsToolHandlerTest.java`
- Modify: `effects-fixtures/src/test/java/io/github/teemuki8/libgdx/agent/effects/fixtures/EffectsFixtureBackendTest.java`
- Test: `effects-core/src/test/java/io/github/teemuki8/libgdx/agent/effects/core/EffectCapabilitiesTest.java`
- Test: `effects-core/src/test/java/io/github/teemuki8/libgdx/agent/effects/core/EffectFamilyTest.java`
- Create: `effects-core/src/test/java/io/github/teemuki8/libgdx/agent/effects/core/CatalogTestFixtures.java`

**Interfaces:**
- Produces: `EffectFamily.from(EffectDefinition)` and `EffectCapabilities.satisfies(EffectCapabilities)`.
- Produces: `PostProcessGraphDefinition implements EffectDefinition`, making all reusable general effects one closed core type.

- [ ] **Step 1: Write failing capability and family tests**

```java
@Test
void actualCapabilitiesMustMeetProfileVersionTextureAndFloatRequirements() {
    EffectCapabilities actual = new EffectCapabilities(4, 1, 8192, true,
            EffectCapabilities.Profile.DESKTOP_OPENGL);
    assertTrue(actual.satisfies(new EffectCapabilities(3, 2, 4096, true,
            EffectCapabilities.Profile.DESKTOP_OPENGL)));
    assertFalse(actual.satisfies(new EffectCapabilities(3, 0, 4096, false,
            EffectCapabilities.Profile.OPENGL_ES)));
    assertFalse(new EffectCapabilities(4, 1, 8192, true,
            EffectCapabilities.Profile.UNKNOWN).satisfies(actual));
}

@Test
void derivesClosedFamiliesIncludingPostProcessGraphs() {
    assertEquals(EffectFamily.TRAIL,
            EffectFamily.from(CatalogTestFixtures.trail("trail")));
    assertEquals(EffectFamily.POST_PROCESS_GRAPH,
            EffectFamily.from(CatalogTestFixtures.postProcessGraph("graph")));
}
```

Add the package-private `CatalogTestFixtures` factory in the same test package with the smallest
valid trail and post-process definitions. Reuse it in Task 2 instead of duplicating constructor
noise in each test.

- [ ] **Step 2: Run the focused tests and verify the missing APIs fail compilation**

Run:

```bash
./gradlew :effects-core:test --tests '*EffectCapabilitiesTest' --tests '*EffectFamilyTest'
```

Expected: FAIL because `satisfies`, core `EffectFamily`, and graph participation in `EffectDefinition` do not exist.

- [ ] **Step 3: Move the enum and implement minimum-capability matching**

```java
public enum EffectFamily {
    LEGACY_SHADER, MATERIAL_2D, MATERIAL_3D, TRAIL, BEAM, LIGHTNING,
    PARTICLE, DECAL, DISTORTION, POST_PROCESS_GRAPH;

    public static EffectFamily from(EffectDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        return switch (definition) {
            case Material2dDefinition ignored -> MATERIAL_2D;
            case Material3dDefinition ignored -> MATERIAL_3D;
            case TrailDefinition ignored -> TRAIL;
            case BeamDefinition ignored -> BEAM;
            case LightningDefinition ignored -> LIGHTNING;
            case ParticleDefinition ignored -> PARTICLE;
            case DecalDefinition ignored -> DECAL;
            case DistortionFieldDefinition ignored -> DISTORTION;
            case PostProcessGraphDefinition ignored -> POST_PROCESS_GRAPH;
        };
    }
}
```

Add `PostProcessGraphDefinition` to `EffectDefinition.permits`, implement the interface on the
record, and add this method to `EffectCapabilities`:

```java
public boolean satisfies(EffectCapabilities required) {
    Objects.requireNonNull(required, "required");
    if (profile == Profile.UNKNOWN || required.profile == Profile.UNKNOWN
            || profile != required.profile) {
        return false;
    }
    boolean version = glMajor > required.glMajor
            || glMajor == required.glMajor && glMinor >= required.glMinor;
    return version && maxTextureSize >= required.maxTextureSize
            && (!required.floatTextures || floatTextures);
}
```

Update protocol/MCP imports to the core enum without changing serialized enum names.

- [ ] **Step 4: Run core and protocol gates**

Run:

```bash
.agents/skills/libgdx-agent-effects-dev/scripts/verify.sh core
.agents/skills/libgdx-agent-effects-dev/scripts/verify.sh protocol
```

Expected: PASS, including existing family-summary JSON tests.

- [ ] **Step 5: Commit the core vocabulary change**

```bash
git add effects-core effects-protocol effects-mcp
git commit -m "Unify effect families and target matching"
```

---

### Task 2: Add the immutable catalog contract

**Files:**
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/CatalogLimits.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/EffectCatalog.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/EffectCatalogEntry.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/EffectCatalogVariant.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/EffectCatalogQuery.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/EffectCatalogMatch.java`
- Create: `effects-core/src/main/java/io/github/teemuki8/libgdx/agent/effects/core/EffectCatalogSearchResult.java`
- Test: `effects-core/src/test/java/io/github/teemuki8/libgdx/agent/effects/core/EffectCatalogModelTest.java`

**Interfaces:**
- Consumes: `EffectFamily.from`, `EffectCapabilities.satisfies`, `EffectDefinition.validate`.
- Produces: `EffectCatalog.search(EffectCatalogQuery)` and `EffectCatalog.find(String, EffectCapabilities)`.

- [ ] **Step 1: Write failing model tests for immutability, bounds, and target visibility**

```java
@Test
void variantRejectsUnknownOrDuplicateQualificationTargets() {
    EffectDefinition effect = CatalogTestFixtures.trail("ship-trail");
    EffectCapabilities desktop = desktopGl2();
    assertThrows(IllegalArgumentException.class, () -> new EffectCatalogVariant(
            "portable", 0, effect, List.of(desktop, desktop)));
    assertThrows(IllegalArgumentException.class, () -> new EffectCatalogVariant(
            "unknown", 0, effect, List.of(new EffectCapabilities(
                    3, 0, 4096, false, EffectCapabilities.Profile.UNKNOWN))));
}

@Test
void entryRequiresOneFamilyAcrossVariantsAndDefensivelyCopies() {
    List<String> tags = new ArrayList<>(List.of("space", "trail"));
    EffectCatalogEntry entry = CatalogTestFixtures.entry("ship-trail", tags,
            CatalogTestFixtures.variant(
                    CatalogTestFixtures.trail("ship-trail"), desktopGl2()));
    tags.clear();
    assertEquals(List.of("space", "trail"), entry.tags());
    assertEquals(EffectFamily.TRAIL, entry.family());
}
```

- [ ] **Step 2: Run the model test and verify it fails compilation**

Run:

```bash
./gradlew :effects-core:test --tests '*EffectCatalogModelTest'
```

Expected: FAIL because catalog records and limits do not exist.

- [ ] **Step 3: Implement the minimal public records**

Use these signatures:

```java
public record CatalogLimits(int maxEntries, int maxVariantsPerEntry,
        int maxTagsPerEntry, int maxResults, int maxTextChars) {
    public static CatalogLimits developmentDefaults() {
        return new CatalogLimits(1024, 16, 32, 256, 1024);
    }
}

public record EffectCatalogVariant(String id, int preference,
        EffectDefinition definition, List<EffectCapabilities> qualifiedTargets) {
    public boolean supports(EffectCapabilities actual) {
        return qualifiedTargets.stream().anyMatch(actual::satisfies);
    }
}

public record EffectCatalogEntry(String id, String version, String displayName,
        String description, EffectFamily family, List<String> tags,
        String license, String provenance, String attributionUrl,
        List<AssetKey> requiredAssets, List<EffectCatalogVariant> variants) {
    public EffectCatalogEntry validate(CatalogLimits catalogLimits, EffectsLimits effectsLimits) {
        // Enforce text/list bounds, unique variant IDs, declared family, and definition limits.
        return this;
    }
}

public record EffectCatalogQuery(EffectCapabilities target, EffectFamily family,
        List<String> tags, int limit) {}

public record EffectCatalogMatch(EffectCatalogEntry entry,
        EffectCatalogVariant variant) {}

public record EffectCatalogSearchResult(List<EffectCatalogMatch> matches,
        boolean truncated) {}

public interface EffectCatalog {
    EffectCatalogSearchResult search(EffectCatalogQuery query);
    Optional<EffectCatalogMatch> find(String id, EffectCapabilities target);
}
```

Validate lowercase IDs with `[a-z0-9]+(?:-[a-z0-9]+)*`, semantic entry versions with
`[0-9]+\.[0-9]+\.[0-9]+`, nonblank license/provenance, unique sorted tags, finite bounded
preferences, nonempty qualified targets, and no `Profile.UNKNOWN` qualification.

- [ ] **Step 4: Run focused core tests and Javadocs**

Run:

```bash
.agents/skills/libgdx-agent-effects-dev/scripts/verify.sh core
```

Expected: PASS with warning-free public Javadocs.

- [ ] **Step 5: Commit the catalog contract**

```bash
git add effects-core
git commit -m "Add immutable effect catalog contract"
```

---

### Task 3: Add the optional in-memory library module

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`
- Create: `effects-library/build.gradle.kts`
- Create: `effects-library/gradle.lockfile`
- Create: `effects-library/src/main/java/io/github/teemuki8/libgdx/agent/effects/library/InMemoryEffectCatalog.java`
- Test: `effects-library/src/test/java/io/github/teemuki8/libgdx/agent/effects/library/InMemoryEffectCatalogTest.java`
- Create: `effects-library/src/test/java/io/github/teemuki8/libgdx/agent/effects/library/LibraryTestFixtures.java`

**Interfaces:**
- Consumes: all Task 2 catalog values.
- Produces: `new InMemoryEffectCatalog(entries, catalogLimits, effectsLimits)`.

- [ ] **Step 1: Write failing query tests**

```java
@Test
void searchFiltersTargetFamilyAndTagsBeforeStableTruncation() {
    InMemoryEffectCatalog catalog = LibraryTestFixtures.mixed();
    EffectCatalogSearchResult result = catalog.search(new EffectCatalogQuery(
            desktopGl2(), EffectFamily.TRAIL, List.of("space"), 1));
    assertEquals(List.of("ship-trail"), result.matches().stream()
            .map(match -> match.entry().id()).toList());
    assertTrue(result.truncated());
}

@Test
void exactLookupDoesNotRevealIncompatibleEntry() {
    assertTrue(LibraryTestFixtures.mixed().find("gpu-sparks", desktopGl2()).isEmpty());
}

@Test
void constructorRejectsDuplicateEntryIds() {
    EffectCatalogEntry entry = LibraryTestFixtures.shipTrail();
    assertThrows(IllegalArgumentException.class, () -> new InMemoryEffectCatalog(
            List.of(entry, entry), CATALOG_LIMITS, EFFECTS_LIMITS));
}
```

Keep `LibraryTestFixtures` package-private and limited to the compatible trail, incompatible
particle, and mixed catalog used by these three tests.

- [ ] **Step 2: Add the module skeleton and verify the tests fail**

Add `effects-library` to `settings.gradle.kts`. Its build file contains only:

```kotlin
dependencies {
    api(project(":effects-core"))
}
```

Run:

```bash
./gradlew :effects-library:test --tests '*InMemoryEffectCatalogTest'
```

Expected: FAIL because `InMemoryEffectCatalog` does not exist.

- [ ] **Step 3: Implement one deterministic linear catalog**

```java
public final class InMemoryEffectCatalog implements EffectCatalog {
    public InMemoryEffectCatalog(List<EffectCatalogEntry> entries,
            CatalogLimits catalogLimits, EffectsLimits effectsLimits) { /* validate and sort */ }

    @Override public EffectCatalogSearchResult search(EffectCatalogQuery query) {
        // Walk stable ID order, select the lowest preference then variant ID,
        // require every query tag, cap at min(query.limit, limits.maxResults), report truncation.
    }

    @Override public Optional<EffectCatalogMatch> find(
            String id, EffectCapabilities target) { /* same variant selector as search */ }
}
```

Do not add indexes, caches, loaders, persistence, or pack formats. At the configured maximum of
1024 entries, a stable linear scan is sufficient and easiest to audit.

- [ ] **Step 4: Register the seventh published artifact and module boundary**

Add `effects-library` / `agent-effects-library` to `publishedModules` and `artifactNames`. Add
`"effects-library" to setOf(":effects-core")` to `verifyModuleBoundaries`.

Generate the lock and run the module gate:

```bash
./gradlew :effects-library:dependencies --write-locks
./gradlew :effects-library:check :effects-library:javadoc --warning-mode=fail
```

Expected: PASS and `effects-library/gradle.lockfile` contains no libGDX, Jackson, or MCP dependency.

- [ ] **Step 5: Commit the module and query implementation**

```bash
git add settings.gradle.kts build.gradle.kts effects-library
git commit -m "Add target-aware in-memory effect catalog"
```

---

### Task 4: Seed and natively qualify the bundled catalog

**Files:**
- Create: `effects-library/src/main/java/io/github/teemuki8/libgdx/agent/effects/library/BuiltInEffectCatalog.java`
- Create: `effects-library/src/main/java/io/github/teemuki8/libgdx/agent/effects/library/BuiltInMaterials.java`
- Create: `effects-library/src/main/java/io/github/teemuki8/libgdx/agent/effects/library/BuiltInGeneralEffects.java`
- Test: `effects-library/src/test/java/io/github/teemuki8/libgdx/agent/effects/library/BuiltInEffectCatalogTest.java`
- Modify: `effects-showcase/build.gradle.kts`
- Modify: `effects-showcase/src/main/java/io/github/teemuki8/libgdx/agent/effects/showcase/GeneralVfxScene.java`
- Modify: `effects-fixtures/build.gradle.kts`
- Create: `effects-fixtures/src/test/java/io/github/teemuki8/libgdx/agent/effects/fixtures/BuiltInEffectCatalogFixtureTest.java`
- Create: `effects-fixtures/src/test/java/io/github/teemuki8/libgdx/agent/effects/fixtures/CatalogFixtureRenderer.java`

**Interfaces:**
- Produces: `BuiltInEffectCatalog.create(CatalogLimits, EffectsLimits)`.
- Produces reusable definitions with IDs `damage-pulse`, `neon-edges`, `ship-trail`,
  `energy-beam`, `arc-lightning`, and `sparks`.

- [ ] **Step 1: Write a failing starter-catalog contract test**

```java
@Test
void bundledCatalogContainsOnlyQualifiedApacheEntriesInStableOrder() {
    EffectCatalog catalog = BuiltInEffectCatalog.create(CATALOG_LIMITS, EFFECTS_LIMITS);
    EffectCatalogSearchResult result = catalog.search(new EffectCatalogQuery(
            desktopGl2(), null, List.of(), 32));
    assertEquals(List.of("arc-lightning", "damage-pulse", "energy-beam",
            "neon-edges", "ship-trail", "sparks"), result.matches().stream()
            .map(match -> match.entry().id()).toList());
    assertTrue(result.matches().stream()
            .allMatch(match -> match.entry().license().equals("Apache-2.0")));
}
```

- [ ] **Step 2: Run the test and verify the built-in provider is missing**

Run:

```bash
./gradlew :effects-library:test --tests '*BuiltInEffectCatalogTest'
```

Expected: FAIL because `BuiltInEffectCatalog` does not exist.

- [ ] **Step 3: Extract six original reusable definitions**

Use `Material2dDefinition` for the two shader materials and the existing definition types for the
four general effects. Reuse the existing showcase/general-fixture GLSL and values; do not create a
second rendering abstraction. Each entry declares this minimum target:

```java
private static final EffectCapabilities DESKTOP_GL2 = new EffectCapabilities(
        2, 0, 2048, false, EffectCapabilities.Profile.DESKTOP_OPENGL);
```

`BuiltInEffectCatalog.create` returns an `InMemoryEffectCatalog` over the six entries. Shader
materials use logical `AssetKey` values such as `source`; the library contains no texture files or
paths. Refactor `GeneralVfxScene` to call `BuiltInGeneralEffects` so the fixture qualifies the same
objects users receive rather than copies.

- [ ] **Step 4: Write the native catalog fixture before completing the refactor**

```java
@Test
void everyVisibleDesktopEntryCompilesAndRenders() throws Exception {
    GdxFixtureHost.run(() -> {
        EffectCatalog catalog = BuiltInEffectCatalog.create(CATALOG_LIMITS, EFFECTS_LIMITS);
        EffectCatalogSearchResult visible = catalog.search(new EffectCatalogQuery(
                actualDesktopCapabilities(), null, List.of(), 32));
        CatalogFixtureRenderer renderer = new CatalogFixtureRenderer(EFFECTS_LIMITS);
        for (EffectCatalogMatch match : visible.matches()) {
            assertTrue(renderer.render(match.variant().definition()).nonBlackPixels() > 0,
                    match.entry().id());
        }
    });
}
```

Keep `CatalogFixtureRenderer` package-private in the fixture test source. Dispatch only across the
six declared families and reuse existing fixture render helpers; it is test infrastructure, not a
new public renderer.

- [ ] **Step 5: Run native and showcase gates**

Run:

```bash
xvfb-run -a ./gradlew :effects-fixtures:test --tests '*BuiltInEffectCatalogFixtureTest' --rerun-tasks --warning-mode=fail
xvfb-run -a ./gradlew :effects-showcase:test --rerun-tasks --warning-mode=fail
```

Expected: PASS; every entry visible to the desktop query produces nonempty pixels.

- [ ] **Step 6: Commit the qualified starter catalog**

```bash
git add effects-library effects-showcase effects-fixtures
git commit -m "Add qualified starter effect catalog"
```

---

### Task 5: Admit successfully qualified imported shaders

**Files:**
- Create: `effects-library/src/main/java/io/github/teemuki8/libgdx/agent/effects/library/QualifiedShaderCatalogEntry.java`
- Test: `effects-library/src/test/java/io/github/teemuki8/libgdx/agent/effects/library/QualifiedShaderCatalogEntryTest.java`

**Interfaces:**
- Consumes: `ShaderImportResult`, `ShaderQualificationResult`, and the actual qualified `EffectCapabilities`.
- Produces: one `EffectCatalogEntry` containing a target-generated `Material2dDefinition`.

- [ ] **Step 1: Write failing admission tests**

```java
@Test
void createsEntryOnlyFromCompiledRenderedMatchingTarget() {
    EffectCatalogEntry entry = QualifiedShaderCatalogEntry.create(
            "water-ripple", "1.0.0", "Water Ripple", "Imported water material",
            List.of("water"), "MIT", "Example author", "",
            importedShader(), successfulQualification(), desktopGl2(),
            CATALOG_LIMITS, EFFECTS_LIMITS);
    assertEquals(EffectFamily.MATERIAL_2D, entry.family());
    assertTrue(entry.variants().get(0).supports(desktopGl2()));
}

@Test
void rejectsUnsupportedUncompiledOrPreviewlessCandidates() {
    assertThrows(IllegalArgumentException.class, () -> create(unsupportedImport(), success()));
    assertThrows(IllegalArgumentException.class, () -> create(importedShader(), failedCompile()));
    assertThrows(IllegalArgumentException.class, () -> create(importedShader(), noPreview()));
}
```

- [ ] **Step 2: Run the test and verify the helper is missing**

Run:

```bash
./gradlew :effects-library:test --tests '*QualifiedShaderCatalogEntryTest'
```

Expected: FAIL because the admission helper does not exist.

- [ ] **Step 3: Implement the single import adapter**

```java
public final class QualifiedShaderCatalogEntry {
    public static EffectCatalogEntry create(String id, String version,
            String displayName, String description, List<String> tags,
            String license, String provenance, String attributionUrl,
            ShaderImportResult imported, ShaderQualificationResult qualification,
            EffectCapabilities capabilities, CatalogLimits catalogLimits,
            EffectsLimits effectsLimits) {
        // Require imported.material, non-UNSUPPORTED fidelity, compiled diagnostic,
        // non-null preview, and a GeneratedShader matching qualification.target.
        // Copy that generated shader into a Material2dDefinition and validate the entry.
    }

    private QualifiedShaderCatalogEntry() {}
}
```

Do not accept source paths, write files, register the result globally, or reinterpret failed
diagnostics. `APPROXIMATED` imports may be admitted when they compiled and rendered; their fidelity
remains visible in provenance/description supplied by the consumer.

- [ ] **Step 4: Run library and core gates**

Run:

```bash
./gradlew :effects-library:check :effects-library:javadoc --warning-mode=fail
.agents/skills/libgdx-agent-effects-dev/scripts/verify.sh core
```

Expected: PASS.

- [ ] **Step 5: Commit qualified import admission**

```bash
git add effects-library
git commit -m "Admit qualified imported shaders to catalogs"
```

---

### Task 6: Expose generic catalog search through protocol and MCP

**Files:**
- Modify: `effects-protocol/src/main/java/io/github/teemuki8/libgdx/agent/effects/protocol/EffectsProtocol.java`
- Modify: `effects-protocol/src/main/java/io/github/teemuki8/libgdx/agent/effects/protocol/EffectsProtocolService.java`
- Modify: `effects-protocol/src/main/java/io/github/teemuki8/libgdx/agent/effects/protocol/Requests.java`
- Modify: `effects-protocol/src/main/java/io/github/teemuki8/libgdx/agent/effects/protocol/Results.java`
- Test: `effects-protocol/src/test/java/io/github/teemuki8/libgdx/agent/effects/protocol/EffectsJsonTest.java`
- Modify: `effects-mcp/src/main/java/io/github/teemuki8/libgdx/agent/effects/mcp/EffectsToolCatalog.java`
- Modify: `effects-mcp/src/main/java/io/github/teemuki8/libgdx/agent/effects/mcp/EffectsToolHandler.java`
- Modify: `effects-mcp/src/test/java/io/github/teemuki8/libgdx/agent/effects/mcp/EffectsToolCatalogTest.java`
- Modify: `effects-mcp/src/test/java/io/github/teemuki8/libgdx/agent/effects/mcp/EffectsToolHandlerTest.java`

**Interfaces:**
- Produces: `EffectsProtocolService.catalog(EffectCatalog)`.
- Produces MCP tools `effect_catalog_search` and `effect_catalog_get`.

- [ ] **Step 1: Write failing closed-JSON tests**

```java
@Test
void catalogRequestsRoundTripAndRejectUnknownFields() throws Exception {
    Requests.CatalogSearchRequest request = new Requests.CatalogSearchRequest(
            desktopGl2(), EffectFamily.TRAIL, List.of("space"), 20);
    String json = mapper.writeValueAsString(request);
    assertEquals(request, mapper.readValue(json, Requests.CatalogSearchRequest.class));
    assertThrows(Exception.class, () -> mapper.readValue(
            json.substring(0, json.length() - 1) + ",\"path\":\"/tmp/x\"}",
            Requests.CatalogSearchRequest.class));
}
```

Add these records:

```java
public record CatalogSearchRequest(EffectCapabilities target,
        EffectFamily family, List<String> tags, int limit) {}

public record CatalogLookupRequest(String id, EffectCapabilities target) {}

public record CatalogSearchResult(List<EffectCatalogMatch> matches, boolean truncated) {}

public record CatalogLookupResult(EffectCatalogMatch match) {}
```

- [ ] **Step 2: Run protocol tests and verify the records are missing**

Run:

```bash
./gradlew :effects-protocol:test --tests '*EffectsJsonTest'
```

Expected: FAIL at compilation.

- [ ] **Step 3: Register an optional generic catalog in the service**

```java
private EffectCatalog catalog;

public synchronized EffectsProtocolService catalog(EffectCatalog value) {
    catalog = Objects.requireNonNull(value, "value");
    return this;
}

public synchronized EffectCatalog catalog() {
    return catalog;
}
```

Requests validate identifier, tag, and limit bounds. Results defensively copy matches. Do not add a
dependency from protocol to `effects-library`; it sees only the core interface.

- [ ] **Step 4: Write failing MCP catalog tests**

```java
@Test
void catalogSearchIsTargetFilteredAndUnavailableWithoutRegistration() {
    McpSchema.CallToolResult unavailable = handler(new EffectsProtocolService())
            .handle(request("effect_catalog_search", desktopSearchArguments())).block();
    assertToolError(unavailable, "NOT_AVAILABLE");

    McpSchema.CallToolResult visible = handler(serviceWithCatalog())
            .handle(request("effect_catalog_search", desktopSearchArguments())).block();
    assertFalse(visible.isError());
    assertEquals(List.of("ship-trail"), matchIds(visible));
}

@Test
void catalogGetDoesNotRevealAnIncompatibleEntry() {
    McpSchema.CallToolResult result = handler(serviceWithCatalog())
            .handle(request("effect_catalog_get", gl2LookupArguments("gpu-sparks"))).block();
    assertToolError(result, "UNKNOWN_EFFECT");
}
```

- [ ] **Step 5: Add two closed MCP tools and handlers**

Use flat bounded capability fields in both schemas: `glMajor`, `glMinor`, `maxTextureSize`,
`floatTextures`, and `profile`. Search additionally accepts optional `family`, optional unique
`tags`, and `limit`; lookup additionally requires `id`. Set `additionalProperties=false`.

The handler constructs the request records, returns `NOT_AVAILABLE` when no catalog is registered,
and delegates both paths to the same core catalog. A missing or incompatible exact lookup returns
`UNKNOWN_EFFECT` without revealing hidden metadata.

- [ ] **Step 6: Run protocol and MCP gates**

Run:

```bash
.agents/skills/libgdx-agent-effects-dev/scripts/verify.sh protocol
.agents/skills/libgdx-agent-effects-dev/scripts/verify.sh mcp
```

Expected: PASS with the exact tool-name set updated to eleven tools.

- [ ] **Step 7: Commit the agent-facing vertical slice**

```bash
git add effects-protocol effects-mcp
git commit -m "Expose target-aware effect catalog tools"
```

---

### Task 7: Document usage and complete publication verification

**Files:**
- Modify: `README.md`
- Create: `docs/guides/effect-library.md`
- Modify: `docs/guides/general-vfx.md`
- Modify: `docs/roadmap.md`
- Modify: `CHANGELOG.md`
- Modify: `docs/guides/releasing.md`
- Modify: `docs/sonatype-central-compliance.md`
- Modify: `.github/workflows/manage-maven-central.yml`

**Interfaces:**
- Documents direct Java use, consumer-owned local catalogs, qualification requirements, and MCP discovery.
- Updates the release contract from six to seven published modules.

- [ ] **Step 1: Write the Java guide with a target-aware example**

```java
EffectCatalog catalog = BuiltInEffectCatalog.create(
        CatalogLimits.developmentDefaults(), EffectsLimits.developmentDefaults());
EffectCapabilities target = new EffectCapabilities(
        3, 2, 8192, true, EffectCapabilities.Profile.DESKTOP_OPENGL);
EffectCatalogSearchResult trails = catalog.search(
        new EffectCatalogQuery(target, EffectFamily.TRAIL, List.of("space"), 20));
EffectDefinition definition = trails.matches().getFirst().variant().definition();
```

Explain that the consumer owns asset resolution and persistence, incompatible entries are absent,
and imported shaders require `ShaderImportQualifier` evidence before
`QualifiedShaderCatalogEntry.create`.

- [ ] **Step 2: Update public module and release documentation**

Add Maven coordinate `io.github.teemuki8:agent-effects-library`. Change all Central expected-module
lists and PURLs to include:

```text
pkg:maven/io.github.teemuki8/agent-effects-library@<version>
```

Add an Unreleased changelog entry describing the optional catalog without claiming mobile/web
qualification or Unity/Unreal import support.

- [ ] **Step 3: Run the focused publication model check**

Run:

```bash
./gradlew -PreleaseVersion=0.3.0 \
  -PreleaseCommit=development-plan-verification \
  generatePomFileForMavenJavaPublication \
  --warning-mode=fail
```

Expected: seven POM-generation tasks. Inspect
`effects-library/build/publications/mavenJava/pom-default.xml` and confirm artifact ID
`agent-effects-library`, version `0.3.0`, and same-version `agent-effects-core` dependency.

- [ ] **Step 4: Run the official full gate**

Run:

```bash
.agents/skills/libgdx-agent-effects-dev/scripts/verify.sh full
```

Expected: PASS under Xvfb with core, library, import, runtime, protocol, MCP, showcase, and native
fixture coverage plus warning-free published-module Javadocs.

- [ ] **Step 5: Review the final diff against the approved design**

Run:

```bash
git diff --check
git status --short
git diff --stat origin/master...HEAD
```

Confirm there is no Gnosis dependency or naming in production code, no remote-pack infrastructure,
and every visible bundled entry is exercised by the native catalog fixture.

- [ ] **Step 6: Commit documentation and release-contract updates**

```bash
git add README.md CHANGELOG.md docs .github/workflows/manage-maven-central.yml
git commit -m "Document the qualified effect library"
```
