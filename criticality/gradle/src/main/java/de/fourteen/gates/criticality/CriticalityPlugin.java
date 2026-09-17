package de.fourteen.gates.criticality;

import de.fourteen.gates.internal.Reports;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;

import java.util.List;

/**
 * Registers the {@code criticality} gate and the {@code criticality} extension. The extension
 * is useful on its own -- {@link CriticalityExtension#classesAt(String)} needs no requirements
 * register -- but the gate only attaches to {@code check} once {@code requirementsFile} is set,
 * like every other gate here.
 */
public class CriticalityPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("base");

        CriticalityExtension extension =
                project.getExtensions().create("criticality", CriticalityExtension.class);
        extension.getCriticalityAnnotationFqn().convention("de.fourteen.gates.annotations.Criticality");
        extension.getAllowedLevels().convention(List.of("LOW", "MEDIUM", "HIGH"));
        extension.getLevelsThatMustNotBeEmpty().convention(List.of("HIGH"));
        project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {
            SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
            extension.getMainClassesDirs().from(sourceSets.getByName("main").getOutput().getClassesDirs());
            extension.getClasspath().from(sourceSets.getByName("main").getRuntimeClasspath());
        });

        TaskProvider<CriticalityTask> task = project.getTasks().register(
                "criticality", CriticalityTask.class, t -> {
                    t.setGroup("verification");
                    t.setDescription("Checks that every criticality annotation names real "
                            + "requirement IDs and that no derived level is empty.");
                    t.getRequirementsFile().set(extension.getRequirementsFile());
                    t.getCriticalityAnnotationFqn().set(extension.getCriticalityAnnotationFqn());
                    t.getAllowedLevels().set(extension.getAllowedLevels());
                    t.getLevelsThatMustNotBeEmpty().set(extension.getLevelsThatMustNotBeEmpty());
                    t.getMainClassesDirs().from(extension.getMainClassesDirs());
                    t.getClasspath().from(extension.getClasspath());
                    t.getReportFile().convention(Reports.conventionFile(project, "criticality"));
                });

        // requirementsFile has no convention -- its presence is what "configured" means.
        task.configure(t -> t.onlyIf(unused -> extension.getRequirementsFile().isPresent()));
        project.getTasks().named("check").configure(check -> check.dependsOn(task));
    }
}
