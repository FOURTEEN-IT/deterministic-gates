package de.fourteen.gates.internal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void recognisesWhatCanAndCannotNameAJavaElement() {
        assertTrue(MarkdownTables.namesAJavaElement("PaymentGateway"));
        assertTrue(MarkdownTables.namesAJavaElement("PaymentGateway.retry"));
        assertTrue(MarkdownTables.namesAJavaElement("demo.domain.PaymentGateway.retry"));

        // Prose from an unrelated table in the same register file: collected as a data row,
        // since the convention fixes the row shape and not the heading above it, but it never
        // named a class, so it isn't a stale register entry either.
        assertFalse(MarkdownTables.namesAJavaElement("Test strategy"));
        assertFalse(MarkdownTables.namesAJavaElement("2026-03-01"));
        assertFalse(MarkdownTables.namesAJavaElement(""));
    }
}
