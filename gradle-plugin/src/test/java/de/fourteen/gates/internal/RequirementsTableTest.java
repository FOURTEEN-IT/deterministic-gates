package de.fourteen.gates.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequirementsTableTest {

    @Test
    void parsesRowsRegardlessOfSurroundingHeadings(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("requirements.md");
        Files.writeString(file, """
                # Requirements

                Some prose that should be ignored entirely.

                | ID | Description | Category |
                |----|--------------|----------|
                | 1.1 | Players can join a room | backend |
                | 1.2 | The join form validates the code | frontend |
                | 2.1-a | Something with a suffixed id | backend |

                More prose after the table.
                """);

        assertEquals(Set.of("1.1", "1.2", "2.1-a"), RequirementsTable.allIds(file.toFile()));
        assertEquals(Set.of("1.1", "2.1-a"), RequirementsTable.idsInCategory(file.toFile(), "backend"));
        assertEquals(Set.of("1.2"), RequirementsTable.idsInCategory(file.toFile(), "frontend"));
    }

    @Test
    void ignoresRowsWithTooFewColumns(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("requirements.md");
        Files.writeString(file, """
                | not a valid row without a category column |
                | 1.1 | backend |
                """);

        // The second line only has two columns (id, category) with none in between, so it
        // doesn't match "| id | ... | category |" either -- both are correctly ignored.
        assertEquals(Set.of(), RequirementsTable.allIds(file.toFile()));
    }

    @Test
    void doesNotReadALowerCaseHeaderRowAsARequirement(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("requirements.md");
        Files.writeString(file, """
                | id  | reference           | category |
                |-----|---------------------|----------|
                | 4.2 | Players join a room | backend  |
                """);

        // "| id | reference | category |" matches the row pattern as readily as a real row does;
        // it is the separator line underneath that marks it as the header.
        assertEquals(Set.of("4.2"), RequirementsTable.allIds(file.toFile()));
        assertEquals(Set.of("4.2"), RequirementsTable.idsInCategory(file.toFile(), "backend"));
    }
}
