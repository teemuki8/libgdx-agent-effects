# Sonatype Central compliance

The published group is `io.github.teemuki8`. The published artifacts are `agent-effects-core`,
`agent-effects-import`, `agent-effects-runtime`, `agent-effects-libgdx`,
`agent-effects-protocol`, and `agent-effects-mcp`. `effects-fixtures` and `effects-showcase` are
never published.

## Artifact contract

Each published module provides:

- a binary JAR, sources JAR, Javadoc JAR, and POM;
- an ASCII-armored detached signature for every primary file;
- Maven-generated checksums;
- `META-INF/LICENSE` and `META-INF/NOTICE` in every JAR;
- reproducible archive ordering and timestamps.

Each POM declares the exact GAV coordinates, project name and description, project URL,
Apache-2.0 license, developer identity, and SCM connection. Published dependencies use fixed
versions; project dependencies resolve to the same release version.

## Trust boundary

The GitHub `maven-central` environment holds the Central Portal user token and in-memory OpenPGP
signing material. Release workflows receive only those named values. They do not publish the
fixture, accept caller-selected coordinates, or publish automatically after validation.

The staging workflow creates a user-managed Central deployment. The management workflow permits
publication only when Central reports `VALIDATED`, the PURLs exactly match all six intended
coordinates, and the downloaded core archive/POM pass the corrected-metadata check.

## Evidence required for completion

A release report must identify the source commit, version tag, GitHub release, staging run,
Central deployment ID, validation result, publication run, and final Central state. Public
verification must cover all six modules and their POM, binary, sources, Javadocs, detached
signatures, and checksums. A successful upload or a `PUBLISHING` state is not completion.
