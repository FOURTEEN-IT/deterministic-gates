plugins {
    `java-gradle-plugin`
    `java-library`
}

group = "de.fourteen.gates"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
    withSourcesJar()
}

repositories {
    mavenCentral()
}

// No runtime dependencies beyond the Gradle API on purpose: every gate here parses plain text,
// JUnit/JaCoCo XML (JDK's own javax.xml.parsers) or reflects over compiled classes -- none of
// that needs a third-party library, and a project pulling in this plugin shouldn't have to pull
// in anything else with it.
dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

gradlePlugin {
    plugins {
        create("gates") {
            id = "de.fourteen.gates"
            implementationClass = "de.fourteen.gates.GatesPlugin"
            displayName = "Deterministic Gates"
            description = "Judgment-free build gates: a requirements register kept honest, " +
                "an architecture doc that can't silently rot, layer-disjoint test coverage, " +
                "feature docs that follow their template, and a suppression register that " +
                "matches the code."
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.named("check") {
    dependsOn(tasks.test)
}
