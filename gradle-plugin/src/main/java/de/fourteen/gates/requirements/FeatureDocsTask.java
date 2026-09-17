package de.fourteen.gates.requirements;

import de.fourteen.gates.internal.MarkdownTables;
import de.fourteen.gates.internal.Reports;
import de.fourteen.gates.internal.RequirementsTable;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Convention: every {@code *.md} file directly under {@link #getFeaturesDir()} (except
 * {@code _template.md}, if present) is a feature doc that
 * <ul>
 *   <li>has every heading in {@link #getRequiredSections()};</li>
 *   <li>states exactly one criticality level, as a line {@code **<label>:** LEVEL} inside
 *       {@link #getCriticalitySectionName()};</li>
 *   <li>lists acceptance criteria as numbered lines under
 *       {@link #getAcceptanceCriteriaSectionName()}, no more than
 *       {@link #getMaxAcceptanceCriteria()};</li>
 *   <li>backs every referenced requirement ID inside {@link #getReferenceTableSectionName()}
 *       with a table row {@code | ID | reference-type | note |}, where each reference type is
 *       one of {@link #getAllowedReferenceTypes()} and, unless the type is "new", the ID exists
 *       in {@link #getRequirementsFile()}'s register; and</li>
 *   <li>carries no build-order table of its own under {@link #getBuildOrderSectionName()} --
 *       a doc with one describes more than one feature and should be sliced further.</li>
 * </ul>
 * A doc that fails any of these is more than one feature, or an out-of-date one.
 */
public abstract class FeatureDocsTask extends DefaultTask {

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getFeaturesDir();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getRequirementsFile();

    @Input
    public abstract ListProperty<String> getRequiredSections();

    @Input
    public abstract Property<String> getAcceptanceCriteriaSectionName();

    @Input
    public abstract Property<String> getReferenceTableSectionName();

    @Input
    public abstract Property<String> getCriticalitySectionName();

    @Input
    public abstract Property<String> getCriticalityLabel();

    @Input
    public abstract Property<String> getBuildOrderSectionName();

    @Input
    public abstract Property<Integer> getMaxAcceptanceCriteria();

    @Input
    public abstract ListProperty<String> getAllowedCriticalityLevels();

    @Input
    public abstract ListProperty<String> getAllowedReferenceTypes();

    @OutputFile
    public abstract RegularFileProperty getReportFile();

    @TaskAction
    public void check() {
        List<String> requiredSections = getRequiredSections().get();
        int maxCriteria = getMaxAcceptanceCriteria().get();
        Set<String> allowedLevels = Set.copyOf(getAllowedCriticalityLevels().get());
        Set<String> allowedReferenceTypes = Set.copyOf(getAllowedReferenceTypes().get());
        Set<String> knownIds = RequirementsTable.allIds(getRequirementsFile().get().getAsFile());

        Pattern criticalityLine = Pattern.compile(
                "^\\*\\*" + Pattern.quote(getCriticalityLabel().get()) + ":\\*\\*\\s+("
                        + String.join("|", allowedLevels) + ")\\s*$");

        File[] files = getFeaturesDir().get().getAsFile().listFiles(
                f -> f.isFile() && f.getName().endsWith(".md") && !f.getName().equals("_template.md"));
        List<File> docs = files == null ? List.of() : List.of(files).stream()
                .sorted(java.util.Comparator.comparing(File::getName)).toList();

        Map<String, List<String>> problems = new LinkedHashMap<>();
        for (File doc : docs) {
            List<String> lines = readLines(doc);
            List<String> issues = new ArrayList<>();

            Set<String> headings = new java.util.HashSet<>();
            for (String line : lines) {
                if (line.startsWith("## ")) {
                    headings.add(line.substring(3).trim());
                }
            }
            List<String> missingSections = requiredSections.stream().filter(s -> !headings.contains(s)).toList();
            if (!missingSections.isEmpty()) {
                issues.add("missing section(s): " + String.join(", ", missingSections));
            }

            List<String> levels = section(lines, getCriticalitySectionName().get()).stream()
                    .map(String::trim)
                    .map(criticalityLine::matcher)
                    .filter(java.util.regex.Matcher::matches)
                    .map(m -> m.group(1))
                    .toList();
            if (levels.isEmpty()) {
                issues.add("no \"**" + getCriticalityLabel().get() + ":** " + String.join("|", allowedLevels)
                        + "\" line in section \"" + getCriticalitySectionName().get() + "\"");
            } else if (levels.size() > 1) {
                issues.add(levels.size() + " criticality levels (" + String.join("/", levels)
                        + ") -- that's " + levels.size() + " features");
            }

            List<String> referenceRows = MarkdownTables.dataRows(section(lines, getReferenceTableSectionName().get()));
            if (referenceRows.isEmpty()) {
                issues.add("section \"" + getReferenceTableSectionName().get() + "\" has no reference table");
            }
            for (String row : referenceRows) {
                String[] columns = trimPipes(row).split("\\|", -1);
                if (columns.length < 2) {
                    issues.add("table row without a reference-type column: " + row);
                    continue;
                }
                String id = columns[0].trim();
                String referenceType = columns[1].trim();
                if (!allowedReferenceTypes.contains(referenceType)) {
                    issues.add("unknown reference type \"" + referenceType + "\" for " + id
                            + " (allowed: " + String.join(", ", allowedReferenceTypes) + ")");
                }
                if (!referenceType.equals("new") && !knownIds.contains(id)) {
                    issues.add(id + " (" + referenceType + ") is not in the requirements register");
                }
            }

            long criteriaCount = section(lines, getAcceptanceCriteriaSectionName().get()).stream()
                    .filter(l -> Pattern.compile("^[0-9]+\\.\\s").matcher(l).find())
                    .count();
            if (criteriaCount > maxCriteria) {
                issues.add(criteriaCount + " acceptance criteria (at most " + maxCriteria
                        + ") -- this slice is too wide, split it further");
            }

            List<String> buildOrderRows = MarkdownTables.dataRows(section(lines, getBuildOrderSectionName().get()));
            if (buildOrderRows.size() > 1) {
                issues.add("\"" + getBuildOrderSectionName().get() + "\" has " + buildOrderRows.size()
                        + " stages -- that's " + buildOrderRows.size() + " features");
            }

            if (!issues.isEmpty()) {
                problems.put(doc.getName(), issues);
            }
        }

        StringBuilder report = new StringBuilder();
        report.append("Feature docs: ").append(docs.size()).append(" checked.\n");
        if (problems.isEmpty()) {
            report.append("Every feature doc follows the template and describes exactly one feature.\n");
        } else {
            report.append("Flagged (").append(problems.size()).append("):\n");
            problems.forEach((name, issues) -> {
                report.append("  - ").append(name).append(":\n");
                issues.forEach(issue -> report.append("      ").append(issue).append("\n"));
            });
        }
        Reports.write(getReportFile().get().getAsFile(), report.toString());

        if (!problems.isEmpty()) {
            throw new GradleException("Feature doc(s) flagged: " + String.join(", ", problems.keySet()));
        }
    }

    private static List<String> section(List<String> lines, String heading) {
        int from = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().equals("## " + heading)) {
                from = i + 1;
                break;
            }
        }
        if (from < 0) {
            return List.of();
        }
        int to = lines.size();
        for (int i = from; i < lines.size(); i++) {
            if (lines.get(i).startsWith("## ")) {
                to = i;
                break;
            }
        }
        return lines.subList(from, to);
    }

    private static String trimPipes(String row) {
        String trimmed = row.trim();
        if (trimmed.startsWith("|")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("|")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static List<String> readLines(File file) {
        try {
            return Files.readAllLines(file.toPath());
        } catch (IOException e) {
            throw new RuntimeException("Could not read " + file, e);
        }
    }
}
