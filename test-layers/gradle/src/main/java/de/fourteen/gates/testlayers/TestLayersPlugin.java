package de.fourteen.gates.testlayers;

import de.fourteen.gates.internal.Reports;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;

/**
 * Registers the {@code testLayers} gate. Attaches to {@code check} once {@code layers} is set --
 * applying this plugin and naming no layers is a no-op, like every other gate here.
 */
public class TestLayersPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("base");

        TestLayersExtension extension = project.getExtensions().create("testLayers", TestLayersExtension.class);
        extension.getTagAnnotationFqns().convention(TestLayersExtension.DEFAULT_TAG_ANNOTATIONS);
        extension.getTestMethodAnnotationFqns().convention(TestLayersExtension.DEFAULT_TEST_METHOD_ANNOTATIONS);
        project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {
            SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
            SourceSet testSourceSet = sourceSets.getByName("test");
            extension.getTestClassesDirs().from(testSourceSet.getOutput().getClassesDirs());
            extension.getClasspath().from(testSourceSet.getRuntimeClasspath());
        });

        TaskProvider<TestLayersTask> task = project.getTasks().register(
                "testLayers", TestLayersTask.class, t -> {
                    t.setGroup("verification");
                    t.setDescription("Checks that every test method belongs to exactly one layer.");
                    t.getLayers().set(extension.getLayers());
                    t.getTagAnnotationFqns().set(extension.getTagAnnotationFqns());
                    t.getTestMethodAnnotationFqns().set(extension.getTestMethodAnnotationFqns());
                    t.getTestClassesDirs().from(extension.getTestClassesDirs());
                    t.getClasspath().from(extension.getClasspath());
                    t.getReportFile().convention(Reports.conventionFile(project, "test-layers"));
                });

        task.configure(t -> t.onlyIf(unused -> !extension.getLayers().get().isEmpty()));
        project.getTasks().named("check").configure(check -> check.dependsOn(task));
    }
}
