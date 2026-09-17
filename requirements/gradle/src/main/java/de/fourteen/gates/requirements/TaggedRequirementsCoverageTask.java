package de.fourteen.gates.requirements;

import de.fourteen.gates.internal.Reports;
import de.fourteen.gates.internal.RequirementsTable;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Convention: source files under {@link #getTaggedSourceDirs()} tag the requirement they
 * exercise by calling a function named {@link #getTagFunctionName()} with the ID as its first
 * argument, e.g. {@code requirement("4.2", ...)} or {@code requirement(["4.2", "4.3"], ...)}.
 * Unlike {@link RequirementsCoverageTask}, this doesn't gate on test results passing (there is
 * no single, language-agnostic way to read those) -- it only checks that every requirement in
 * {@link #getTaggedCoverageCategory()} is tagged somewhere, and flags tags that reference an ID
 * the register doesn't know.
 */
public abstract class TaggedRequirementsCoverageTask extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getRequirementsFile();

    @Input
    public abstract Property<String> getTaggedCoverageCategory();

    @Input
    public abstract Property<String> getTagFunctionName();

    @Input
    public abstract ListProperty<String> getTaggedSourceExtensions();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getTaggedSourceDirs();

    @OutputFile
    public abstract RegularFileProperty getReportFile();

    @TaskAction
    public void check() {
        File requirementsFile = getRequirementsFile().get().getAsFile();
        String category = getTaggedCoverageCategory().get();
        Set<String> requiredIds = RequirementsTable.idsInCategory(requirementsFile, category);
        Set<String> allIds = RequirementsTable.allIds(requirementsFile);
        Set<String> extensions = new LinkedHashSet<>(getTaggedSourceExtensions().get());

        Pattern call = Pattern.compile(
                Pattern.quote(getTagFunctionName().get()) + "\\(\\s*(\\[[^]]*]|\"[^\"]*\")");
        Pattern singleId = Pattern.compile("\"([^\"]+)\"");

        Set<String> tagged = new LinkedHashSet<>();
        for (File dir : getTaggedSourceDirs().getFiles()) {
            if (!dir.isDirectory()) {
                continue;
            }
            collectTags(dir, extensions, call, singleId, tagged);
        }

        List<String> untagged = requiredIds.stream().filter(id -> !tagged.contains(id)).sorted().toList();
        List<String> unknown = tagged.stream().filter(id -> !allIds.contains(id)).sorted().toList();

        StringBuilder report = new StringBuilder();
        report.append("Tagged requirements coverage (category \"").append(category).append("\"): ")
                .append(requiredIds.size() - untagged.size()).append(" of ")
                .append(requiredIds.size()).append(" requirement(s) tagged.\n");
        if (untagged.isEmpty() && unknown.isEmpty()) {
            report.append("Every requirement in this category is tagged.\n");
        }
        if (!untagged.isEmpty()) {
            report.append("Untagged (").append(untagged.size()).append("):\n");
            untagged.forEach(id -> report.append("  - ").append(id).append("\n"));
        }
        if (!unknown.isEmpty()) {
            report.append("Tagged IDs the register doesn't know (").append(unknown.size()).append("):\n");
            unknown.forEach(id -> report.append("  - ").append(id).append("\n"));
        }
        Reports.write(getReportFile().get().getAsFile(), report.toString());

        List<String> problems = new java.util.ArrayList<>();
        if (!untagged.isEmpty()) {
            problems.add("untagged requirements: " + untagged);
        }
        if (!unknown.isEmpty()) {
            problems.add("unknown tagged IDs: " + unknown);
        }
        if (!problems.isEmpty()) {
            throw new GradleException("Tagged requirements coverage failed -- " + String.join("; ", problems));
        }
    }

    private static void collectTags(File dir, Set<String> extensions, Pattern call, Pattern singleId, Set<String> tagged) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collectTags(child, extensions, call, singleId, tagged);
                continue;
            }
            String name = child.getName();
            int dot = name.lastIndexOf('.');
            if (dot < 0 || !extensions.contains(name.substring(dot + 1))) {
                continue;
            }
            String text = readFile(child);
            Matcher callMatcher = call.matcher(text);
            while (callMatcher.find()) {
                Matcher idMatcher = singleId.matcher(callMatcher.group(1));
                while (idMatcher.find()) {
                    tagged.add(idMatcher.group(1));
                }
            }
        }
    }

    private static String readFile(File file) {
        try {
            return Files.readString(file.toPath());
        } catch (IOException e) {
            throw new RuntimeException("Could not read " + file, e);
        }
    }
}
