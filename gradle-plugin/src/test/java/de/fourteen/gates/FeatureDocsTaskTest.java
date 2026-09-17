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

class FeatureDocsTaskTest {

    Path projectDir;
    Project project;
    GatesExtension extension;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        projectDir = tempDir;
        Files.createDirectories(projectDir.resolve("features"));

        project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();
        project.getPluginManager().apply(GatesPlugin.class);
        extension = project.getExtensions().getByType(GatesExtension.class);
        extension.getFeaturesDir().set(project.getLayout().getProjectDirectory().dir("features"));
        extension.getRequirementsFile().set(project.getLayout().getProjectDirectory().file("requirements.md"));

        Files.writeString(projectDir.resolve("requirements.md"), """
                | ID | Description | Category |
                |----|--------------|----------|
                | 4.2 | Join a room | backend |
                """);
    }

    private FeatureDocsTask task() {
        return (FeatureDocsTask) project.getTasks().getByName("featureDocs");
    }

    private void writeDoc(String criteria, String referenceRows, String criticality, boolean withScenarios)
            throws IOException {
        StringBuilder doc = new StringBuilder();
        doc.append("# 001 - Join a room\n\n");
        doc.append("## Motivation\n\nPlayers need a way to join a running watch party.\n\n");
        doc.append("## Affected Requirements\n\n");
        doc.append("| ID | Reference | Note |\n|----|-----------|------|\n");
        doc.append(referenceRows).append("\n");
        doc.append("## Acceptance Criteria\n\n");
        doc.append(criteria).append("\n");
        if (withScenarios) {
            doc.append("## Scenarios\n\nGiven a running room, when a player enters its code, then they join it.\n\n");
        }
        doc.append("## Criticality\n\n");
        doc.append(criticality).append("\n\n");
        doc.append("## Implemented In\n\nJoinRoomTest\n\n");
        doc.append("## Open Questions\n\nNone.\n");
        Files.writeString(projectDir.resolve("features/001-join.md"), doc.toString());
    }

    private static final String ONE_CRITERION = "1. A player can enter a room code.\n";
    private static final String ONE_REFERENCE_ROW = "| 4.2 | existing | join flow |\n";
    private static final String ONE_LEVEL = "**Level:** HIGH";

    @Test
    void passesForAWellFormedDoc() throws IOException {
        writeDoc(ONE_CRITERION, ONE_REFERENCE_ROW, ONE_LEVEL, true);

        assertDoesNotThrow(() -> task().check());
    }

    @Test
    void failsWhenARequiredSectionIsMissing() throws IOException {
        writeDoc(ONE_CRITERION, ONE_REFERENCE_ROW, ONE_LEVEL, false);

        GradleException exception = assertThrows(GradleException.class, () -> task().check());
        assertTrue(exception.getMessage().contains("001-join.md"));
    }

    @Test
    void failsWhenAReferencedIdIsUnknownToTheRegister() throws IOException {
        writeDoc(ONE_CRITERION, ONE_REFERENCE_ROW + "| 9.9 | changed | made up |\n", ONE_LEVEL, true);

        assertThrows(GradleException.class, () -> task().check());
    }

    @Test
    void failsWhenTooManyAcceptanceCriteria() throws IOException {
        StringBuilder manyCriteria = new StringBuilder();
        for (int i = 1; i <= 13; i++) {
            manyCriteria.append(i).append(". Criterion number ").append(i).append(".\n");
        }
        writeDoc(manyCriteria.toString(), ONE_REFERENCE_ROW, ONE_LEVEL, true);

        assertThrows(GradleException.class, () -> task().check());
    }

    @Test
    void failsWhenTwoCriticalityLevelsAreStated() throws IOException {
        writeDoc(ONE_CRITERION, ONE_REFERENCE_ROW, "**Level:** HIGH\n**Level:** MEDIUM", true);

        assertThrows(GradleException.class, () -> task().check());
    }

    @Test
    void failsWhenReferenceTypeIsUnknown() throws IOException {
        writeDoc(ONE_CRITERION, "| 4.2 | maybe | unsure |\n", ONE_LEVEL, true);

        assertThrows(GradleException.class, () -> task().check());
    }
}
