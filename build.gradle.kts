plugins {
    `java-gradle-plugin`
}

group = "io.github.lightbatis"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

gradlePlugin {
    plugins {
        create("lightbatis") {
            id = "io.github.lightbatis"
            implementationClass = "io.github.lightbatis.gradle.LightBatisPlugin"
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    // No dependency on the processor: the plugin only passes its Maven
    // coordinates (annotationProcessor) and a directory path (-A option).
    // Generation happens inside javac; nothing leaks anywhere (§03).

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(gradleApi())
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
