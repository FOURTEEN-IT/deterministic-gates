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

// This directory holds only the build definition and the code with no single fachlich owner
// (internal/* -- shared parsing helpers used across gates -- and the cross-plugin functional
// test). Each gate's actual source lives next to its Claude Code counterpart (if any) under its
// own top-level Fachlichkeit directory, not here -- see the repo README for why.
sourceSets {
    main {
        java {
            srcDir("../structure-doc/gradle/src/main/java")
            srcDir("../requirements/gradle/src/main/java")
            srcDir("../layer-disjointness/gradle/src/main/java")
            srcDir("../suppression-register/gradle/src/main/java")
            srcDir("../commit-discipline/gradle/src/main/java")
            srcDir("../feature-slicing/gradle/src/main/java")
        }
        resources {
            srcDir("../commit-discipline/gradle/src/main/resources")
        }
    }
    test {
        java {
            srcDir("../structure-doc/gradle/src/test/java")
            srcDir("../requirements/gradle/src/test/java")
            srcDir("../suppression-register/gradle/src/test/java")
            srcDir("../feature-slicing/gradle/src/test/java")
        }
    }
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

// Six plugins, not one: structureDoc, layerDisjointness and suppressionRegister check three
// unrelated things and can be adopted independently; requirementsCoverage,
// taggedRequirementsCoverage and featureDocs stay together in "requirements" because all three
// read the same requirements register through the same parser (see
// requirements/RequirementsExtension.java) -- splitting those three further would mean
// duplicating that parser instead of sharing it. sliceStructure and sliceCoverage stay together
// in "featureSlicing" for the same reason: both read the same slice tree under featuresDir (see
// feature-slicing/FeatureSlicingExtension.java).
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
        create("featureSlicing") {
            id = "de.fourteen.gates.featureslicing"
            implementationClass = "de.fourteen.gates.featureslicing.FeatureSlicingPlugin"
            displayName = "Deterministic Gates: featureSlicing"
            description = "Checks that a feature's slice tree stays numbered consistently, " +
                "that each slice's status agrees with its folder structure, and that every " +
                "leaf slice is covered by a passed, annotated test."
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
