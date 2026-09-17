package de.fourteen.gates.featureslicing;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SliceStructureTaskTest {

    Path projectDir;
    Path featuresDir;
    Project project;
    FeatureSlicingExtension extension;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        projectDir = tempDir;
        featuresDir = projectDir.resolve("features");
        Files.createDirectories(featuresDir);

        project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();
        project.getPluginManager().apply(FeatureSlicingPlugin.class);
        extension = project.getExtensions().getByType(FeatureSlicingExtension.class);
        extension.getFeaturesDir().set(project.getLayout().getProjectDirectory().dir("features"));

        Files.writeString(featuresDir.resolve("4.2.md"), "# Join a room\n");
    }

    private SliceStructureTask task() {
        return (SliceStructureTask) project.getTasks().getByName("sliceStructure");
    }

    private void writeSlice(String relativePath, String status) throws IOException {
        writeSlice(relativePath, status, "A player can now enter a room code.", 1);
    }

    private void writeSlice(String relativePath, String status, String userOutcome, int acceptanceCriteria)
            throws IOException {
        Path doc = featuresDir.resolve(relativePath);
        Files.createDirectories(doc.getParent());
        StringBuilder criteria = new StringBuilder();
        IntStream.rangeClosed(1, acceptanceCriteria)
                .forEach(i -> criteria.append(i).append(". Criterion number ").append(i).append(".\n"));
        Files.writeString(doc, "## Status\n\n**Status:** " + status + "\n\n"
                + "## User Outcome\n\n" + userOutcome + "\n\n"
                + "## Acceptance Criteria\n\n" + criteria + "\n");
    }

    @Test
    void passesForAFeatureWithNoSlicesYet() {
        assertDoesNotThrow(() -> task().check());
    }

    @Test
    void passesForASingleReadyLeafSlice() throws IOException {
        writeSlice("4.2/1.md", "ready for implementation");

        assertDoesNotThrow(() -> task().check());
    }

    @Test
    void passesForANestedTreeWithConsistentStatuses() throws IOException {
        writeSlice("4.2/1.md", "needs splitting");
        writeSlice("4.2/1/1.md", "ready for implementation");
        writeSlice("4.2/1/2.md", "ready for implementation");
        writeSlice("4.2/2.md", "ready for implementation");

        assertDoesNotThrow(() -> task().check());
    }

    @Test
    void failsWhenASliceHasNoStatusLine() throws IOException {
        Path doc = featuresDir.resolve("4.2/1.md");
        Files.createDirectories(doc.getParent());
        Files.writeString(doc, "# A slice with no status\n");

        GradleException exception = assertThrows(GradleException.class, () -> task().check());
        assertTrue(exception.getMessage().contains("4.2.1"));
    }

    @Test
    void failsWhenReadyForImplementationButChildrenExist() throws IOException {
        writeSlice("4.2/1.md", "ready for implementation");
        writeSlice("4.2/1/1.md", "ready for implementation");

        GradleException exception = assertThrows(GradleException.class, () -> task().check());
        assertTrue(exception.getMessage().contains("4.2.1"));
    }

    @Test
    void failsWhenNeedsSplittingButNoChildrenExist() throws IOException {
        writeSlice("4.2/1.md", "needs splitting");

        GradleException exception = assertThrows(GradleException.class, () -> task().check());
        assertTrue(exception.getMessage().contains("4.2.1"));
    }

    @Test
    void failsWhenSliceNumberingHasAGap() throws IOException {
        writeSlice("4.2/1.md", "ready for implementation");
        writeSlice("4.2/3.md", "ready for implementation");

        assertThrows(GradleException.class, () -> task().check());
    }

    @Test
    void failsWhenAnUnknownStatusValueIsUsed() throws IOException {
        writeSlice("4.2/1.md", "maybe later");

        GradleException exception = assertThrows(GradleException.class, () -> task().check());
        assertTrue(exception.getMessage().contains("4.2.1"));
    }

    @Test
    void failsWhenTheUserOutcomeSectionIsMissing() throws IOException {
        Path doc = featuresDir.resolve("4.2/1.md");
        Files.createDirectories(doc.getParent());
        Files.writeString(doc, "## Status\n\n**Status:** ready for implementation\n");

        GradleException exception = assertThrows(GradleException.class, () -> task().check());
        assertTrue(exception.getMessage().contains("4.2.1"));
    }

    @Test
    void failsWhenTheUserOutcomeSectionIsEmpty() throws IOException {
        writeSlice("4.2/1.md", "ready for implementation", "", 1);

        GradleException exception = assertThrows(GradleException.class, () -> task().check());
        assertTrue(exception.getMessage().contains("4.2.1"));
    }

    @Test
    void passesButNoticesWhenMoreThanTwelveAcceptanceCriteria() throws IOException {
        writeSlice("4.2/1.md", "ready for implementation", "A player can now enter a room code.", 13);

        assertDoesNotThrow(() -> task().check());

        String report = Files.readString(task().getReportFile().get().getAsFile().toPath());
        assertTrue(report.contains("4.2.1"));
        assertTrue(report.contains("13 acceptance criteria"));
        assertTrue(report.contains("re-examine whether this slice is really unsplittable"));
    }
}
