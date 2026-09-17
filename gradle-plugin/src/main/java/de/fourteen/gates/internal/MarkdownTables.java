package de.fourteen.gates.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Picks the data rows out of a standard markdown table (header row, then a separator row of
 * dashes/colons, then data rows) without needing to know the header text -- which is project
 * vocabulary, not something this plugin should hardcode.
 */
public final class MarkdownTables {

    /** {@code Class}, {@code Class.method} or a package-qualified form of either. */
    private static final Pattern JAVA_NAME =
            Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*");

    private MarkdownTables() {
    }

    /**
     * Whether a first column can name a Java class or method at all. Data rows are collected from
     * every table in a file, since the convention fixes the row shape and not the heading above
     * it -- so an unrelated table in the same document used to turn into register entries, each
     * reported as a suppression that had been removed from the code. A row whose first column
     * couldn't name a class or method never referred to one.
     */
    public static boolean namesAJavaElement(String firstColumn) {
        return JAVA_NAME.matcher(firstColumn).matches();
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
