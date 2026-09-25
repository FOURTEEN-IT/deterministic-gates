package de.fourteen.gates.featureslicing;

import de.fourteen.gates.internal.Reports;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;

import java.util.List;

/**
 * Registers {@code sliceStructure} and {@code sliceCoverage}. Each attaches to {@code check}
 * independently, only once its own required properties are set -- applying this plugin and
 * configuring only {@code featuresDir} runs {@code sliceStructure} alone, the same way
 * configuring only {@code featuresDir} on the {@code requirements} plugin runs {@code
 * featureDocs} alone.
 */
public class FeatureSlicingPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("base");

        FeatureSlicingExtension extension = project.getExtensions().create(
                "featureSlicing", FeatureSlicingExtension.class);
        applyConventions(project, extension);

        TaskProvider<SliceStructureTask> sliceStructure = project.getTasks().register(
                "sliceStructure", SliceStructureTask.class, task -> {
                    task.setGroup("verification");
                    task.setDescription("Checks that every slice's numbering and status "
                            + "agree with the feature-slicing folder structure.");
                    task.getFeaturesDir().set(extension.getFeaturesDir());
                    task.getStatusSectionName().set(extension.getStatusSectionName());
                    task.getStatusLabel().set(extension.getStatusLabel());
                    task.getNeedsSplittingStatus().set(extension.getNeedsSplittingStatus());
                    task.getAllowedStatuses().set(extension.getAllowedStatuses());
                    task.getUserOutcomeSectionName().set(extension.getUserOutcomeSectionName());
                    task.getAcceptanceCriteriaSectionName().set(extension.getAcceptanceCriteriaSectionName());
                    task.getUnsplittabilityReviewThreshold().set(extension.getUnsplittabilityReviewThreshold());
                    task.getReportFile().convention(Reports.conventionFile(project, "slice-structure"));
                });
        sliceStructure.configure(t -> t.onlyIf(unused -> extension.getFeaturesDir().isPresent()));

        TaskProvider<SliceCoverageTask> sliceCoverage = project.getTasks().register(
                "sliceCoverage", SliceCoverageTask.class, task -> {
                    task.setGroup("verification");
                    task.setDescription("Compares leaf slices against green, "
                            + "requirement-annotated test methods.");
                    task.getFeaturesDir().set(extension.getFeaturesDir());
                    task.getRequirementAnnotationFqn().set(extension.getRequirementAnnotationFqn());
                    task.getTestClassesDirs().from(extension.getTestClassesDirs());
                    task.getTestResultsDirs().from(extension.getTestResultsDirs());
                    task.getTestRuntimeClasspath().from(extension.getTestRuntimeClasspath());
                    task.getReportFile().convention(Reports.conventionFile(project, "slice-coverage"));
                });
        // featuresDir alone isn't enough to mean "run this gate": sliceStructure needs only the
        // folder tree, but sliceCoverage additionally needs test classes -- same reasoning as
        // requirementsCoverage's own activation check.
        sliceCoverage.configure(t -> t.onlyIf(unused ->
                extension.getFeaturesDir().isPresent() && !extension.getTestClassesDirs().isEmpty()));

        project.getTasks().named("check").configure(check -> check.dependsOn(sliceStructure, sliceCoverage));
    }

    private void applyConventions(Project project, FeatureSlicingExtension extension) {
        extension.getStatusSectionName().convention("Status");
        extension.getStatusLabel().convention("Status");
        extension.getNeedsSplittingStatus().convention("needs splitting");
        extension.getAllowedStatuses().convention(List.of("needs splitting", "ready for implementation"));
        extension.getUserOutcomeSectionName().convention("User Outcome");
        extension.getAcceptanceCriteriaSectionName().convention("Acceptance Criteria");
        extension.getUnsplittabilityReviewThreshold().convention(12);
        extension.getRequirementAnnotationFqn().convention("de.fourteen.gates.annotations.Requirement");

        project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {
            SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
            SourceSet testSourceSet = sourceSets.getByName("test");
            extension.getTestClassesDirs().from(testSourceSet.getOutput().getClassesDirs());
            extension.getTestRuntimeClasspath().from(testSourceSet.getRuntimeClasspath());
            extension.getTestResultsDirs().from(
                    project.getTasks().named(testSourceSet.getName(), Test.class)
                            .map(test -> test.getReports().getJunitXml().getOutputLocation().get()));
        });
    }
}
