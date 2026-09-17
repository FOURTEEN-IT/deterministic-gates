package de.fourteen.gates.internal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarkdownTablesTest {

    @Test
    void skipsHeaderAndSeparatorRegardlessOfHeaderText() {
        List<String> lines = List.of(
                "Some prose before the table.",
                "| Whatever Header Text | Reason | Date |",
                "|---|---|---|",
                "| Foo.bar | some reason | 2026-01-01 |",
                "| Baz | another reason | 2026-01-02 |",
                "Some prose after.");

        List<String> rows = MarkdownTables.dataRows(lines);

        assertEquals(List.of(
                "| Foo.bar | some reason | 2026-01-01 |",
                "| Baz | another reason | 2026-01-02 |"), rows);
    }

    @Test
    void firstColumnStripsPipesAndWhitespace() {
        assertEquals("Foo.bar", MarkdownTables.firstColumn("| Foo.bar | reason |"));
        assertEquals("Foo.bar", MarkdownTables.firstColumn("|Foo.bar|reason|"));
    }

    @Test
    void emptyTableProducesNoRows() {
        assertEquals(List.of(), MarkdownTables.dataRows(List.of("no table here at all")));
    }
}
