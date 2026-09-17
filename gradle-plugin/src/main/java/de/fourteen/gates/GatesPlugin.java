package de.fourteen.gates;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;

import java.util.List;

public class GatesPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        // Guarantees a `check` task exists to hang the gates off, even in a project that
        // applies neither `java` nor `application`.
        project.getPluginManager().apply("base");

        GatesExtension extension = project.getExtensions().create("gates", GatesExtension.class);
        applyConventions(project, extension);

        TaskProvider<StructureDocTask> structureDoc = project.getTasks().register(
                "structureDoc", StructureDocTask.class, task -> {
                    task.setGroup("verification");
                    task.setDescription("Checks that the architecture doc names no files that don't "
                            + "exist and mentions every domain type.");
                    task.getArchitectureDocFile().set(extension.getArchitectureDocFile());
                    task.getDomainModelDir().set(extension.getDomainModelDir());
                    task.getStructureDocAllowedMissingNames().set(extension.getStructureDocAllowedMissingNames());
                    task.getReportFile().convention(reportFile(project, "structure-doc"));
                });

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
                    task.getReportFile().convention(reportFile(project, "requirements-coverage"));
                });

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
                    task.getReportFile().convention(reportFile(project, "tagged-requirements-coverage"));
                    task.onlyIf(t -> !extension.getTaggedSourceDirs().isEmpty());
                });

        TaskProvider<LayerDisjointnessTask> layerDisjointness = project.getTasks().register(
                "layerDisjointness", LayerDisjointnessTask.class, task -> {
                    task.setGroup("verification");
                    task.setDescription("Checks that no domain line is covered only by an outer-layer test.");
                    task.getDomainPackagePrefix().set(extension.getDomainPackagePrefix());
                    task.getInnerCoverageReportXml().set(extension.getInnerCoverageReportXml());
                    task.getOuterCoverageReportXmls().from(extension.getOuterCoverageReportXmls());
                    task.getReportFile().convention(reportFile(project, "layer-disjointness"));
                    task.onlyIf(t -> !extension.getOuterCoverageReportXmls().isEmpty());
                });

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
                    task.getReportFile().convention(reportFile(project, "feature-docs"));
                });

        TaskProvider<SuppressionRegisterTask> suppressionRegister = project.getTasks().register(
                "suppressionRegister", SuppressionRegisterTask.class, task -> {
                    task.setGroup("verification");
                    task.setDescription("Checks that every suppression annotation in the code has a "
                            + "matching register entry, and vice versa.");
                    task.getExceptionsRegisterFile().set(extension.getExceptionsRegisterFile());
                    task.getSuppressionAnnotationFqns().set(extension.getSuppressionAnnotationFqns());
                    task.getReportFile().convention(reportFile(project, "suppression-register"));
                    project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {
                        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
                        task.getMainClassesDirs().from(sourceSets.getByName("main").getOutput().getClassesDirs());
                        task.getTestClassesDirs().from(sourceSets.getByName("test").getOutput().getClassesDirs());
                        task.getClasspath().from(sourceSets.getByName("test").getRuntimeClasspath());
                    });
                });

        project.getTasks().register("installGitHooks", InstallGitHooksTask.class, task -> {
            task.setGroup("verification");
            task.setDescription("Copies the bundled commit-msg hook into .git/hooks.");
        });

        project.getTasks().named("check").configure(check -> check.dependsOn(
                structureDoc, requirementsCoverage, taggedRequirementsCoverage,
                layerDisjointness, featureDocs, suppressionRegister));
    }

    private void applyConventions(Project project, GatesExtension extension) {
        extension.getArchitectureDocFile().convention(project.getLayout().getProjectDirectory().file("CLAUDE.md"));
        extension.getStructureDocAllowedMissingNames().convention(List.of());

        extension.getCoverageCategory().convention("backend");

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

        // Shipped by this plugin's `annotations` module -- add it as a testImplementation
        // dependency to use these gates without configuring anything here. Both properties stay
        // overridable for a project that already has an equivalent annotation of its own.
        extension.getRequirementAnnotationFqn().convention("de.fourteen.gates.annotations.Requirement");
        extension.getSuppressionAnnotationFqns().convention(List.of(
                "de.fourteen.gates.annotations.RegisteredSuppression",
                "org.junit.jupiter.api.Disabled"));

        project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {
            SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
            SourceSet testSourceSet = sourceSets.getByName("test");
            extension.getTestClassesDirs().from(testSourceSet.getOutput().getClassesDirs());
            extension.getTestRuntimeClasspath().from(testSourceSet.getRuntimeClasspath());
            extension.getTestResultsDirs().from(project.getLayout().getBuildDirectory().dir("test-results/test"));
        });
    }

    private static org.gradle.api.provider.Provider<org.gradle.api.file.RegularFile> reportFile(
            Project project, String name) {
        return project.getLayout().getBuildDirectory().file("reports/gates/" + name + ".txt");
    }
}
