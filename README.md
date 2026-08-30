# larkbatis-gradle-plugin

Gradle plugin for LarkBatis: wires mapper XML into the generator core
(`larkbatis-processor`) at build time. A build-tool plugin (not just APT)
is required because the `Filer.getResource` spec does not guarantee access to
files under `src/main/resources`.

## Usage

```kotlin
plugins {
    java
    id("io.github.larkbatis") version "0.1.0-SNAPSHOT"
}

dependencies {
    implementation("io.github.larkbatis:larkbatis-runtime:0.1.0-SNAPSHOT")
    implementation("io.github.larkbatis:larkbatis-annotations:0.1.0-SNAPSHOT")
    // larkbatis-processor lands on annotationProcessor automatically
}
```

What the plugin does — and all it does:

- passes `-Alarkbatis.mapperDir=<dir>` to `compileJava` (default:
  `src/main/resources`; only files with a `<mapper>` root element are read,
  so other XML in the same tree is ignored),
- registers the mapper XML files as inputs of `compileJava`, so editing a
  mapper recompiles the mappers,
- passes `-parameters` to `compileJava` (see below),
- adds `io.github.larkbatis:larkbatis-processor` to `annotationProcessor`,
- registers the `larkbatisScan` task (below).

Configuration:

```kotlin
larkbatis {
    mapperDir = layout.projectDirectory.dir("src/main/mappers")
    addProcessorDependency = false // manage the processor version yourself
    addParametersFlag = false      // you already pass -parameters, or use @Param everywhere
}
```

### Why `-parameters`

It is not a convenience flag. Gradle's incremental annotation processing
re-runs an aggregating processor over *unchanged* mappers from their **class
files**, and a parameter name only survives into a class file when it was
compiled with `-parameters`. Without it a clean build resolves `#{name}`
happily and the next incremental build sees `arg0` — same source, two
outcomes, decided by whether the file happened to be touched.

A build that already passes it keeps its own copy; the plugin does not repeat
it. A build that cannot carry the flag at all switches `addParametersFlag`
off and puts `@Param` on every mapper parameter, which needs no parameter
names.

All code generation stays inside javac; the plugin generates nothing itself
and adds nothing to the application's runtime classpath.

## `larkbatisScan` — before you migrate

```bash
./gradlew larkbatisScan
./gradlew larkbatisScan --args="--summary"
./gradlew larkbatisScan --args="--min=BLOCKER --out=migration.txt src/main"
```

Reads the project's mapper XML, `mybatis-config.xml` and MyBatis-annotated
Java, and prints what migrating to LarkBatis would cost — statement by
statement, with line numbers, ranked by how much thought each finding needs.
It compiles nothing, so it works on a project that has never been built and on
one that still depends entirely on MyBatis.

Every finding names a heading in the migration guide (`MIGRATION.md` in the
`larkbatis` repository). `--fail-on-blocker` makes it usable as a CI gate
once a migration is under way.

The scanner runs in its own JVM off a detached configuration: like the
processor, it is a build-time tool and never appears on a configuration a
consumer can see.

Build: `./gradlew build validatePlugins` (JDK 17 via toolchain).
Local development: `settings.gradle.kts` already has `includeBuild("../larkbatis")`.
