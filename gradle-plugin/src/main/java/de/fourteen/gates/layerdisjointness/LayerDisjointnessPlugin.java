package de.fourteen.gates.layerdisjointness;

import de.fourteen.gates.internal.Reports;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;

public class LayerDisjointnessPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("base");

        LayerDisjointnessExtension extension =
                project.getExtensions().create("layerDisjointness", LayerDisjointnessExtension.class);

        TaskProvider<LayerDisjointnessTask> task = project.getTasks().register(
                "layerDisjointness", LayerDisjointnessTask.class, t -> {
                    t.setGroup("verification");
                    t.setDescription("Checks that no domain line is covered only by an outer-layer test.");
                    t.getDomainPackagePrefix().set(extension.getDomainPackagePrefix());
                    t.getInnerCoverageReportXml().set(extension.getInnerCoverageReportXml());
                    t.getOuterCoverageReportXmls().from(extension.getOuterCoverageReportXmls());
                    t.getReportFile().convention(Reports.conventionFile(project, "layer-disjointness"));
                });

        task.configure(t -> t.onlyIf(unused ->
                extension.getDomainPackagePrefix().isPresent()
                        && extension.getInnerCoverageReportXml().isPresent()
                        && !extension.getOuterCoverageReportXmls().isEmpty()));
        project.getTasks().named("check").configure(check -> check.dependsOn(task));
    }
}
