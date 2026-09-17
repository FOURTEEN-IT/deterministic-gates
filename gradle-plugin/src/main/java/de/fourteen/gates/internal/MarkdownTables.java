package de.fourteen.gates.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * Picks the data rows out of a standard markdown table (header row, then a separator row of
 * dashes/colons, then data rows) without needing to know the header text -- which is project
 * vocabulary, not something this plugin should hardcode.
 */
public final class MarkdownTables {

    private MarkdownTables() {
    }

    public static List<String> dataRows(List<String> lines) {
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (!trimmed.startsWith("|") || isSeparatorRow(trimmed)) {
                continue;
            }
            boolean nextIsSeparator = i + 1 < lines.size() && isSeparatorRow(lines.get(i + 1).trim());
            if (nextIsSeparator) {
                continue; // this is the header row
            }
            rows.add(trimmed);
        }
        return rows;
    }

    public static String firstColumn(String row) {
        String trimmed = row.trim();
        if (trimmed.startsWith("|")) {
            trimmed = trimmed.substring(1);
        }
        int nextPipe = trimmed.indexOf('|');
        return (nextPipe >= 0 ? trimmed.substring(0, nextPipe) : trimmed).trim();
    }

    public static boolean isSeparatorRow(String row) {
        if (row.isEmpty()) {
            return false;
        }
        return row.chars().allMatch(c -> c == '|' || c == '-' || c == ':' || c == ' ');
    }
}
