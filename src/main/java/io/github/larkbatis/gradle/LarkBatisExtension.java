package io.github.larkbatis.gradle;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;

/**
 * {@code larkbatis { ... }} build-script configuration.
 *
 * <pre>{@code
 * larkbatis {
 *     mapperDir = layout.projectDirectory.dir("src/main/mappers") // default: src/main/resources
 *     addProcessorDependency = false                              // default: true
 *     addParametersFlag = false                                   // default: true
 * }
 * }</pre>
 */
public abstract class LarkBatisExtension {

    /**
     * Directory scanned (recursively) for mapper XML. Only files whose root
     * element is {@code <mapper>} are picked up, so pointing this at
     * {@code src/main/resources} — the default — is safe next to other XML.
     */
    public abstract DirectoryProperty getMapperDir();

    /**
     * Whether the plugin adds {@code io.github.larkbatis:larkbatis-processor}
     * to the {@code annotationProcessor} configuration automatically. Switch
     * off to manage the processor version yourself.
     */
    public abstract Property<Boolean> getAddProcessorDependency();

    /**
     * Whether the plugin passes {@code -parameters} to {@code compileJava}.
     * On by default, and switching it off is a decision to understand first.
     *
     * <p>Gradle's incremental annotation processing re-runs an aggregating
     * processor over <em>unchanged</em> mappers from their class files, and a
     * parameter name only survives into a class file when it was compiled with
     * this flag. Without it a full build resolves {@code #{name}} and the next
     * incremental build sees {@code arg0} — the same source, two outcomes,
     * decided by whether the file happened to be touched.
     *
     * <p>The alternative for a build that cannot carry the flag is {@code @Param}
     * on every mapper parameter, which needs no parameter names at all.
     */
    public abstract Property<Boolean> getAddParametersFlag();
}
