plugins {
    `java-library`
    `maven-publish`
}

group = "de.fourteen.gates"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

// No dependencies: these are plain marker annotations, read by reflection from the
// gradle-plugin module -- nothing here needs anything beyond the JDK's own annotation API.
dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "de.fourteen.gates"
            artifactId = "annotations"
            version = project.version.toString()
        }
    }
}
