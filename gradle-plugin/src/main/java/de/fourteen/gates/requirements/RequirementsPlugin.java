package de.fourteen.gates.requirements;

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
 * Registers {@code requirementsCoverage}, {@code taggedRequirementsCoverage} and
 * {@code featureDocs}. Each attaches to {@code check} independently, only once its own required
 * properties are set -- applying this plugin and configuring only {@code featuresDir}, say,
 * runs {@code featureDocs} alone.
 */
public class RequirementsPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("base");

        RequirementsExtension extension = project.getExtensions().create("requirements", RequirementsExtension.class);
        applyConventions(project, extension);

        TaskProvider<RequirementsCoverageTask> requirementsCoverage = project.getTasks().register(
                "requirementsCoverage", RequirementsCoverageTask.class, task -> {
                    task.setGroup("verification");
                    task.setDescription("Compares the requirements register against green, "
                            + "requirement-annotated test methods.");
                    task.getRequirementsFile().set(extension.getRequirementsFile());
                    task.getCoverageCategory().set(extension.getCoverageCategory());
                    task.getRequirementAnnotationFqn().set(extension.getRequirementAnnotationFqn());
                    task.getTestClassesDirs().from(extension.getTestClassesDirs());
                    task.getTestResultsDirs().from(extension.getTestResultsDirs());
                    task.getTestRuntimeClasspath().from(extension.getTestRuntimeClasspath());
                    task.getReportFile().convention(Reports.conventionFile(project, "requirements-coverage"));
                });
        // requirementsFile alone is not enough to mean "run this gate": all three gates read it,
        // so keying off it made requirementsCoverage activate in a project that configured only
        // featuresDir -- and then fail looking for a marker annotation that project never had a
        // reason to put on its classpath. Test classes are what this gate actually needs.
        requirementsCoverage.configure(t -> t.onlyIf(unused ->
                extension.getRequirementsFile().isPresent() && !extension.getTestClassesDirs().isEmpty()));

        TaskProvider<TaggedRequirementsCoverageTask> taggedRequirementsCoverage = project.getTasks().register(
                "taggedRequirementsCoverage", TaggedRequirementsCoverageTask.class, task -> {
                    task.setGroup("verification");
                    task.setDescription("Compares the requirements register against source-tagged "
                            + "requirement IDs.");
                    task.getRequirementsFile().set(extension.getRequirementsFile());
                    task.getTaggedCoverageCategory().set(extension.getTaggedCoverageCategory());
                    task.getTagFunctionName().set(extension.getTagFunctionName());
                    task.getTaggedSourceExtensions().set(extension.getTaggedSourceExtensions());
                    task.getTaggedSourceDirs().from(extension.getTaggedSourceDirs());
                    task.getReportFile().convention(Reports.conventionFile(project, "tagged-requirements-coverage"));
                });
        taggedRequirementsCoverage.configure(t -> t.onlyIf(unused ->
                extension.getRequirementsFile().isPresent() && !extension.getTaggedSourceDirs().isEmpty()));

        TaskProvider<FeatureDocsTask> featureDocs = project.getTasks().register(
                "featureDocs", FeatureDocsTask.class, task -> {
                    task.setGroup("verification");
                    task.setDescription("Checks that every feature doc follows the template and "
                            + "describes exactly one feature.");
                    task.getFeaturesDir().set(extension.getFeaturesDir());
                    task.getRequirementsFile().set(extension.getRequirementsFile());
                    task.getRequiredSections().set(extension.getRequiredSections());
                    task.getAcceptanceCriteriaSectionName().set(extension.getAcceptanceCriteriaSectionName());
                    task.getReferenceTableSectionName().set(extension.getReferenceTableSectionName());
                    task.getCriticalitySectionName().set(extension.getCriticalitySectionName());
                    task.getCriticalityLabel().set(extension.getCriticalityLabel());
                    task.getBuildOrderSectionName().set(extension.getBuildOrderSectionName());
                    task.getMaxAcceptanceCriteria().set(extension.getMaxAcceptanceCriteria());
                    task.getAllowedCriticalityLevels().set(extension.getAllowedCriticalityLevels());
                    task.getAllowedReferenceTypes().set(extension.getAllowedReferenceTypes());
                    task.getReportFile().convention(Reports.conventionFile(project, "feature-docs"));
                });
        featureDocs.configure(t -> t.onlyIf(unused ->
                extension.getRequirementsFile().isPresent() && extension.getFeaturesDir().isPresent()));

        project.getTasks().named("check").configure(check -> check.dependsOn(
                requirementsCoverage, taggedRequirementsCoverage, featureDocs));
    }

    private void applyConventions(Project project, RequirementsExtension extension) {
        extension.getCoverageCategory().convention("backend");
        extension.getRequirementAnnotationFqn().convention("de.fourteen.gates.annotations.Requirement");

        extension.getTaggedCoverageCategory().convention("frontend");
        extension.getTagFunctionName().convention("requirement");
        extension.getTaggedSourceExtensions().convention(List.of("js", "jsx"));

        extension.getRequiredSections().convention(List.of(
                "Motivation", "Affected Requirements", "Acceptance Criteria",
                "Scenarios", "Criticality", "Implemented In", "Open Questions"));
        extension.getAcceptanceCriteriaSectionName().convention("Acceptance Criteria");
        extension.getReferenceTableSectionName().convention("Affected Requirements");
        extension.getCriticalitySectionName().convention("Criticality");
        extension.getCriticalityLabel().convention("Level");
        extension.getBuildOrderSectionName().convention("Build Order");
        extension.getMaxAcceptanceCriteria().convention(12);
        extension.getAllowedCriticalityLevels().convention(List.of("LOW", "MEDIUM", "HIGH"));
        extension.getAllowedReferenceTypes().convention(List.of("existing", "changed", "new", "reverted"));

        project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {
            SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
            SourceSet testSourceSet = sourceSets.getByName("test");
            extension.getTestClassesDirs().from(testSourceSet.getOutput().getClassesDirs());
            extension.getTestRuntimeClasspath().from(testSourceSet.getRuntimeClasspath());
            // Taken from the test task's own report location rather than the equivalent literal
            // path: a literal carries no task dependency, so `check` was free to run
            // requirementsCoverage *before* test and judge the register against results that
            // weren't written yet (or, without a clean, against the previous run's). Deriving it
            // from the task makes Gradle order the two.
            extension.getTestResultsDirs().from(
                    project.getTasks().named(testSourceSet.getName(), Test.class)
                            .map(test -> test.getReports().getJunitXml().getOutputLocation().get()));
        });
    }
}
