package de.fourteen.gates.suppressionregister;

import de.fourteen.gates.internal.Reports;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;

import java.util.List;

public class SuppressionRegisterPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("base");

        SuppressionRegisterExtension extension =
                project.getExtensions().create("suppressionRegister", SuppressionRegisterExtension.class);
        extension.getSuppressionAnnotationFqns().convention(List.of(
                "de.fourteen.gates.annotations.RegisteredSuppression",
                "org.junit.jupiter.api.Disabled"));

        TaskProvider<SuppressionRegisterTask> task = project.getTasks().register(
                "suppressionRegister", SuppressionRegisterTask.class, t -> {
                    t.setGroup("verification");
                    t.setDescription("Checks that every suppression annotation in the code has a "
                            + "matching register entry, and vice versa.");
                    t.getExceptionsRegisterFile().set(extension.getExceptionsRegisterFile());
                    t.getSuppressionAnnotationFqns().set(extension.getSuppressionAnnotationFqns());
                    t.getReportFile().convention(Reports.conventionFile(project, "suppression-register"));
                    project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {
                        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
                        t.getMainClassesDirs().from(sourceSets.getByName("main").getOutput().getClassesDirs());
                        t.getTestClassesDirs().from(sourceSets.getByName("test").getOutput().getClassesDirs());
                        t.getClasspath().from(sourceSets.getByName("test").getRuntimeClasspath());
                    });
                });

        // exceptionsRegisterFile has no convention -- its presence is what "configured" means.
        task.configure(t -> t.onlyIf(unused -> extension.getExceptionsRegisterFile().isPresent()));
        project.getTasks().named("check").configure(check -> check.dependsOn(task));
    }
}
