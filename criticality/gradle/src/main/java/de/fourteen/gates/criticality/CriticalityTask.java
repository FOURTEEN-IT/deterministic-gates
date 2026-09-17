package de.fourteen.gates.criticality;

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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Convention: criticality is recorded at the production code it applies to, as an annotation
 * carrying a {@code level()} and the {@code requirements()} that justify it. This gate holds
 * that record to three things:
 *
 * <ul>
 *   <li>every requirement ID named exists in the requirements register -- an invented or
 *       mistyped ID is a justification nobody can look up;</li>
 *   <li>every annotation names at least one ID and a level the project allows;</li>
 *   <li>no level a build derives from is empty (see
 *       {@link CriticalityExtension#getLevelsThatMustNotBeEmpty()}).</li>
 * </ul>
 */
public abstract class CriticalityTask extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getRequirementsFile();

    @Input
    public abstract Property<String> getCriticalityAnnotationFqn();

    @Input
    public abstract ListProperty<String> getAllowedLevels();

    @Input
    public abstract ListProperty<String> getLevelsThatMustNotBeEmpty();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getMainClassesDirs();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getClasspath();

    @OutputFile
    public abstract RegularFileProperty getReportFile();

    @TaskAction
    public void check() {
        List<CriticalityScan.Finding> findings = CriticalityScan.scan(
                getMainClassesDirs().getFiles(),
                getClasspath().getFiles(),
                getCriticalityAnnotationFqn().get());

        Set<String> knownIds = RequirementsTable.allIds(getRequirementsFile().get().getAsFile());
        List<String> allowedLevels = getAllowedLevels().get();

        List<String> unknownIds = new ArrayList<>();
        List<String> withoutIds = new ArrayList<>();
        List<String> unknownLevels = new ArrayList<>();
        for (CriticalityScan.Finding finding : findings) {
            if (!allowedLevels.contains(finding.level())) {
                unknownLevels.add(finding.describe() + " is " + finding.level());
            }
            if (finding.requirements().isEmpty()) {
                withoutIds.add(finding.describe());
            }
            for (String id : finding.requirements()) {
                if (!knownIds.contains(id)) {
                    unknownIds.add(finding.describe() + " names '" + id + "'");
                }
            }
        }

        List<String> emptyLevels = new ArrayList<>();
        for (String level : getLevelsThatMustNotBeEmpty().get()) {
            if (CriticalityScan.classNamesAt(findings, level).isEmpty()) {
                emptyLevels.add(level);
            }
        }

        Reports.write(getReportFile().get().getAsFile(), report(findings, unknownIds, withoutIds,
                unknownLevels, emptyLevels));

        if (!unknownIds.isEmpty()) {
            throw new GradleException("Criticality names a requirement ID that isn't in the register: "
                    + unknownIds + " -- a justification pointing nowhere.");
        }
        if (!withoutIds.isEmpty()) {
            throw new GradleException("Criticality without a single requirement ID: " + withoutIds
                    + " -- a level is a claim about a requirement, name which one.");
        }
        if (!unknownLevels.isEmpty()) {
            throw new GradleException("Criticality level outside " + allowedLevels + ": " + unknownLevels);
        }
        if (!emptyLevels.isEmpty()) {
            throw new GradleException("No class or method is annotated " + emptyLevels
                    + " -- whatever the build derives from that level would run against nothing and "
                    + "report success. Either the annotation is gone or collecting it is broken.");
        }
    }

    private static String report(List<CriticalityScan.Finding> findings, List<String> unknownIds,
            List<String> withoutIds, List<String> unknownLevels, List<String> emptyLevels) {
        StringBuilder report = new StringBuilder();
        report.append("Criticality: ").append(findings.size()).append(" annotated element(s).\n");
        Set<String> levels = new TreeSet<>();
        findings.forEach(f -> levels.add(f.level()));
        for (String level : levels) {
            Set<String> classNames = new LinkedHashSet<>(CriticalityScan.classNamesAt(findings, level));
            report.append("  ").append(level).append(": ").append(classNames.size())
                    .append(" class(es) -- ").append(String.join(", ", classNames)).append("\n");
        }
        if (unknownIds.isEmpty() && withoutIds.isEmpty() && unknownLevels.isEmpty() && emptyLevels.isEmpty()) {
            report.append("Every level is populated and every requirement ID exists in the register.\n");
        }
        appendAll(report, "Unknown requirement ID", unknownIds);
        appendAll(report, "Criticality without a requirement ID", withoutIds);
        appendAll(report, "Level outside the allowed set", unknownLevels);
        appendAll(report, "Level with nothing annotated", emptyLevels);
        return report.toString();
    }

    private static void appendAll(StringBuilder report, String heading, List<String> entries) {
        if (entries.isEmpty()) {
            return;
        }
        report.append(heading).append(" (").append(entries.size()).append("):\n");
        entries.forEach(entry -> report.append("  - ").append(entry).append("\n"));
    }
}
