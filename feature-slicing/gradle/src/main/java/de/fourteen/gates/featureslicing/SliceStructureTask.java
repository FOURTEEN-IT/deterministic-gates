package de.fourteen.gates.featureslicing;

import de.fourteen.gates.internal.Reports;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Convention (see the {@code feature-slicing} skill): every slice doc under a top-level feature
 * doc's same-named directory
 * <ul>
 *   <li>is numbered contiguously from 1 among its siblings, with no gaps or duplicates
 *       (checked by {@link SliceTree});</li>
 *   <li>states its status as a {@code **<label>:** <value>} line inside
 *       {@link #getStatusSectionName()}, exactly one of {@link #getAllowedStatuses()}; and</li>
 *   <li>has that status agree with what's actually on disk: {@link #getNeedsSplittingStatus()}
 *       requires at least one child slice underneath it, any other status requires none -- a
 *       slice can't claim to be ready for implementation while still having open children, or
 *       claim to need further splitting while nothing has been split yet.</li>
 * </ul>
 * The top-level feature doc itself carries no status (that's {@code featureDocs}' job); only the
 * slices underneath it do.
 */
public abstract class SliceStructureTask extends DefaultTask {

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getFeaturesDir();

    @Input
    public abstract Property<String> getStatusSectionName();

    @Input
    public abstract Property<String> getStatusLabel();

    @Input
    public abstract Property<String> getNeedsSplittingStatus();

    @Input
    public abstract ListProperty<String> getAllowedStatuses();

    @OutputFile
    public abstract RegularFileProperty getReportFile();

    @TaskAction
    public void check() {
        List<String> allowedStatuses = getAllowedStatuses().get();
        String needsSplitting = getNeedsSplittingStatus().get();
        Pattern statusLine = Pattern.compile("^\\*\\*" + Pattern.quote(getStatusLabel().get()) + ":\\*\\*\\s+("
                + String.join("|", allowedStatuses.stream().map(Pattern::quote).toList()) + ")\\s*$");

        Map<String, List<String>> problems = new LinkedHashMap<>();
        int sliceCount = 0;
        for (SliceTree.Node root : SliceTree.forest(getFeaturesDir().get().getAsFile())) {
            if (!root.issues().isEmpty()) {
                problems.put(root.id(), root.issues());
            }
            for (SliceTree.Node child : root.children()) {
                sliceCount += 1 + countDescendants(child);
                checkNode(child, statusLine, allowedStatuses, needsSplitting, problems);
            }
        }

        StringBuilder report = new StringBuilder();
        report.append("Slices: ").append(sliceCount).append(" checked.\n");
        if (problems.isEmpty()) {
            report.append("Every slice's numbering and status agree with the folder structure.\n");
        } else {
            report.append("Flagged (").append(problems.size()).append("):\n");
            problems.forEach((id, issues) -> {
                report.append("  - ").append(id).append(":\n");
                issues.forEach(issue -> report.append("      ").append(issue).append("\n"));
            });
        }
        Reports.write(getReportFile().get().getAsFile(), report.toString());

        if (!problems.isEmpty()) {
            throw new GradleException("Slice(s) flagged: " + String.join(", ", problems.keySet()));
        }
    }

    private void checkNode(SliceTree.Node node, Pattern statusLine, List<String> allowedStatuses,
            String needsSplitting, Map<String, List<String>> problems) {
        List<String> issues = new ArrayList<>(node.issues());
        List<String> lines = readLines(node.doc());

        List<String> statusValues = section(lines, getStatusSectionName().get()).stream()
                .map(String::trim)
                .map(statusLine::matcher)
                .filter(Matcher::matches)
                .map(m -> m.group(1))
                .toList();
        if (statusValues.isEmpty()) {
            issues.add("no \"**" + getStatusLabel().get() + ":** " + String.join("|", allowedStatuses)
                    + "\" line in section \"" + getStatusSectionName().get() + "\"");
        } else if (statusValues.size() > 1) {
            issues.add(statusValues.size() + " status lines (" + String.join("/", statusValues) + ")");
        } else {
            String status = statusValues.get(0);
            boolean hasChildren = !node.children().isEmpty();
            if (status.equals(needsSplitting) && !hasChildren) {
                issues.add("status is \"" + needsSplitting + "\" but no child slices exist under "
                        + node.childDir().getName() + "/");
            } else if (!status.equals(needsSplitting) && hasChildren) {
                issues.add("status is \"" + status + "\" but child slices already exist under "
                        + node.childDir().getName() + "/ -- should be \"" + needsSplitting + "\"");
            }
        }

        if (!issues.isEmpty()) {
            problems.put(node.id(), issues);
        }
        for (SliceTree.Node child : node.children()) {
            checkNode(child, statusLine, allowedStatuses, needsSplitting, problems);
        }
    }

    private static int countDescendants(SliceTree.Node node) {
        int count = 0;
        for (SliceTree.Node child : node.children()) {
            count += 1 + countDescendants(child);
        }
        return count;
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

    private static List<String> readLines(File file) {
        try {
            return Files.readAllLines(file.toPath());
        } catch (IOException e) {
            throw new RuntimeException("Could not read " + file, e);
        }
    }
}
