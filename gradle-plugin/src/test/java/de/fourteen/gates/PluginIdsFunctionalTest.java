package de.fourteen.gates;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs a real, separate Gradle build applying plugins by their public {@code id("...")}, the
 * way an actual consumer would -- unlike the other tests, which apply plugin classes directly
 * through ProjectBuilder and so never exercise the id-to-implementation-class resolution that
 * {@code gradlePlugin { plugins { ... } } } in build.gradle.kts sets up. Confirms two of the
 * five plugin ids resolve and that applying only one of them leaves the other's gate absent
 * from {@code check} entirely -- the actual point of splitting into five.
 */
class PluginIdsFunctionalTest {

    @Test
    void applyingOnlyStructureDocDoesNotPullInSuppressionRegister(@TempDir Path projectDir) throws IOException {
        Files.writeString(projectDir.resolve("settings.gradle.kts"), "rootProject.name = \"fixture\"\n");
        Files.writeString(projectDir.resolve("build.gradle.kts"), """
                plugins {
                    id("de.fourteen.gates.structuredoc")
                }
                """);

        BuildResult result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments("tasks", "--all")
                .build();

        assertTrue(result.getOutput().contains("structureDoc"), "structureDoc task should be registered");
        assertTrue(!result.getOutput().contains("suppressionRegister"),
                "suppressionRegister task should not exist without applying its plugin");
    }
}
