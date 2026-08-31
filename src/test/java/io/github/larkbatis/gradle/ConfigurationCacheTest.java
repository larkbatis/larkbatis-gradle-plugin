package io.github.larkbatis.gradle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A real consumer build, run twice with {@code --configuration-cache}. The
 * unit tests configure a project in memory and would pass either way: the
 * cache only fails when Gradle tries to <em>store</em> the task graph, and
 * what it refuses to store is a lambda holding the Project. The mapper
 * directories are resolved late, inside exactly such a lambda, so this is the
 * test that says whether a consumer with the cache on can build at all.
 *
 * <p>The second run is the assertion that matters: "Configuration cache entry
 * reused" is the proof the entry was both stored and loadable.
 */
class ConfigurationCacheTest {

    @TempDir
    Path projectDir;

    @BeforeEach
    void consumerProject() throws IOException {
        write(projectDir.resolve("settings.gradle.kts"), "rootProject.name = \"consumer\"\n");
        write(projectDir.resolve("build.gradle.kts"), """
                plugins {
                    java
                    id("io.github.larkbatis")
                }

                larkbatis {
                    mapperDir = layout.projectDirectory.dir("src/main/mappers")
                    mapperDirs.from("src/main/legacy-mappers")
                    // Resolving it would need a repository; the option and the
                    // input registration are what this test is about.
                    addProcessorDependency = false
                }
                """);
        write(projectDir.resolve("src/main/java/com/example/Nothing.java"),
                "package com.example; public class Nothing {}\n");
        write(projectDir.resolve("src/main/mappers/UserMapper.xml"),
                "<mapper namespace=\"com.example.UserMapper\"/>\n");
        write(projectDir.resolve("src/main/legacy-mappers/OrderMapper.xml"),
                "<mapper namespace=\"com.example.OrderMapper\"/>\n");
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private BuildResult build() {
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments("compileJava", "--configuration-cache", "--stacktrace")
                .build();
    }

    @Test
    void aConsumerBuildStoresAndReusesTheConfigurationCache() {
        build();

        assertTrue(build().getOutput().contains("Configuration cache entry reused"),
                "the second run did not reuse the entry — something in the task graph "
                        + "could not be stored");
    }

    /**
     * Editing mapper XML in the <em>second</em> directory has to make
     * {@code compileJava} run again. Registering those files as inputs is half
     * of what this plugin is for, and a configuration cache hit must not turn a
     * changed input into an up-to-date task — the cache covers the task graph,
     * not the file hashes, and confusing the two is how a stale generated
     * mapper ships.
     */
    @Test
    void anXmlEditInTheSecondDirectoryReRunsTheCompile() throws IOException {
        assertEquals(TaskOutcome.SUCCESS, compileOutcome(build()));
        assertEquals(TaskOutcome.UP_TO_DATE, compileOutcome(build()),
                "nothing changed, so the second run must be up to date");

        Files.writeString(projectDir.resolve("src/main/legacy-mappers/OrderMapper.xml"),
                "<mapper namespace=\"com.example.OrderMapper\"><!-- edited --></mapper>\n");

        assertEquals(TaskOutcome.SUCCESS, compileOutcome(build()),
                "an XML edit in mapperDirs left compileJava up to date");
    }

    private static TaskOutcome compileOutcome(BuildResult result) {
        return result.task(":compileJava").getOutcome();
    }
}
