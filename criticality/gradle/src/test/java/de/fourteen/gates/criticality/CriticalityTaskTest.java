package de.fourteen.gates.criticality;

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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Uses a fixture-local criticality annotation for the same reason as
 * SuppressionRegisterTaskTest: exercises the real compile-reflect-check mechanism without
 * depending on the {@code annotations} module having been built first -- and, here, doubles as
 * the evidence that a project's own annotation works as long as it offers level() and
 * requirements().
 */
class CriticalityTaskTest {

    private static final String ANNOTATION = """
            package fixture;
            import java.lang.annotation.*;
            @Retention(RetentionPolicy.RUNTIME)
            @Target({ElementType.TYPE, ElementType.METHOD})
            public @interface Crit {
                enum Level { LOW, MEDIUM, HIGH }
                Level level();
                String[] requirements();
            }
            """;

    private static final String HIGH_CLASS = """
            package fixture;
            @Crit(level = Crit.Level.HIGH, requirements = {"4.2"})
            public class Settlement {
            }
            """;

    private static final String METHOD_LEVEL_CLASS = """
            package fixture;
            public class Ledger {
                @Crit(level = Crit.Level.MEDIUM, requirements = {"7.1"})
                public void post() {}
            }
            """;

    private static final String REGISTER = """
            | ID  | Reference      | Category |
            |-----|----------------|----------|
            | 4.2 | settle a round | backend  |
            | 7.1 | post a ledger  | backend  |
            """;

    @Test
    void passesWhenEveryIdExistsAndTheDerivedLevelIsPopulated(@TempDir Path tempDir) throws IOException {
        Fixture fixture = fixture(tempDir, REGISTER, HIGH_CLASS, METHOD_LEVEL_CLASS);

        assertDoesNotThrow(() -> fixture.task().check());
    }

    @Test
    void failsOnARequirementIdThatIsNotInTheRegister(@TempDir Path tempDir) throws IOException {
        String registerWithout42 = """
                | ID  | Reference     | Category |
                |-----|---------------|----------|
                | 7.1 | post a ledger | backend  |
                """;
        Fixture fixture = fixture(tempDir, registerWithout42, HIGH_CLASS, METHOD_LEVEL_CLASS);

        GradleException exception = assertThrows(GradleException.class, () -> fixture.task().check());
        assertTrue(exception.getMessage().contains("fixture.Settlement"));
        assertTrue(exception.getMessage().contains("4.2"));
    }

    @Test
    void failsOnACriticalityThatNamesNoRequirementAtAll(@TempDir Path tempDir) throws IOException {
        String withoutIds = """
                package fixture;
                @Crit(level = Crit.Level.HIGH, requirements = {})
                public class Unjustified {
                }
                """;
        Fixture fixture = fixture(tempDir, REGISTER, HIGH_CLASS, withoutIds);

        GradleException exception = assertThrows(GradleException.class, () -> fixture.task().check());
        assertTrue(exception.getMessage().contains("fixture.Unjustified"));
    }

    @Test
    void failsWhenTheLevelTheBuildDerivesFromIsEmpty(@TempDir Path tempDir) throws IOException {
        // The dangerous case: nothing is HIGH, so a mutation run targeting HIGH would mutate
        // nothing and pass -- indistinguishable from a real run.
        Fixture fixture = fixture(tempDir, REGISTER, METHOD_LEVEL_CLASS);

        GradleException exception = assertThrows(GradleException.class, () -> fixture.task().check());
        assertTrue(exception.getMessage().contains("HIGH"));
    }

    @Test
    void classesAtCollectsClassLevelAndMethodLevelAnnotations(@TempDir Path tempDir) throws IOException {
        Fixture fixture = fixture(tempDir, REGISTER, HIGH_CLASS, METHOD_LEVEL_CLASS);

        assertEquals(Set.of("fixture.Settlement"), fixture.extension().classesAt("HIGH").get());
        assertEquals(Set.of("fixture.Ledger"), fixture.extension().classesAt("MEDIUM").get());
    }

    @Test
    void classesAtFailsRatherThanHandingBackAnEmptyTargetSet(@TempDir Path tempDir) throws IOException {
        Fixture fixture = fixture(tempDir, REGISTER, METHOD_LEVEL_CLASS);

        GradleException exception = assertThrows(GradleException.class,
                () -> fixture.extension().classesAt("HIGH").get());
        assertTrue(exception.getMessage().contains("HIGH"));
    }

    @Test
    void taskIsSkippedInTheBuildGraphUntilRequirementsFileIsConfigured(@TempDir Path freshProjectDir) {
        Project project = ProjectBuilder.builder().withProjectDir(freshProjectDir.toFile()).build();
        project.getPluginManager().apply(CriticalityPlugin.class);
        CriticalityExtension extension = project.getExtensions().getByType(CriticalityExtension.class);
        CriticalityTask task = (CriticalityTask) project.getTasks().getByName("criticality");

        assertFalse(task.getOnlyIf().isSatisfiedBy(task),
                "task should be skipped in the build graph while requirementsFile is unset");

        extension.getRequirementsFile().set(project.getLayout().getProjectDirectory().file("requirements.md"));
        assertTrue(task.getOnlyIf().isSatisfiedBy(task),
                "task should attach to the build graph once requirementsFile is set");
    }

    private record Fixture(Project project, CriticalityExtension extension) {

        CriticalityTask task() {
            return (CriticalityTask) project.getTasks().getByName("criticality");
        }
    }

    private static Fixture fixture(Path tempDir, String register, String... classSources) throws IOException {
        Path projectDir = tempDir.resolve("project");
        Path srcDir = tempDir.resolve("src");
        Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(projectDir);
        Files.createDirectories(classesDir);

        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("Crit", ANNOTATION);
        for (String source : classSources) {
            sources.put(simpleNameOf(source), source);
        }
        compile(srcDir, classesDir, sources);
        Files.writeString(projectDir.resolve("requirements.md"), register);

        Project project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();
        project.getPluginManager().apply(CriticalityPlugin.class);
        CriticalityExtension extension = project.getExtensions().getByType(CriticalityExtension.class);
        extension.getRequirementsFile().set(project.getLayout().getProjectDirectory().file("requirements.md"));
        extension.getCriticalityAnnotationFqn().set("fixture.Crit");
        // Without the `java` plugin applied, CriticalityPlugin never wires these from a source
        // set -- set them the way a project without `java` would have to anyway.
        extension.getMainClassesDirs().setFrom(classesDir.toFile());
        extension.getClasspath().setFrom(classesDir.toFile());
        Fixture fixture = new Fixture(project, extension);
        fixture.task().getMainClassesDirs().setFrom(classesDir.toFile());
        fixture.task().getClasspath().setFrom(classesDir.toFile());
        return fixture;
    }

    private static String simpleNameOf(String source) {
        for (String keyword : List.of("public class ", "public @interface ")) {
            int start = source.indexOf(keyword);
            if (start >= 0) {
                String rest = source.substring(start + keyword.length());
                return rest.split("[ \\n{]")[0];
            }
        }
        throw new IllegalArgumentException("fixture source declares no public type: " + source);
    }

    private static void compile(Path srcDir, Path classesDir, Map<String, String> sources) throws IOException {
        Files.createDirectories(srcDir.resolve("fixture"));
        List<java.io.File> files = new ArrayList<>();
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
