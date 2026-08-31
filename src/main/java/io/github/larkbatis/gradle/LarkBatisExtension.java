package io.github.larkbatis.gradle;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;

/**
 * {@code larkbatis { ... }} build-script configuration.
 *
 * <pre>{@code
 * larkbatis {
 *     mapperDir = layout.projectDirectory.dir("src/main/mappers") // default: src/main/resources
 *     mapperDirs.from("src/main/legacy-mappers")                  // default: empty
 *     addProcessorDependency = false                              // default: true
 *     addParametersFlag = false                                   // default: true
 *     mapUnderscoreToCamelCase = false                            // default: processor default (true)
 *     typeHandlers = "com.example.Money:com.example.MoneyHandler" // default: none
 *     registryPackage = "com.example.app"                         // default: processor default
 *     springConfig = false                                        // default: processor default
 *     springConfigPackage = "com.example.config"                  // default: processor default
 * }
 * }</pre>
 */
public abstract class LarkBatisExtension {

    /**
     * Directory scanned (recursively) for mapper XML. Only files whose root
     * element is {@code <mapper>} are picked up, so pointing this at
     * {@code src/main/resources} — the default — is safe next to other XML.
     *
     * <p>Unset by default rather than conventioned to the resources directory,
     * because the default has to disappear when {@link #getMapperDirs()} names
     * directories of its own: a build that lists two mapper trees should scan
     * those two, not those two plus a resources directory it never mentioned.
     */
    public abstract DirectoryProperty getMapperDir();

    /**
     * Further directories scanned the same way, for a module whose mapper XML
     * does not live under one root — rewritten mappers kept beside the legacy
     * ones, or generated mapper XML under the build directory.
     *
     * <p>Every namespace found still has to name a mapper interface compiled
     * in this module; a directory outside it is only useful when the
     * interfaces are here too, and any file that matches nothing is reported
     * and ignored.
     *
     * <p>The scanned set is {@link #getMapperDir()} followed by this
     * collection, duplicates removed; when neither is set it is
     * {@code src/main/resources}. Two directories declaring the same mapper
     * namespace is a compile error, not a last-one-wins merge — nothing here
     * overrides anything, so there is no order to learn.
     *
     * <pre>{@code
     * larkbatis {
     *     mapperDirs.from("src/main/mappers", "src/main/legacy-mappers")
     * }
     * }</pre>
     */
    public abstract ConfigurableFileCollection getMapperDirs();

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

    // ── Processor options ────────────────────────────────────────────
    //
    // Each property below maps to a -Alarkbatis.xxx option passed to javac.
    // None have a convention: when unset, the plugin does not emit the
    // option and the processor applies its own default.

    /**
     * {@code false} makes underscores significant when a ResultSet label is
     * matched to a property — the behaviour MyBatis has with its setting of
     * the same name off. The processor defaults to {@code true}.
     *
     * <p>Passed as {@code -Alarkbatis.mapUnderscoreToCamelCase}.
     */
    public abstract Property<Boolean> getMapUnderscoreToCamelCase();

    /**
     * A default handler per Java type: {@code javaType:handlerClass} pairs
     * separated by commas — the build-time answer to a
     * {@code mybatis-config.xml} {@code <typeHandlers>} block.
     *
     * <p>Passed as {@code -Alarkbatis.typeHandlers}.
     */
    public abstract Property<String> getTypeHandlers();

    /**
     * Package for the generated {@code LarkBatisMappers} registry class.
     * When unset, the processor picks the common package prefix of all
     * mappers.
     *
     * <p>Passed as {@code -Alarkbatis.registryPackage}.
     */
    public abstract Property<String> getRegistryPackage();

    /**
     * {@code false} suppresses the generated Spring {@code @Configuration}.
     * When unset, the processor emits it when spring-context is on the
     * build classpath.
     *
     * <p>Passed as {@code -Alarkbatis.springConfig}.
     */
    public abstract Property<Boolean> getSpringConfig();

    /**
     * Package for the generated Spring {@code @Configuration} class.
     * When unset, it lands in the same package as the registry.
     *
     * <p>Passed as {@code -Alarkbatis.springConfigPackage}.
     */
    public abstract Property<String> getSpringConfigPackage();
}
