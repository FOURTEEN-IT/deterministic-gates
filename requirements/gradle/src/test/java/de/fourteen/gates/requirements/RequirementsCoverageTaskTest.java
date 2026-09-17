package de.fourteen.gates.requirements;

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
 * XML result the way a real test run would, and let the task cross-reference them -- rather than
 * mocking any of the three. Uses a fixture-local annotation (not this repo's own
 * {@code annotations} module) so the test doesn't depend on that module having been built first;
 * {@link RequirementsPluginConventionsTest} separately locks in that the shipped one is the default.
 */
class RequirementsCoverageTaskTest {

    Path projectDir;
    Path classesDir;
    Path resultsDir;
    Project project;
    RequirementsExtension extension;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        projectDir = tempDir.resolve("project");
        Path srcDir = tempDir.resolve("src");
        classesDir = tempDir.resolve("classes");
        resultsDir = tempDir.resolve("test-results");
        Files.createDirectories(projectDir);
        Files.createDirectories(srcDir.resolve("fixture"));
        Files.createDirectories(classesDir);
        Files.createDirectories(resultsDir);

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
                    @Marker("4.2")
                    public void passedAndAnnotated() {}

                    @Marker("9.9")
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

        Files.writeString(projectDir.resolve("requirements.md"), """
                | ID | Description | Category |
                |----|--------------|----------|
                | 4.2 | Covered by a passed, annotated test | backend |
                """);

        project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();
        project.getPluginManager().apply(RequirementsPlugin.class);
        extension = project.getExtensions().getByType(RequirementsExtension.class);
        extension.getRequirementsFile().set(project.getLayout().getProjectDirectory().file("requirements.md"));
        extension.getRequirementAnnotationFqn().set("fixture.Marker");
        extension.getTestClassesDirs().setFrom(classesDir.toFile());
        extension.getTestResultsDirs().setFrom(resultsDir.toFile());
        extension.getTestRuntimeClasspath().setFrom(classesDir.toFile());
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

    private RequirementsCoverageTask task() {
        return (RequirementsCoverageTask) project.getTasks().getByName("requirementsCoverage");
    }

    @Test
    void passesWhenTheOnlyRequiredIdIsCoveredByAPassedAnnotatedMethod() {
        assertDoesNotThrow(() -> task().check());
    }

    @Test
    void failsWhenARequiredIdHasNoPassedAnnotatedMethod() throws IOException {
        Files.writeString(projectDir.resolve("requirements.md"), """
                | ID | Description | Category |
                |----|--------------|----------|
                | 4.2 | Covered by a passed, annotated test | backend |
                | 9.9 | Only annotated on a method that never ran | backend |
                """);

        GradleException exception = assertThrows(GradleException.class, () -> task().check());
        assertTrue(exception.getMessage().contains("9.9"));
    }
}
