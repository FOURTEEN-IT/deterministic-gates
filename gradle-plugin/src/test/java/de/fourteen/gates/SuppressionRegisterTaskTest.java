package de.fourteen.gates;

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
 * Uses a fixture-local suppression annotation for the same reason as
 * {@link RequirementsCoverageTaskTest}: exercises the real compile-reflect-diff mechanism
 * without depending on the {@code annotations} module having been built first.
 */
class SuppressionRegisterTaskTest {

    Path projectDir;
    Path classesDir;
    Project project;
    GatesExtension extension;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        projectDir = tempDir.resolve("project");
        Path srcDir = tempDir.resolve("src");
        classesDir = tempDir.resolve("classes");
        Files.createDirectories(projectDir);
        Files.createDirectories(srcDir.resolve("fixture"));
        Files.createDirectories(classesDir);

        Files.writeString(srcDir.resolve("fixture/Suppressed.java"), """
                package fixture;
                import java.lang.annotation.*;
                @Retention(RetentionPolicy.RUNTIME)
                @Target({ElementType.TYPE, ElementType.METHOD})
                public @interface Suppressed {
                }
                """);
        Files.writeString(srcDir.resolve("fixture/FlakyTest.java"), """
                package fixture;
                public class FlakyTest {
                    @Suppressed
                    public void aFlakyMethod() {}
                }
                """);
        compile(srcDir, classesDir);

        project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();
        project.getPluginManager().apply(GatesPlugin.class);
        extension = project.getExtensions().getByType(GatesExtension.class);
        extension.getExceptionsRegisterFile().set(project.getLayout().getProjectDirectory().file("exceptions.md"));
        extension.getSuppressionAnnotationFqns().set(List.of("fixture.Suppressed"));

        // Without the `java` plugin applied, GatesPlugin never wires these from a source set
        // (see GatesPlugin.apply) -- set them directly on the task, the way a project without
        // `java` (but with its own compiled-classes convention) would have to anyway.
        task().getMainClassesDirs().setFrom(classesDir.toFile());
        task().getClasspath().setFrom(classesDir.toFile());
    }

    private static void compile(Path srcDir, Path classesDir) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(classesDir.toFile()));
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromFiles(List.of(
                    srcDir.resolve("fixture/Suppressed.java").toFile(),
                    srcDir.resolve("fixture/FlakyTest.java").toFile()));
            boolean success = compiler.getTask(null, fileManager, null, null, null, units).call();
            assertTrue(success, "fixture sources must compile");
        }
    }

    private SuppressionRegisterTask task() {
        return (SuppressionRegisterTask) project.getTasks().getByName("suppressionRegister");
    }

    @Test
    void passesWhenEveryCodeSuppressionHasAMatchingRegisterEntry() throws IOException {
        Files.writeString(projectDir.resolve("exceptions.md"), """
                | Suppressed | Reason | Date |
                |------------|--------|------|
                | FlakyTest.aFlakyMethod | third-party flakiness in CI | 2026-01-01 |
                """);

        assertDoesNotThrow(() -> task().check());
    }

    @Test
    void failsWhenACodeSuppressionHasNoRegisterEntry() throws IOException {
        Files.writeString(projectDir.resolve("exceptions.md"), """
                | Suppressed | Reason | Date |
                |------------|--------|------|
                """);

        GradleException exception = assertThrows(GradleException.class, () -> task().check());
        assertTrue(exception.getMessage().contains("FlakyTest.aFlakyMethod"));
    }

    @Test
    void failsWhenARegisterEntryHasNoMatchingSuppression() throws IOException {
        Files.writeString(projectDir.resolve("exceptions.md"), """
                | Suppressed | Reason | Date |
                |------------|--------|------|
                | FlakyTest.aFlakyMethod | third-party flakiness in CI | 2026-01-01 |
                | LongGoneTest.deletedMethod | stale | 2025-01-01 |
                """);

        GradleException exception = assertThrows(GradleException.class, () -> task().check());
        assertTrue(exception.getMessage().contains("LongGoneTest.deletedMethod"));
    }
}
