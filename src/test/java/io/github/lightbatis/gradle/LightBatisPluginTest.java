package io.github.lightbatis.gradle;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightBatisPluginTest {

    private static Project apply() {
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply("java");
        project.getPlugins().apply("io.github.lightbatis");
        return project;
    }

    private static List<String> compilerArgsOf(Project project) {
        JavaCompile compile = (JavaCompile) project.getTasks().getByName("compileJava");
        List<String> args = new ArrayList<>();
        for (CommandLineArgumentProvider provider
                : compile.getOptions().getCompilerArgumentProviders()) {
            provider.asArguments().forEach(args::add);
        }
        return args;
    }

    @Test
    void passesTheMapperDirOptionWithTheResourcesDefault() {
        Project project = apply();
        List<String> args = compilerArgsOf(project);
        assertEquals(1, args.size());
        String expected = "-Alightbatis.mapperDir=" + project.getProjectDir()
                + File.separator + "src" + File.separator + "main" + File.separator + "resources";
        assertEquals(expected, args.get(0));
    }

    @Test
    void mapperDirIsConfigurable() {
        Project project = apply();
        LightBatisExtension extension =
                project.getExtensions().getByType(LightBatisExtension.class);
        extension.getMapperDir().set(project.getLayout()
                .getProjectDirectory().dir("src/main/mappers"));
        assertTrue(compilerArgsOf(project).get(0).endsWith("mappers"));
    }

    @Test
    void addsTheProcessorToAnnotationProcessorByDefault() {
        Project project = apply();
        ((ProjectInternal) project).evaluate();
        boolean present = project.getConfigurations()
                .getByName(JavaPlugin.ANNOTATION_PROCESSOR_CONFIGURATION_NAME)
                .getDependencies().stream()
                .map(Dependency::getName)
                .anyMatch("lightbatis-processor"::equals);
        assertTrue(present, "expected lightbatis-processor on annotationProcessor");
    }

    @Test
    void processorDependencyCanBeSwitchedOff() {
        Project project = apply();
        project.getExtensions().getByType(LightBatisExtension.class)
                .getAddProcessorDependency().set(false);
        ((ProjectInternal) project).evaluate();
        boolean present = project.getConfigurations()
                .getByName(JavaPlugin.ANNOTATION_PROCESSOR_CONFIGURATION_NAME)
                .getDependencies().stream()
                .map(Dependency::getName)
                .anyMatch("lightbatis-processor"::equals);
        assertEquals(false, present);
    }
}
