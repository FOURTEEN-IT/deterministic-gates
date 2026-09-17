package de.fourteen.gates.testlayers;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixture-local tag and test annotations rather than JUnit's own: the fixture sources are
 * compiled in-process, so depending on JUnit would mean handing the compiler a classpath it
 * doesn't otherwise need -- and doing without proves the configurable annotation names work,
 * which a run against the hardcoded defaults never would.
 */
class TestLayersTaskTest {

    private static final String TAG = """
            package fixture;
            import java.lang.annotation.*;
            @Retention(RetentionPolicy.RUNTIME)
            @Target({ElementType.TYPE, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
            public @interface Tag {
                String value();
            }
            """;

    private static final String CASE = """
            package fixture;
            import java.lang.annotation.*;
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.METHOD)
            public @interface Case {
            }
            """;

    /** A project's own layer annotation, carrying the tag as a meta-annotation. */
    private static final String UNIT_TEST = """
            package fixture;
            import java.lang.annotation.*;
            @Retention(RetentionPolicy.RUNTIME)
            @Target({ElementType.TYPE, ElementType.METHOD})
            @Tag("unit")
            public @interface UnitTest {
            }
            """;

    private static final String UNIT_CLASS = """
            package fixture;
            @UnitTest
            public class SettlementTest {
                @Case public void splitsThePool() {}
            }
            """;

    private static final String PORT_CLASS = """
            package fixture;
            @Tag("port")
            public class RoomCommandsTest {
                @Case public void enqueues() {}
            }
            """;

    @Test
    void passesWhenEveryTestMethodCarriesExactlyOneLayerTag(@TempDir Path tempDir) throws IOException {
        Fixture fixture = fixture(tempDir, List.of("unit", "port"), UNIT_CLASS, PORT_CLASS);

        assertDoesNotThrow(() -> fixture.task().check());
    }

    @Test
    void failsOnATestMethodThatBelongsToNoLayer(@TempDir Path tempDir) throws IOException {
        String untagged = """
                package fixture;
                public class ForgottenTest {
                    @Case public void neverRunsAnywhere() {}
                }
                """;
        Fixture fixture = fixture(tempDir, List.of("unit", "port"), UNIT_CLASS, PORT_CLASS, untagged);

        GradleException exception = assertThrows(GradleException.class, () -> fixture.task().check());
        assertTrue(exception.getMessage().contains("ForgottenTest#neverRunsAnywhere"));
    }

    @Test
    void failsOnATestMethodThatBelongsToTwoLayers(@TempDir Path tempDir) throws IOException {
        String both = """
                package fixture;
                @UnitTest
                public class AmbiguousTest {
                    @Tag("port") @Case public void runsTwice() {}
                }
                """;
        Fixture fixture = fixture(tempDir, List.of("unit", "port"), PORT_CLASS, both);

        GradleException exception = assertThrows(GradleException.class, () -> fixture.task().check());
        assertTrue(exception.getMessage().contains("AmbiguousTest#runsTwice"));
    }

    @Test
    void failsOnALayerWithNoTestMethodAtAll(@TempDir Path tempDir) throws IOException {
        Fixture fixture = fixture(tempDir, List.of("unit", "port", "api"), UNIT_CLASS, PORT_CLASS);

        GradleException exception = assertThrows(GradleException.class, () -> fixture.task().check());
        assertTrue(exception.getMessage().contains("api"));
    }

    @Test
    void ignoresClassesWithoutTestMethods(@TempDir Path tempDir) throws IOException {
        // Hand-written test doubles, JGiven stages, builders: untagged on purpose, and not tests.
        String testDouble = """
                package fixture;
                public class FakeClientGateway {
                    public void send(String frame) {}
                }
                """;
        Fixture fixture = fixture(tempDir, List.of("unit", "port"), UNIT_CLASS, PORT_CLASS, testDouble);

        assertDoesNotThrow(() -> fixture.task().check());
    }

    @Test
    void taskIsSkippedInTheBuildGraphUntilLayersAreNamed(@TempDir Path freshProjectDir) {
        Project project = ProjectBuilder.builder().withProjectDir(freshProjectDir.toFile()).build();
        project.getPluginManager().apply(TestLayersPlugin.class);
        TestLayersExtension extension = project.getExtensions().getByType(TestLayersExtension.class);
        TestLayersTask task = (TestLayersTask) project.getTasks().getByName("testLayers");

        assertFalse(task.getOnlyIf().isSatisfiedBy(task),
                "task should be skipped in the build graph while no layers are named");

        extension.getLayers().set(List.of("unit"));
        assertTrue(task.getOnlyIf().isSatisfiedBy(task),
                "task should attach to the build graph once layers are named");
    }

    private record Fixture(Project project) {

        TestLayersTask task() {
            return (TestLayersTask) project.getTasks().getByName("testLayers");
        }
    }

    private static Fixture fixture(Path tempDir, List<String> layers, String... classSources) throws IOException {
        Path projectDir = tempDir.resolve("project");
        Path srcDir = tempDir.resolve("src");
        Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(projectDir);
        Files.createDirectories(classesDir);

        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("Tag", TAG);
        sources.put("Case", CASE);
        sources.put("UnitTest", UNIT_TEST);
        for (String source : classSources) {
            sources.put(simpleNameOf(source), source);
        }
        compile(srcDir, classesDir, sources);

        Project project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();
        project.getPluginManager().apply(TestLayersPlugin.class);
        TestLayersExtension extension = project.getExtensions().getByType(TestLayersExtension.class);
        extension.getLayers().set(layers);
        extension.getTagAnnotationFqns().set(List.of("fixture.Tag"));
        extension.getTestMethodAnnotationFqns().set(List.of("fixture.Case"));
        Fixture fixture = new Fixture(project);
        // Without the `java` plugin applied, TestLayersPlugin never wires these from a source set.
        fixture.task().getTestClassesDirs().setFrom(classesDir.toFile());
        fixture.task().getClasspath().setFrom(classesDir.toFile());
        return fixture;
    }

    private static String simpleNameOf(String source) {
        for (String keyword : List.of("public class ", "public @interface ")) {
            int start = source.indexOf(keyword);
            if (start >= 0) {
                return source.substring(start + keyword.length()).split("[ \\n{]")[0];
            }
        }
        throw new IllegalArgumentException("fixture source declares no public type: " + source);
    }

    private static void compile(Path srcDir, Path classesDir, Map<String, String> sources) throws IOException {
        Files.createDirectories(srcDir.resolve("fixture"));
        List<File> files = new ArrayList<>();
        for (Map.Entry<String, String> source : sources.entrySet()) {
            Path file = srcDir.resolve("fixture/" + source.getKey() + ".java");
            Files.writeString(file, source.getValue());
            files.add(file.toFile());
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(classesDir.toFile()));
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromFiles(files);
            boolean success = compiler.getTask(null, fileManager, null, null, null, units).call();
            assertTrue(success, "fixture sources must compile");
        }
    }
}
