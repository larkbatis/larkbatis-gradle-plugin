# Changelog

Notable changes to `larkbatis-gradle-plugin`. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project
follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

The release workflow reads the section for the version being tagged out of this
file and uses it verbatim as the GitHub Release body, so a version with no
section here does not get released.

## [Unreleased]

### Added

- **Mapper XML can live in more than one directory**, through a new
  `larkbatis.mapperDirs` file collection:

  ```kotlin
  larkbatis {
      mapperDirs.from("src/main/mappers", "src/main/legacy-mappers")
  }
  ```

  Every directory is scanned recursively, and all of them reach javac in one
  `-Alarkbatis.mapperDir` option — a repeated `-A` of the same name is the last
  one javac reads, not the union. The XML under each is registered as a
  `compileJava` input, so an edit in any of them regenerates.

  `mapperDir` still takes a single directory and is scanned first. The
  `src/main/resources` default now applies only when the build names neither:
  listing mapper trees no longer quietly adds a resources directory nobody
  mentioned. A directory reached through both properties is scanned once,
  because scanning it twice makes the processor report every namespace in it as
  declared by two files.

- **`-parameters` is passed to `compileJava`**, controlled by
  `larkbatis.addParametersFlag` (default on). This was the one wiring step the
  first real migration still had to do by hand, and it is not a convenience:
  Gradle's incremental annotation processing re-runs an aggregating processor
  over *unchanged* mappers from their class files, where a parameter name only
  survives if that flag was on. Without it a clean build resolves `#{name}` and
  the next incremental build fails on `arg0` — the same source with two
  outcomes, decided by whether the file happened to be touched. A build that
  already passes the flag keeps its own copy rather than getting a second one.

### Fixed

- **`mapperDir` is declared as a task input.** Only the mapper XML *files* were
  tracked, so moving `mapperDir` to a directory holding identical XML changed
  the `-A` option text without changing any file hash, and `compileJava` stayed
  UP-TO-DATE with the old path baked in.

## [0.1.0] - 2026-08-30

First public release. Published to both the Gradle Plugin Portal (plugin id
`io.github.larkbatis`) and Maven Central
(`io.github.larkbatis:larkbatis-gradle-plugin`).

```kotlin
plugins {
    java
    id("io.github.larkbatis") version "0.1.0"
}

dependencies {
    implementation("io.github.larkbatis:larkbatis-annotations:0.1.0")
    implementation("io.github.larkbatis:larkbatis-runtime:0.1.0")
    // larkbatis-processor lands on annotationProcessor automatically
}
```

### Added

- **Mapper XML wiring.** Passes `-Alarkbatis.mapperDir=<dir>` to `compileJava`
  (default `src/main/resources`; only files whose root element is `<mapper>` are
  read, so other XML in the same tree is ignored) and registers those files as
  inputs of `compileJava`, so editing a mapper recompiles the mappers. A build
  plugin exists for exactly this reason: `Filer.getResource` does not guarantee
  access to `src/main/resources`, so something outside javac has to hand the
  processor a real directory path.
- **Processor dependency.** Adds `io.github.larkbatis:larkbatis-processor` to
  `annotationProcessor`. Opt out with `addProcessorDependency = false` and manage
  the version yourself.
- **`larkbatisScan`** — the migration-cost report over this project's mappers,
  run in its own JVM off a detached configuration:

  ```bash
  ./gradlew larkbatisScan
  ./gradlew larkbatisScan --args="--summary"
  ./gradlew larkbatisScan --args="--min=BLOCKER --out=migration.txt src/main"
  ```

  `--fail-on-blocker` makes it a CI gate once a migration is under way.
- **Configuration:**

  ```kotlin
  larkbatis {
      mapperDir = layout.projectDirectory.dir("src/main/mappers")
      addProcessorDependency = false
  }
  ```

- **The injected core version is generated from the build**, not typed into the
  source. `larkbatisCoreVersion` in `gradle.properties` decides which
  `larkbatis-processor` and `larkbatis-scanner` consumers resolve, and the
  release workflow refuses to publish while it still reads `-SNAPSHOT`. A
  released plugin that hands out a snapshot coordinate fails in someone else's
  build, days later; this is what stops that.

All code generation stays inside javac. The plugin generates nothing itself and
adds nothing to the application's runtime classpath.

### Known limitations

- **`compileJava` only** — test-scoped mappers are not wired. Mapper interfaces
  belong in `src/main/java`; test code uses them as ordinary classes.
- A `mapperDir` path containing a comma is not supported: the processor treats
  commas as separators between directories.
- TestKit tests against a real consuming build are still deferred; the unit
  tests pin the task wiring instead.

[Unreleased]: https://github.com/larkbatis/larkbatis-gradle-plugin/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/larkbatis/larkbatis-gradle-plugin/releases/tag/v0.1.0
