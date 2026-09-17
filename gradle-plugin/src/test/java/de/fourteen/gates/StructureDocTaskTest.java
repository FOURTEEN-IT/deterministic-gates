package de.fourteen.gates;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureDocTaskTest {

    Path projectDir;
    Project project;
    GatesExtension extension;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        projectDir = tempDir;
        Files.createDirectories(projectDir.resolve("domain"));

        project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();
        project.getPluginManager().apply(GatesPlugin.class);
        extension = project.getExtensions().getByType(GatesExtension.class);
        extension.getArchitectureDocFile().set(project.getLayout().getProjectDirectory().file("ARCHITECTURE.md"));
        extension.getDomainModelDir().set(project.getLayout().getProjectDirectory().dir("domain"));
    }

    private StructureDocTask task() {
        return (StructureDocTask) project.getTasks().getByName("structureDoc");
    }

    @Test
    void passesWhenDocAndTreeAgree() throws IOException {
        Files.writeString(projectDir.resolve("domain/Room.java"), "class Room {}");
        Files.writeString(projectDir.resolve("ARCHITECTURE.md"),
                "The domain model has one type, Room.java, which is the aggregate root.");

        assertDoesNotThrow(() -> task().check());
    }

    @Test
    void failsWhenDocNamesAFileThatDoesNotExist() throws IOException {
        Files.writeString(projectDir.resolve("domain/Room.java"), "class Room {}");
        Files.writeString(projectDir.resolve("ARCHITECTURE.md"),
                "The domain model has Room.java and also GoneAway.java, which was deleted.");

        GradleException exception = assertThrows(GradleException.class, () -> task().check());
        assertTrue(exception.getMessage().contains("GoneAway.java"));
    }

    @Test
    void failsWhenADomainTypeIsNotMentioned() throws IOException {
        Files.writeString(projectDir.resolve("domain/Room.java"), "class Room {}");
        Files.writeString(projectDir.resolve("domain/Player.java"), "class Player {}");
        Files.writeString(projectDir.resolve("ARCHITECTURE.md"), "The domain model has Room.java.");

        GradleException exception = assertThrows(GradleException.class, () -> task().check());
        assertTrue(exception.getMessage().contains("Player"));
    }

    @Test
    void allowedMissingNamesAreNotFlagged() throws IOException {
        extension.getStructureDocAllowedMissingNames().set(java.util.List.of("PLACEHOLDER.md"));
        Files.writeString(projectDir.resolve("domain/Room.java"), "class Room {}");
        Files.writeString(projectDir.resolve("ARCHITECTURE.md"),
                "Room.java is the aggregate root. See PLACEHOLDER.md for the template.");

        assertDoesNotThrow(() -> task().check());
    }
}
