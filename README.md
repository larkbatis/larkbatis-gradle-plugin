# lightbatis-gradle-plugin

Gradle plugin for LightBatis: wires mapper XML into the generator core
(`lightbatis-processor`) at build time. A build-tool plugin (not just APT)
is required because the `Filer.getResource` spec does not guarantee access to
files under `src/main/resources` (design §03).

## Usage

```kotlin
plugins {
    java
    id("io.github.lightbatis") version "0.1.0-SNAPSHOT"
}

dependencies {
    implementation("io.github.lightbatis:lightbatis-runtime:0.1.0-SNAPSHOT")
    implementation("io.github.lightbatis:lightbatis-annotations:0.1.0-SNAPSHOT")
    // lightbatis-processor lands on annotationProcessor automatically
}
```

What the plugin does — and all it does:

- passes `-Alightbatis.mapperDir=<dir>` to `compileJava` (default:
  `src/main/resources`; only files with a `<mapper>` root element are read,
  so other XML in the same tree is ignored),
- registers the mapper XML files as inputs of `compileJava`, so editing a
  mapper recompiles the mappers,
- adds `io.github.lightbatis:lightbatis-processor` to `annotationProcessor`.

Configuration:

```kotlin
lightbatis {
    mapperDir = layout.projectDirectory.dir("src/main/mappers")
    addProcessorDependency = false // manage the processor version yourself
}
```

All code generation stays inside javac; the plugin generates nothing itself
and adds nothing to the application's runtime classpath.

Build: `./gradlew build validatePlugins` (JDK 17 via toolchain).
Local development: `settings.gradle.kts` already has `includeBuild("../lightbatis")`.
