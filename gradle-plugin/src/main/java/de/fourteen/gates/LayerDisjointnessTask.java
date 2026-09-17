package de.fourteen.gates;

import de.fourteen.gates.internal.JacocoReport;
import de.fourteen.gates.internal.Reports;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A domain line covered only by an outer-layer test (adapter, API, end-to-end) and by no
 * inner-layer test (unit/domain, or a port-level test one ring out) is a gap further in, not a
 * credit to the outer layer -- that inner ring should have its own test reaching that line.
 * This gate diffs two JaCoCo XML reports restricted to {@link #getDomainPackagePrefix()} and
 * fails if the outer report covers anything the inner one doesn't.
 */
public abstract class LayerDisjointnessTask extends DefaultTask {

    @Input
    public abstract Property<String> getDomainPackagePrefix();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getInnerCoverageReportXml();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getOuterCoverageReportXmls();

    @OutputFile
    public abstract RegularFileProperty getReportFile();

    @TaskAction
    public void check() {
        String prefix = getDomainPackagePrefix().get();
        Set<String> inner = JacocoReport.coveredLines(getInnerCoverageReportXml().get().getAsFile(), prefix);

        Set<String> outer = new LinkedHashSet<>();
        for (File outerReport : getOuterCoverageReportXmls().getFiles()) {
            outer.addAll(JacocoReport.coveredLines(outerReport, prefix));
        }

        List<String> gaps = outer.stream().filter(line -> !inner.contains(line)).sorted().toList();

        StringBuilder report = new StringBuilder();
        report.append("Layer disjointness: ").append(gaps.size())
                .append(" domain line(s) covered only by an outer-layer test.\n");
        gaps.forEach(line -> report.append("  - ").append(line).append("\n"));
        Reports.write(getReportFile().get().getAsFile(), report.toString());

        if (!gaps.isEmpty()) {
            throw new GradleException("Layer disjointness violated: " + gaps.size()
                    + " domain line(s) covered only by an outer-layer test -- gap further in: " + gaps);
        }
    }
}
