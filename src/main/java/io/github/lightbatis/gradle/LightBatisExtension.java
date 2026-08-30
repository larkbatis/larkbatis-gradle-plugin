package io.github.lightbatis.gradle;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;

/**
 * {@code lightbatis { ... }} build-script configuration.
 *
 * <pre>{@code
 * lightbatis {
 *     mapperDir = layout.projectDirectory.dir("src/main/mappers") // default: src/main/resources
 *     addProcessorDependency = false                              // default: true
 * }
 * }</pre>
 */
public abstract class LightBatisExtension {

    /**
     * Directory scanned (recursively) for mapper XML. Only files whose root
     * element is {@code <mapper>} are picked up, so pointing this at
     * {@code src/main/resources} — the default — is safe next to other XML.
     */
    public abstract DirectoryProperty getMapperDir();

    /**
     * Whether the plugin adds {@code io.github.lightbatis:lightbatis-processor}
     * to the {@code annotationProcessor} configuration automatically. Switch
     * off to manage the processor version yourself.
     */
    public abstract Property<Boolean> getAddProcessorDependency();
}
