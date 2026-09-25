package de.fourteen.gates.featureslicing;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the real mechanism -- compile a fixture annotation + test class, hand-write a JUnit
 * XML result the way a real test run would, and let the task cross-reference them against the
 * leaf slices found under {@code featuresDir} -- rather than mocking any of the three. Mirrors
 * {@code RequirementsCoverageTaskTest} in the {@code requirements} plugin.
 */
class SliceCoverageTaskTest {

    Path projectDir;
    Path featuresDir;
    Path classesDir;
    Path resultsDir;
    Project project;
    FeatureSlicingExtension extension;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        projectDir = tempDir.resolve("project");
        Path srcDir = tempDir.resolve("src");
        featuresDir = projectDir.resolve("features");
        classesDir = tempDir.resolve("classes");
        resultsDir = tempDir.resolve("test-results");
        Files.createDirectories(featuresDir);
        Files.createDirectories(srcDir.resolve("fixture"));
        Files.createDirectories(classesDir);
        Files.createDirectories(resultsDir);

        Files.writeString(featuresDir.resolve("4.2.md"), "# Join a room\n");
        writeSlice("4.2/1.md", "ready for implementation");

        Files.writeString(srcDir.resolve("fixture/Marker.java"), """
                package fixture;
                import java.lang.annotation.*;
                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.METHOD)
                public @interface Marker {
                    String[] value();
                }
                """);
        Files.writeString(srcDir.resolve("fixture/FixtureTest.java"), """
                package fixture;
                public class FixtureTest {
                    @Marker("4.2.1")
                    public void passedAndAnnotated() {}

                    @Marker("9.9.9")
                    public void annotatedButNeverRan() {}
                }
                """);
        compile(srcDir, classesDir);

        Files.writeString(resultsDir.resolve("TEST-fixture.FixtureTest.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="fixture.FixtureTest" tests="1">
                    <testcase classname="fixture.FixtureTest" name="passedAndAnnotated"/>
                </testsuite>
                """);

        project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();
        project.getPluginManager().apply(FeatureSlicingPlugin.class);
        extension = project.getExtensions().getByType(FeatureSlicingExtension.class);
        extension.getFeaturesDir().set(project.getLayout().getProjectDirectory().dir("features"));
        extension.getRequirementAnnotationFqn().set("fixture.Marker");
        extension.getTestClassesDirs().setFrom(classesDir.toFile());
        extension.getTestResultsDirs().setFrom(resultsDir.toFile());
        extension.getTestRuntimeClasspath().setFrom(classesDir.toFile());
    }

    private void writeSlice(String relativePath, String status) throws IOException {
        Path doc = featuresDir.resolve(relativePath);
        Files.createDirectories(doc.getParent());
        Files.writeString(doc, "## Status\n\n**Status:** " + status + "\n");
    }

    private static void compile(Path srcDir, Path classesDir) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(classesDir.toFile()));
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromFiles(List.of(
                    srcDir.resolve("fixture/Marker.java").toFile(),
                    srcDir.resolve("fixture/FixtureTest.java").toFile()));
            boolean success = compiler.getTask(null, fileManager, null, null, null, units).call();
            assertTrue(success, "fixture sources must compile");
        }
    }

    private SliceCoverageTask task() {
        return (SliceCoverageTask) project.getTasks().getByName("sliceCoverage");
    }

    @Test
    void passesWhenTheOnlyLeafSliceIsCoveredByAPassedAnnotatedMethod() {
        assertDoesNotThrow(() -> task().check());
    }

    @Test
    void failsWhenALeafSliceHasNoPassedAnnotatedMethod() throws IOException {
        writeSlice("4.2/2.md", "ready for implementation");

        GradleException exception = assertThrows(GradleException.class, () -> task().check());
        assertTrue(exception.getMessage().contains("4.2.2"));
    }

    @Test
    void doesNotRequireCoverageForAnUnsplitFeature() throws IOException {
        Files.writeString(featuresDir.resolve("9.1.md"), "# Not sliced yet\n");

        assertDoesNotThrow(() -> task().check());
    }

    @Test
    void doesNotRequireCoverageForANonLeafSlice() throws IOException {
        writeSlice("4.2/1.md", "needs splitting");
        writeSlice("4.2/1/1.md", "ready for implementation");

        GradleException exception = assertThrows(GradleException.class, () -> task().check());
        // 4.2.1 itself was split further and is no longer a leaf -- only 4.2.1.1 must be covered,
        // and it's the only open one reported (a message naming just "[4.2.1.1]").
        assertTrue(exception.getMessage().contains("[4.2.1.1]"));
    }
}
