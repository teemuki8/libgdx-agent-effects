# Reusable Effect Library Design

**Status:** Approved design

**Date:** 2026-08-14

## Purpose

Add a small, reusable catalog of qualified shaders and effects to `libgdx-agent-effects`.
The catalog is a generic Java/libGDX feature. Gnosis may use it as a dependency, but this
repository has no Gnosis dependency, types, persistence rules, or project knowledge.

Ordinary catalog queries return only entries already normalized to this project's public effect
model and qualified for the consumer's declared target capabilities. An unsupported or failed
import is a candidate with diagnostics, not a library entry, and is never shown by catalog search.

## Goals

- Ship an optional curated catalog of original or permissively redistributable effects.
- Let consumers build application-owned local catalogs from successful import results.
- Filter every query by explicit target capabilities so incompatible entries are not returned.
- Reuse the existing immutable effect definitions, import diagnostics, render-thread adapters, and
  deterministic native qualification.
- Expose the same generic semantics through Java, protocol, and MCP without giving MCP filesystem
  or network access.

## Non-goals

- No Gnosis-specific integration in this repository.
- No remote catalog service, marketplace, dependency resolver, or automatic download system.
- No arbitrary filesystem paths, URLs, includes, scripts, expressions, reflection, or class names.
- No catalog entry for a merely parsed, approximately translated, or unqualified candidate.
- No promise that one variant works on every platform. Compatibility is target-specific.
- No new importers are implied by the catalog. Godot shader and libGDX/Flame particle importers can
  feed it now; later importers use the same admission path.

## Module boundary

Add a published JDK-only `effects-library` module depending only on `effects-core`.

`effects-core` owns the minimal immutable catalog contract because protocol and other consumers
must be able to describe entries without depending on bundled content:

- `EffectCatalogEntry`: stable ID and version, family, tags, license/provenance, variants, and
  required logical assets.
- `EffectCatalogVariant`: one normalized effect definition plus its content digest and qualified
  target capabilities.
- `EffectCatalogQuery`: target capabilities plus optional family and tag filters.
- `EffectCatalog`: bounded deterministic search and exact lookup.

The records reuse existing effect definitions and `EffectCapabilities`; they do not introduce a
second shader, particle, or runtime model. `effects-library` supplies the curated implementation
and definitions. `effects-import` remains independent and produces candidates. `effects-libgdx`
continues to own compilation, rendering, and GL qualification on the application render thread.

Protocol and MCP accept an application-registered catalog backend, as they already do for import
and GL operations. This keeps the bundled catalog optional and preserves module direction:

```text
effects-library -> effects-core <- effects-import
                               <- effects-runtime
                               <- effects-libgdx
effects-mcp -> effects-protocol -> effects-core
```

Fixtures and the unpublished showcase may depend on the full stack.

## Catalog entry contract

An entry contains only information needed to discover and instantiate a compatible effect:

- stable lowercase ID and semantic entry version;
- effect family and a bounded ordered tag set;
- display name and short description;
- SPDX license identifier, provenance text, and optional upstream attribution URL;
- bounded logical `AssetKey` requirements without paths;
- one or more target-specific variants.

Each variant contains an existing immutable effect definition, default parameters, a digest of the
definition and required asset declarations, and qualification evidence for explicit capabilities.
Enhanced and fallback variants may share an entry ID. Selection is deterministic: compatible
variants are ordered by declared preference and then stable variant ID.

The first bundled catalog contains only original project content and assets whose redistribution
terms are clearly compatible with this Apache-2.0 library. Other legally usable imports may remain
in a consumer-owned local catalog without being published here.

## Import and admission flow

1. A consumer passes bounded source content, source format, license/provenance metadata, and
   explicit logical asset mappings to an importer.
2. The importer returns an immutable normalized candidate plus structured fidelity diagnostics.
   It does not register or persist anything.
3. The consumer or repository qualification fixture compiles and renders the candidate for
   explicit target capabilities using deterministic reference scenes.
4. Admission verifies the license fields, logical assets, bounds, definition digest, successful
   compilation, deterministic preview, renderer/backend selection, and qualification evidence.
5. A successful candidate becomes a catalog variant only for the capabilities it passed. A failed
   candidate remains ordinary import/qualification evidence outside catalog search.

The library returns values to the caller and never chooses where local catalogs are stored. Gnosis
or any other application may persist those returned values in its own project.

## Query and use flow

A consumer supplies `EffectCatalogQuery` with its actual `EffectCapabilities`. Search first removes
every entry without a compatible qualified variant, then applies optional family and tag filters,
and finally returns a bounded stable ID order. Exact lookup follows the same compatibility rule;
it does not reveal an incompatible entry through a different API.

A result supplies the selected normalized variant, defaults, required logical assets, license, and
provenance. The application resolves those logical assets and owns effect instances, loop calls,
cameras, batches, textures, GL resources, and disposal exactly as it does today.

Protocol and MCP add generic operations equivalent to:

- catalog capabilities;
- bounded target-aware search;
- compatible exact lookup.

They return typed bounded data and do not write packs or project files.

## Qualification scope

A target profile is advertised only when a real corresponding fixture exercises it. Parsing or
shader generation alone is not qualification. Initially the curated catalog advertises only the
desktop OpenGL capabilities covered by the repository's native LWJGL3/Xvfb gates. Android, iOS,
and web capabilities are added only with real platform-specific verification.

Qualification evidence records the definition digest, target capabilities, selected backend,
structured compilation result, preview comparison, warnings, and enforced limits. If the effect or
required asset declaration changes, its digest changes and the old evidence cannot admit it.

## Validation and failures

Construction rejects duplicate entry or variant IDs, invalid versions, missing license/provenance,
unknown families, oversized strings or collections, unresolved logical assets, duplicate target
declarations, stale digests, and unqualified variants. Public values are immutable and defensively
copy inputs.

Search does not return partial incompatible results. Bounded result truncation is deterministic
and reported. Import and qualification failures retain their existing structured diagnostics; the
catalog adds no parallel error language.

## Verification

Focused JDK tests cover:

- immutable construction and configured bounds;
- duplicate IDs, license requirements, and digest mismatch;
- compatible variant selection and exclusion of unsupported targets;
- stable filtering, ordering, and truncation;
- exact lookup obeying the same compatibility filter;
- failed candidates never becoming searchable entries.

Native Linux/Xvfb fixtures compile and render every bundled variant for every capability it
advertises. Protocol and MCP tests cover closed inputs, unavailable backends, stable target-aware
results, and hard result bounds. Publication verification covers the new module's binary, sources,
Javadocs, license notices, POM, signatures, checksums, and same-version core dependency.

## Simplicity constraints

The first implementation is intentionally narrow:

- one optional module;
- one in-memory immutable catalog implementation;
- one target-aware query path shared by search and lookup;
- a small useful starter set built from existing showcase and general-VFX examples;
- existing importers and qualification fixtures extended rather than replaced.

Remote packs, signing formats for local catalogs, databases, indexing engines, popularity metrics,
dependency solvers, and automatic asset acquisition are deferred until a concrete consumer need
exists.
