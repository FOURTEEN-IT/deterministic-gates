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

// Five plugins, not one: structureDoc, layerDisjointness and suppressionRegister check three
// unrelated things and can be adopted independently; requirementsCoverage,
// taggedRequirementsCoverage and featureDocs stay together in "requirements" because all three
// read the same requirements register through the same parser (see
// requirements/RequirementsExtension.java) -- splitting those three further would mean
// duplicating that parser instead of sharing it.
gradlePlugin {
    plugins {
        create("structureDoc") {
            id = "de.fourteen.gates.structuredoc"
            implementationClass = "de.fourteen.gates.structuredoc.StructureDocPlugin"
            displayName = "Deterministic Gates: structureDoc"
            description = "Checks that an architecture doc names no file that doesn't exist " +
                "and mentions every domain type."
        }
        create("requirements") {
            id = "de.fourteen.gates.requirements"
            implementationClass = "de.fourteen.gates.requirements.RequirementsPlugin"
            displayName = "Deterministic Gates: requirements"
            description = "Keeps a requirements register honest: annotated-and-passed test " +
                "coverage, source-tagged coverage, and feature docs that reference only real IDs."
        }
        create("layerDisjointness") {
            id = "de.fourteen.gates.layerdisjointness"
            implementationClass = "de.fourteen.gates.layerdisjointness.LayerDisjointnessPlugin"
            displayName = "Deterministic Gates: layerDisjointness"
            description = "Checks that no domain line is covered only by an outer-layer test."
        }
        create("suppressionRegister") {
            id = "de.fourteen.gates.suppressionregister"
            implementationClass = "de.fourteen.gates.suppressionregister.SuppressionRegisterPlugin"
            displayName = "Deterministic Gates: suppressionRegister"
            description = "Checks that every suppression annotation in the code has a " +
                "matching, dated register entry, and vice versa."
        }
        create("gitHooks") {
            id = "de.fourteen.gates.githooks"
            implementationClass = "de.fourteen.gates.githooks.GitHooksPlugin"
            displayName = "Deterministic Gates: gitHooks"
            description = "Installs a Conventional-Commits-checking commit-msg git hook."
        }
    }
}

tasks.test {
    useJUnitPlatform()

    // The functional tests build real consumer projects that need JUnit on *their* test
    // classpath. Handing them the jars this build already resolved keeps those fixtures off the
    // network: a gate test that can fail because a repository rate-limits isn't a gate test.
    val junitJars = configurations.testRuntimeClasspath.map { classpath ->
        classpath.files.filter { jar ->
            listOf("junit", "opentest4j", "apiguardian").any { jar.name.startsWith(it) }
        }.joinToString(File.pathSeparator) { it.absolutePath }
    }
    inputs.property("junitFixtureClasspath", junitJars)
    doFirst { systemProperty("gates.junitClasspath", junitJars.get()) }
}

tasks.named("check") {
    dependsOn(tasks.test)
}
