package de.fourteen.gates.internal;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the requirements register convention this plugin expects: a markdown table with rows
 * of the form {@code | id | ... | category |}, matched anywhere in the file regardless of
 * surrounding headings. See the README for the exact format and an example row.
 */
public final class RequirementsTable {

    private static final Pattern ROW = Pattern.compile(
            "^\\|\\s*([A-Za-z0-9][A-Za-z0-9.-]*)\\s*\\|.*\\|\\s*([a-z][a-z-]*)\\s*\\|\\s*$");

    private RequirementsTable() {
    }

    public record Row(String id, String category) {
    }

    public static List<Row> parse(File requirementsFile) {
        List<Row> rows = new ArrayList<>();
        for (String line : readLines(requirementsFile)) {
            Matcher matcher = ROW.matcher(line);
            if (matcher.matches()) {
                rows.add(new Row(matcher.group(1), matcher.group(2)));
            }
        }
        return rows;
    }

    public static LinkedHashSet<String> idsInCategory(File requirementsFile, String category) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (Row row : parse(requirementsFile)) {
            if (row.category().equals(category)) {
                ids.add(row.id());
            }
        }
        return ids;
    }

    public static LinkedHashSet<String> allIds(File requirementsFile) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (Row row : parse(requirementsFile)) {
            ids.add(row.id());
        }
        return ids;
    }

    private static List<String> readLines(File file) {
        try {
            return Files.readAllLines(file.toPath());
        } catch (IOException e) {
            throw new RuntimeException("Could not read requirements file: " + file, e);
        }
    }
}
