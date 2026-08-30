# Releasing

The full runbook — secrets, signing key, the Central Portal flow, the order the
four repositories release in — lives in **`larkbatis/RELEASING.md`**. This file
covers only what is specific to this repository.

## Two destinations

A release publishes to both, and they are independent:

- **Maven Central** — `io.github.larkbatis:larkbatis-gradle-plugin`, plus the
  `io.github.larkbatis.gradle.plugin` marker artifact that a `plugins {}` block
  resolves through.
- **The Gradle Plugin Portal** — plugin id `io.github.larkbatis`, which is what
  makes `plugins { id("io.github.larkbatis") version "0.1.0" }` work with no
  `pluginManagement` block in the consumer's `settings.gradle.kts`.

The Plugin Portal step needs `GRADLE_PUBLISH_KEY` and `GRADLE_PUBLISH_SECRET`
(from <https://plugins.gradle.org/user/profile>) as repository secrets. Without
them it logs a notice and skips, and the Central publish still happens.

Unlike Central, a Plugin Portal publish is immediate — there is no validate-then-
confirm step to hold it back. It runs after the Central upload for that reason:
if the bundle is going to be rejected, it is rejected before anything is
irreversible.

## `larkbatisCoreVersion`

`gradle.properties` carries the version of `larkbatis-processor` and
`larkbatis-scanner` that this plugin injects into **consumer** builds. It is
generated into `CoreVersion.java` at build time rather than typed into the
source, and the release workflow refuses to run while it still reads
`-SNAPSHOT`.

Release `larkbatis` first, then set:

```properties
version=0.1.0
larkbatisCoreVersion=0.1.0
```

The two are deliberately separate knobs: a plugin-only fix must not bump the
coordinate consumers resolve, and adopting a new core release should not require
a plugin release.

## Rehearse first

```bash
gh workflow run release.yml -f version=0.1.0 -f dry-run=true
```

Builds, tests, runs `validatePlugins`, signs and assembles the bundle — and
uploads nothing, to neither destination.
