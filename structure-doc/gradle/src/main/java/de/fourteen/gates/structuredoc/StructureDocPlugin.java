package de.fourteen.gates.structuredoc;

import de.fourteen.gates.internal.Reports;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;

import java.util.List;

/**
 * Registers {@code structureDoc} and hangs it off {@code check} -- but only once
 * {@link StructureDocExtension#getDomainModelDir()} is actually configured, so applying this
 * plugin without configuring it is a no-op rather than a guaranteed build failure.
 */
public class StructureDocPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        // Guarantees a `check` task exists to hang the gate off, even in a project that applies
        // neither `java` nor `application`.
        project.getPluginManager().apply("base");

        StructureDocExtension extension = project.getExtensions().create("structureDoc", StructureDocExtension.class);
        extension.getArchitectureDocFile().convention(project.getLayout().getProjectDirectory().file("CLAUDE.md"));
        extension.getAllowedMissingNames().convention(List.of());

        TaskProvider<StructureDocTask> task = project.getTasks().register(
                "structureDoc", StructureDocTask.class, t -> {
                    t.setGroup("verification");
                    t.setDescription("Checks that the architecture doc names no files that don't "
                            + "exist and mentions every domain type.");
                    t.getArchitectureDocFile().set(extension.getArchitectureDocFile());
                    t.getDomainModelDir().set(extension.getDomainModelDir());
                    t.getAllowedMissingNames().set(extension.getAllowedMissingNames());
                    t.getReportFile().convention(Reports.conventionFile(project, "structure-doc"));
                });

        // domainModelDir has no convention -- its presence is what "configured" means here.
        task.configure(t -> t.onlyIf(unused -> extension.getDomainModelDir().isPresent()));
        project.getTasks().named("check").configure(check -> check.dependsOn(task));
    }
}
