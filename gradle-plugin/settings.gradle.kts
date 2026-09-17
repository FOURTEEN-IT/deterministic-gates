plugins {
    // Lets Gradle download the toolchain's JDK 17 itself on a machine that doesn't already
    // have one, instead of failing the build with a "no matching toolchain" error.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "deterministic-gates-gradle-plugin"
