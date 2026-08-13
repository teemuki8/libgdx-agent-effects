# Releasing

Release preparation, staging, and Maven Central publication are separate authorities. Publishing
is irreversible and requires explicit authorization.

## Prepare

1. Confirm the worktree is clean and `HEAD` equals `origin/main`.
2. Confirm `CHANGELOG.md` contains the exact release version.
3. Run the full Linux gate:

   ```bash
   .agents/skills/libgdx-agent-effects-dev/scripts/verify.sh full
   ```

4. Generate and inspect the release POMs without publishing:

   ```bash
   ./gradlew -PreleaseVersion=0.1.0 \
     generatePomFileForMavenJavaPublication \
     --warning-mode=fail
   ```

5. Confirm the `maven-central` GitHub environment contains the namespace variable, Central user
   token, armored signing key, and signing-key passphrase.

## Stage

With explicit release authorization, create the exact semantic tag and GitHub release. Publishing
the GitHub release starts `Stage Maven Central`, which checks out that tag, runs the clean Xvfb
gate, signs and uploads the four published modules, and transfers them to Central with
user-managed publication.

The staging workflow must reach success before continuing. Its success means the deployment was
uploaded for Central validation; it does not mean the artifacts are public.

## Inspect and publish

Run `Manage Maven Central` against the exact version tag with `operation=inspect`. Confirm that one
deployment is `VALIDATED`, its corrected metadata check passes, and its PURLs are exactly:

- `pkg:maven/io.github.teemuki8/agent-effects-core@<version>`
- `pkg:maven/io.github.teemuki8/agent-effects-libgdx@<version>`
- `pkg:maven/io.github.teemuki8/agent-effects-protocol@<version>`
- `pkg:maven/io.github.teemuki8/agent-effects-mcp@<version>`

Only then run the workflow against the same tag with `operation=publish` and the validated
deployment ID. Do not create a second deployment while Central reports `PUBLISHING`.

Publication is complete only after Central reports `PUBLISHED` and every POM, binary, sources,
Javadocs, signature, and checksum is publicly downloadable from Maven Central.
