package io.github.lightbatis.gradle;

import java.util.List;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.FileTree;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.process.CommandLineArgumentProvider;

/**
 * Wires mapper XML into the LightBatis annotation processor (design §03):
 * passes {@code -Alightbatis.mapperDir} to {@code compileJava} and registers
 * the XML files as compile inputs, so editing a mapper file recompiles the
 * mappers. The plugin exists because the {@code Filer.getResource} spec does
 * not guarantee access to {@code src/main/resources} — a build-tool plugin is
 * the reliable way to hand the processor a real directory path.
 *
 * <p>All code generation stays inside javac ({@code lightbatis-processor});
 * this plugin never generates sources itself and adds nothing to the
 * application's runtime classpath.
 */
public final class LightBatisPlugin implements Plugin<Project> {

    /** Kept in lockstep with the plugin's own version by the release process. */
    static final String PROCESSOR_COORDINATES =
            "io.github.lightbatis:lightbatis-processor:0.1.0-SNAPSHOT";

    @Override
    public void apply(Project project) {
        LightBatisExtension extension =
                project.getExtensions().create("lightbatis", LightBatisExtension.class);
        extension.getMapperDir().convention(project.getLayout()
                .getProjectDirectory().dir("src/main/resources"));
        extension.getAddProcessorDependency().convention(true);

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
                                        tree -> tree.include("**/*.xml")));
                        compile.getOptions().getCompilerArgumentProviders().add(arguments);
                    });
        });
    }

    /**
     * The {@code -A} option plus its up-to-date tracking: the XML files are
     * inputs of {@code compileJava}, path-sensitive to their relative
     * location only.
     */
    static final class MapperXmlArguments implements CommandLineArgumentProvider {

        private final Provider<String> mapperDir;
        private final FileTree mapperXml;

        MapperXmlArguments(Provider<String> mapperDir, FileTree mapperXml) {
            this.mapperDir = mapperDir;
            this.mapperXml = mapperXml;
        }

        @InputFiles
        @PathSensitive(PathSensitivity.RELATIVE)
        public FileTree getMapperXml() {
            return mapperXml;
        }

        @Override
        public Iterable<String> asArguments() {
            return List.of("-Alightbatis.mapperDir=" + mapperDir.get());
        }
    }
}
