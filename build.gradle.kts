plugins {
    `java-gradle-plugin`
    `maven-publish`
    signing
    // Publishes to the Gradle Plugin Portal, and configures the sources and
    // javadoc jars that Maven Central also requires.
    id("com.gradle.plugin-publish") version "1.3.1"
}

group = "io.github.larkbatis"
version = providers.gradleProperty("version").get()
description = "Gradle plugin for LarkBatis: wires mapper XML into the annotation processor"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

// --- the core version this plugin injects ---------------------------------
//
// It used to be a literal in LarkBatisPlugin.java with a comment promising the
// release process kept it in step. Generating it is what makes that promise
// true: a released plugin that injects `larkbatis-processor:0.1.0-SNAPSHOT`
// fails in the *consumer's* build, long after ours went green.

val coreVersion = providers.gradleProperty("larkbatisCoreVersion").get()

val generateCoreVersion = tasks.register("generateCoreVersion") {
    description = "Write the larkbatis core version this plugin injects into consumer builds"
    val outputDir = layout.buildDirectory.dir("generated/sources/coreversion/java/main")
    outputs.dir(outputDir)
    // Captured as plain data so the task action holds no project reference
    // (Gradle configuration cache).
    val version = coreVersion
    doLast {
        val dir = outputDir.get().asFile.resolve("io/github/larkbatis/gradle")
        dir.mkdirs()
        dir.resolve("CoreVersion.java").writeText(
            """
            package io.github.larkbatis.gradle;

            /** Generated from the larkbatisCoreVersion build property — do not edit. */
            final class CoreVersion {

                static final String VALUE = "$version";

                private CoreVersion() {
                }
            }
            """.trimIndent() + "\n"
        )
    }
}

sourceSets["main"].java.srcDir(generateCoreVersion)

gradlePlugin {
    website = "https://github.com/larkbatis/larkbatis"
    vcsUrl = "https://github.com/larkbatis/larkbatis-gradle-plugin.git"
    plugins {
        create("larkbatis") {
            id = "io.github.larkbatis"
            implementationClass = "io.github.larkbatis.gradle.LarkBatisPlugin"
            displayName = "LarkBatis"
            description = "Compiles MyBatis-style mappers and SQL into plain Java at build time: " +
                "passes the mapper XML directory to larkbatis-processor, registers those files as " +
                "compileJava inputs, and adds the larkbatisScan migration report."
            tags = listOf("mybatis", "sql", "jdbc", "orm", "code-generation", "annotation-processor", "native-image")
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    // No dependency on the processor: the plugin only passes its Maven
    // coordinates (annotationProcessor) and a directory path (-A option).
    // Generation happens inside javac; nothing leaks anywhere.

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(gradleApi())
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// --- publishing ------------------------------------------------------------
//
// Two destinations, and the plugin needs both. The Plugin Portal is what makes
// `plugins { id("io.github.larkbatis") version "..." }` resolve out of the box;
// Maven Central carries the same artifacts for builds that pin their plugin
// repositories. `java-gradle-plugin` contributes the marker publication that
// the `plugins {}` block resolves through, so the POM and the signature have to
// reach every publication, not just the main one.

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name = artifactId
            description = provider { project.description }
            url = "https://github.com/larkbatis/larkbatis-gradle-plugin"
            inceptionYear = "2026"
            licenses {
                license {
                    name = "The Apache License, Version 2.0"
                    url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                    distribution = "repo"
                }
            }
            developers {
                developer {
                    id = "larkbatis"
                    name = "LarkBatis contributors"
                    url = "https://github.com/larkbatis"
                }
            }
            scm {
                connection = "scm:git:https://github.com/larkbatis/larkbatis-gradle-plugin.git"
                developerConnection = "scm:git:ssh://git@github.com/larkbatis/larkbatis-gradle-plugin.git"
                url = "https://github.com/larkbatis/larkbatis-gradle-plugin"
            }
            issueManagement {
                system = "GitHub Issues"
                url = "https://github.com/larkbatis/larkbatis-gradle-plugin/issues"
            }
        }
    }

    repositories {
        // The Central Portal takes a zipped bundle, not a deploy over the wire:
        // publish into a local Maven layout that
        // .github/scripts/publish-to-central.sh zips and uploads.
        maven {
            name = "centralBundle"
            url = uri(layout.buildDirectory.dir("central-bundle"))
        }
        maven {
            name = "centralSnapshots"
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
            credentials {
                username = providers.environmentVariable("CENTRAL_USERNAME").orNull
                password = providers.environmentVariable("CENTRAL_PASSWORD").orNull
            }
        }
    }
}

signing {
    val signingKey = providers.environmentVariable("SIGNING_KEY").orNull
    val signingPassword = providers.environmentVariable("SIGNING_PASSWORD").orNull
    isRequired = signingKey != null
    if (signingKey != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        // The collection overload, not a named publication: the marker
        // publications are added after this block runs.
        sign(publishing.publications)
    }
}
