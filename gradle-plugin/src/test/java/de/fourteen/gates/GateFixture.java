package de.fourteen.gates;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A real consumer project on disk, driven through a real Gradle build -- the only kind of test
 * that exercises what an adopter actually runs: plugin ids resolving, gates attaching to
 * {@code check}, and the task ordering between a gate and whatever produces the files it reads.
 * The {@code ProjectBuilder} tests elsewhere call a task's {@code check()} method directly and
 * so see none of that.
 */
final class GateFixture {

    private final Path dir;
    private final List<String> pluginIds = new ArrayList<>();
    private final StringBuilder config = new StringBuilder();
    private boolean java;

    GateFixture(Path dir) {
        this.dir = dir;
    }

    GateFixture plugin(String id) {
        pluginIds.add(id);
        return this;
    }

    /** Adds the `java` plugin and JUnit, wired to the jars this build already resolved. */
    GateFixture withJava() {
        java = true;
        return this;
    }

    GateFixture config(String block) {
        config.append(block).append("\n");
        return this;
    }

    GateFixture file(String relativePath, String content) throws IOException {
        Path target = dir.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
        return this;
    }

    private void write() throws IOException {
        Files.writeString(dir.resolve("settings.gradle.kts"), "rootProject.name = \"fixture\"\n");

        StringBuilder build = new StringBuilder("plugins {\n");
        if (java) {
            build.append("    java\n");
        }
        for (String id : pluginIds) {
            build.append("    id(\"").append(id).append("\")\n");
        }
        build.append("}\n\n");

        if (java) {
            // Deliberately no repositories block: the fixture resolves nothing over the network.
            String junitClasspath = System.getProperty("gates.junitClasspath");
            assertTrue(junitClasspath != null && !junitClasspath.isEmpty(),
                    "gates.junitClasspath system property must be set by the test task");
            build.append("dependencies {\n");
            for (String jar : junitClasspath.split(File.pathSeparator)) {
                build.append("    testImplementation(files(\"")
                        .append(jar.replace("\\", "\\\\"))
                        .append("\"))\n");
            }
            build.append("}\n\n");
            build.append("tasks.test { useJUnitPlatform() }\n\n");
        }

        build.append(config);
        Files.writeString(dir.resolve("build.gradle.kts"), build.toString());
    }

    /**
     * Runs the given tasks twice with the configuration cache on, asserting both runs succeed
     * and that the second one actually reuses the stored entry -- a task reaching for the
     * project at execution time only fails on that second, reusing run.
     */
    BuildResult runTwiceWithConfigurationCache(String... tasks) throws IOException {
        write();
        run(tasks);
        BuildResult second = run(tasks);
        assertTrue(second.getOutput().contains("Reusing configuration cache"),
                "second run should reuse the configuration cache entry, output was:\n" + second.getOutput());
        return second;
    }

    /** Runs the given tasks once, expecting the build to fail, and returns the result. */
    BuildResult runExpectingFailure(String... tasks) throws IOException {
        write();
        return runner(tasks).buildAndFail();
    }

    private BuildResult run(String... tasks) {
        return runner(tasks).build();
    }

    private GradleRunner runner(String... tasks) {
        List<String> arguments = new ArrayList<>(List.of(tasks));
        arguments.add("--configuration-cache");
        return GradleRunner.create()
                .withProjectDir(dir.toFile())
                .withPluginClasspath()
                .withArguments(arguments);
    }
}
