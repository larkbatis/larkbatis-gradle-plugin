package io.github.larkbatis.gradle;

import java.util.ArrayList;
import java.util.List;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileTree;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.process.CommandLineArgumentProvider;

/**
 * Wires mapper XML into the LarkBatis annotation processor:
 * passes {@code -Alarkbatis.mapperDir} to {@code compileJava} and registers
 * the XML files as compile inputs, so editing a mapper file recompiles the
 * mappers. The plugin exists because the {@code Filer.getResource} spec does
 * not guarantee access to {@code src/main/resources} — a build-tool plugin is
 * the reliable way to hand the processor a real directory path.
 *
 * <p>It also passes {@code -parameters}, which is not a convenience. Gradle's
 * incremental annotation processing re-runs an aggregating processor over
 * unchanged mappers from their <em>class files</em>, where a parameter name
 * survives only if that flag was on; without it the first clean build resolves
 * {@code #{name}} and the next incremental build fails on {@code arg0}. Wiring
 * this by hand is exactly the step a consuming build forgets, which is why the
 * plugin does it.
 *
 * <p>Also registers {@code larkbatisScan}, which reports what migrating a
 * MyBatis codebase to LarkBatis would cost. It runs the scanner in its own
 * JVM off a detached configuration, so neither the scanner nor the processor
 * ever touches the application's classpath.
 *
 * <p>All code generation stays inside javac ({@code larkbatis-processor});
 * this plugin never generates sources itself and adds nothing to the
 * application's runtime classpath.
 */
public final class LarkBatisPlugin implements Plugin<Project> {

    /**
     * The core version this plugin hands to consumers. Generated from the
     * {@code larkbatisCoreVersion} build property rather than typed here, so a
     * released plugin cannot inject a SNAPSHOT coordinate into someone else's
     * build — the failure that a literal invites and that nothing else catches.
     */
    static final String PROCESSOR_COORDINATES =
            "io.github.larkbatis:larkbatis-processor:" + CoreVersion.VALUE;

    static final String SCANNER_COORDINATES =
            "io.github.larkbatis:larkbatis-scanner:" + CoreVersion.VALUE;

    static final String SCAN_TASK_NAME = "larkbatisScan";

    @Override
    public void apply(Project project) {
        LarkBatisExtension extension =
                project.getExtensions().create("larkbatis", LarkBatisExtension.class);
        extension.getMapperDir().convention(project.getLayout()
                .getProjectDirectory().dir("src/main/resources"));
        extension.getAddProcessorDependency().convention(true);
        extension.getAddParametersFlag().convention(true);

        project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {
            project.afterEvaluate(evaluated -> {
                if (extension.getAddProcessorDependency().get()) {
                    evaluated.getDependencies()
                            .add(JavaPlugin.ANNOTATION_PROCESSOR_CONFIGURATION_NAME,
                                    PROCESSOR_COORDINATES);
                }
            });
            project.getTasks().named(JavaPlugin.COMPILE_JAVA_TASK_NAME, JavaCompile.class,
                    compile -> {
                        MapperXmlArguments arguments = new MapperXmlArguments(
                                extension.getMapperDir()
                                        .map(dir -> dir.getAsFile().getAbsolutePath()),
                                project.fileTree(extension.getMapperDir(),
                                        tree -> tree.include("**/*.xml")),
                                extension.getAddParametersFlag(),
                                compile.getOptions().getCompilerArgs());
                        compile.getOptions().getCompilerArgumentProviders().add(arguments);
                    });
            registerScanTask(project);
        });
    }

    /**
     * {@code ./gradlew larkbatisScan} — the migration report, over this
     * project's own directory by default. Pass {@code --args} to narrow it:
     * {@code --args="--summary src/main/resources"}.
     *
     * <p>The scanner is resolved into a detached configuration and run in a
     * separate JVM: it is a build-time tool that reads source files, and it
     * has no business on any configuration a consumer can see.
     */
    private static void registerScanTask(Project project) {
        Configuration scanner = project.getConfigurations().detachedConfiguration(
                project.getDependencies().create(SCANNER_COORDINATES));
        scanner.setDescription("The LarkBatis migration scanner, run out of process");
        String projectDirectory = project.getProjectDir().getAbsolutePath();

        project.getTasks().register(SCAN_TASK_NAME, JavaExec.class, task -> {
            task.setGroup("verification");
            task.setDescription(
                    "Report what migrating this project's MyBatis mappers would cost");
            task.setClasspath(scanner);
            task.getMainClass().set("io.github.larkbatis.scanner.ScannerMain");
            task.setArgs(List.of(projectDirectory));
        });
    }

    /**
     * Everything this plugin puts on the javac command line, plus its
     * up-to-date tracking: the XML files are inputs of {@code compileJava},
     * path-sensitive to their relative location only, and the two values that
     * decide the arguments are inputs in their own right — moving
     * {@code mapperDir} to a directory holding identical XML changes the
     * option text without changing the file hashes.
     */
    static final class MapperXmlArguments implements CommandLineArgumentProvider {

        static final String PARAMETERS_FLAG = "-parameters";

        private final Provider<String> mapperDir;
        private final FileTree mapperXml;
        private final Provider<Boolean> addParametersFlag;
        /** The task's own {@code compilerArgs}, read late so a manual flag wins. */
        private final List<String> compilerArgs;

        MapperXmlArguments(Provider<String> mapperDir, FileTree mapperXml,
                Provider<Boolean> addParametersFlag, List<String> compilerArgs) {
            this.mapperDir = mapperDir;
            this.mapperXml = mapperXml;
            this.addParametersFlag = addParametersFlag;
            this.compilerArgs = compilerArgs;
        }

        @InputFiles
        @PathSensitive(PathSensitivity.RELATIVE)
        public FileTree getMapperXml() {
            return mapperXml;
        }

        @Input
        public Provider<String> getMapperDir() {
            return mapperDir;
        }

        @Input
        public Provider<Boolean> getAddParametersFlag() {
            return addParametersFlag;
        }

        @Override
        public Iterable<String> asArguments() {
            List<String> arguments = new ArrayList<>();
            arguments.add("-Alarkbatis.mapperDir=" + mapperDir.get());
            // javac tolerates a repeated -parameters, but emitting it twice
            // would make the command line read as if the plugin had not
            // noticed the build already asks for it
            if (addParametersFlag.get() && !compilerArgs.contains(PARAMETERS_FLAG)) {
                arguments.add(PARAMETERS_FLAG);
            }
            return arguments;
        }
    }
}
