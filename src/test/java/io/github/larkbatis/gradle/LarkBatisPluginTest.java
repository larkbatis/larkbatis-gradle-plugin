package io.github.larkbatis.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LarkBatisPluginTest {

    private static Project apply() {
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply("java");
        project.getPlugins().apply("io.github.larkbatis");
        return project;
    }

    private static void write(File file) throws IOException {
        Files.createDirectories(file.getParentFile().toPath());
        Files.writeString(file.toPath(), "<mapper namespace=\"com.example.Any\"/>");
    }

    /** The XML files the plugin registered as inputs of {@code compileJava}. */
    private static org.gradle.api.file.FileTree mapperXmlOf(Project project) {
        JavaCompile compile = (JavaCompile) project.getTasks().getByName("compileJava");
        for (CommandLineArgumentProvider provider
                : compile.getOptions().getCompilerArgumentProviders()) {
            if (provider instanceof LarkBatisPlugin.MapperXmlArguments arguments) {
                return arguments.getMapperXml();
            }
        }
        throw new AssertionError("the plugin registered no argument provider");
    }

    /** The directories inside the single {@code -Alarkbatis.mapperDir} option. */
    private static List<String> mapperDirsOf(Project project) {
        String option = compilerArgsOf(project).get(0);
        assertTrue(option.startsWith("-Alarkbatis.mapperDir="), option);
        return List.of(option.substring("-Alarkbatis.mapperDir=".length())
                .split(java.util.regex.Pattern.quote(File.pathSeparator)));
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
        String expected = "-Alarkbatis.mapperDir=" + project.getProjectDir()
                + File.separator + "src" + File.separator + "main" + File.separator + "resources";
        assertEquals(expected, args.get(0));
    }

    @Test
    void mapperDirIsConfigurable() {
        Project project = apply();
        LarkBatisExtension extension =
                project.getExtensions().getByType(LarkBatisExtension.class);
        extension.getMapperDir().set(project.getLayout()
                .getProjectDirectory().dir("src/main/mappers"));
        assertTrue(compilerArgsOf(project).get(0).endsWith("mappers"));
    }

    @Test
    void mapperDirsAddsFurtherDirectories() {
        Project project = apply();
        LarkBatisExtension extension =
                project.getExtensions().getByType(LarkBatisExtension.class);
        extension.getMapperDir().set(project.getLayout()
                .getProjectDirectory().dir("src/main/mappers"));
        extension.getMapperDirs().from("src/main/legacy-mappers");

        assertEquals(List.of(
                        new File(project.getProjectDir(), "src/main/mappers").getAbsolutePath(),
                        new File(project.getProjectDir(), "src/main/legacy-mappers")
                                .getAbsolutePath()),
                mapperDirsOf(project));
    }

    /**
     * The resources default is a fallback, not a floor. A build that lists its
     * mapper trees gets those trees — silently walking a directory it never
     * named would make an unrelated {@code <mapper>} file elsewhere in
     * resources part of the compilation.
     */
    @Test
    void mapperDirsAloneReplacesTheResourcesDefault() {
        Project project = apply();
        project.getExtensions().getByType(LarkBatisExtension.class)
                .getMapperDirs().from("src/main/mappers", "shared/mappers");

        assertEquals(List.of(
                        new File(project.getProjectDir(), "src/main/mappers").getAbsolutePath(),
                        new File(project.getProjectDir(), "shared/mappers").getAbsolutePath()),
                mapperDirsOf(project));
    }

    /**
     * Half of what the plugin is for: the XML has to be an input of
     * {@code compileJava}, or an edit to a mapper file in a second directory
     * leaves the build up to date and the generated code stale.
     */
    @Test
    void registersMapperXmlFromEveryDirectoryAsACompileInput() throws IOException {
        Project project = apply();
        File mappers = new File(project.getProjectDir(), "src/main/mappers");
        File shared = new File(project.getProjectDir(), "shared/mappers");
        write(new File(mappers, "UserMapper.xml"));
        write(new File(shared, "nested/OrderMapper.xml"));
        write(new File(shared, "notes.txt"));

        project.getExtensions().getByType(LarkBatisExtension.class)
                .getMapperDirs().from(mappers, shared);

        assertEquals(Set.of("UserMapper.xml", "OrderMapper.xml"),
                mapperXmlOf(project).getFiles().stream()
                        .map(File::getName)
                        .collect(Collectors.toSet()));
    }

    /**
     * A directory reached through both properties must be walked once: the
     * second walk would report every namespace in it as declared twice, which
     * is a compile error rather than a warning.
     */
    @Test
    void deduplicatesADirectoryNamedTwice() {
        Project project = apply();
        LarkBatisExtension extension =
                project.getExtensions().getByType(LarkBatisExtension.class);
        extension.getMapperDir().set(project.getLayout()
                .getProjectDirectory().dir("src/main/mappers"));
        extension.getMapperDirs().from("src/main/mappers");

        assertEquals(1, mapperDirsOf(project).size());
    }

    /**
     * Not a convenience: without it, an incremental build re-runs the processor
     * over unchanged mappers from their class files and every {@code #{name}}
     * resolves to {@code arg0}.
     */
    @Test
    void passesTheParametersFlagByDefault() {
        assertTrue(compilerArgsOf(apply()).contains("-parameters"));
    }

    @Test
    void parametersFlagCanBeSwitchedOff() {
        Project project = apply();
        project.getExtensions().getByType(LarkBatisExtension.class)
                .getAddParametersFlag().set(false);
        List<String> args = compilerArgsOf(project);
        assertEquals(false, args.contains("-parameters"));
        assertEquals(1, args.size(), "the mapperDir option must still be passed");
    }

    /** A build that already asks for it must not get it twice. */
    @Test
    void doesNotRepeatAParametersFlagTheBuildAlreadySets() {
        Project project = apply();
        JavaCompile compile = (JavaCompile) project.getTasks().getByName("compileJava");
        compile.getOptions().getCompilerArgs().add("-parameters");

        List<String> everythingJavacSees = new ArrayList<>(compile.getOptions().getCompilerArgs());
        everythingJavacSees.addAll(compilerArgsOf(project));
        assertEquals(1, everythingJavacSees.stream().filter("-parameters"::equals).count());
    }

    @Test
    void addsTheProcessorToAnnotationProcessorByDefault() {
        Project project = apply();
        ((ProjectInternal) project).evaluate();
        boolean present = project.getConfigurations()
                .getByName(JavaPlugin.ANNOTATION_PROCESSOR_CONFIGURATION_NAME)
                .getDependencies().stream()
                .map(Dependency::getName)
                .anyMatch("larkbatis-processor"::equals);
        assertTrue(present, "expected larkbatis-processor on annotationProcessor");
    }

    @Test
    void processorDependencyCanBeSwitchedOff() {
        Project project = apply();
        project.getExtensions().getByType(LarkBatisExtension.class)
                .getAddProcessorDependency().set(false);
        ((ProjectInternal) project).evaluate();
        boolean present = project.getConfigurations()
                .getByName(JavaPlugin.ANNOTATION_PROCESSOR_CONFIGURATION_NAME)
                .getDependencies().stream()
                .map(Dependency::getName)
                .anyMatch("larkbatis-processor"::equals);
        assertEquals(false, present);
    }

    @Test
    void registersTheMigrationScanOverTheProjectDirectory() {
        Project project = apply();
        JavaExec scan = (JavaExec) project.getTasks()
                .getByName(LarkBatisPlugin.SCAN_TASK_NAME);
        assertEquals("io.github.larkbatis.scanner.ScannerMain", scan.getMainClass().get());
        assertEquals(List.of(project.getProjectDir().getAbsolutePath()), scan.getArgs());
    }

    @Test
    void keepsTheScannerOffEveryConfigurationAConsumerCanSee() {
        Project project = apply();
        ((ProjectInternal) project).evaluate();
        for (Configuration configuration : project.getConfigurations()) {
            for (Dependency dependency : configuration.getDependencies()) {
                assertTrue(!"larkbatis-scanner".equals(dependency.getName()),
                        "the scanner leaked onto " + configuration.getName());
            }
        }
    }
}
