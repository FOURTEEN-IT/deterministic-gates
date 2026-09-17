package de.fourteen.gates;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * Configuration for the deterministic gates.
 *
 * <p>Each gate checks against a fixed file convention that is documented in the README, not
 * against something configurable here -- adopting a gate means adopting that convention.
 * These properties only say <em>where</em> a project keeps the file or directory the
 * convention lives in, and the handful of names (annotation class, package prefix) that are
 * inevitably project-specific.
 */
public abstract class GatesExtension {

    // Shared by requirementsCoverage, taggedRequirementsCoverage and featureDocs: the single
    // register of requirement IDs a project checks its tests and feature docs against.
    public abstract RegularFileProperty getRequirementsFile();

    // structureDoc
    public abstract RegularFileProperty getArchitectureDocFile();
    public abstract DirectoryProperty getDomainModelDir();
    public abstract ListProperty<String> getStructureDocAllowedMissingNames();

    // requirementsCoverage -- the marker annotation is shipped by this plugin's `annotations`
    // module (de.fourteen.gates.annotations.Requirement) and used by default; override only if
    // a project already has its own equivalent and doesn't want the extra dependency.
    public abstract Property<String> getRequirementAnnotationFqn();
    public abstract Property<String> getCoverageCategory();
    public abstract ConfigurableFileCollection getTestClassesDirs();
    public abstract ConfigurableFileCollection getTestResultsDirs();
    public abstract ConfigurableFileCollection getTestRuntimeClasspath();

    // taggedRequirementsCoverage
    public abstract ConfigurableFileCollection getTaggedSourceDirs();
    public abstract ListProperty<String> getTaggedSourceExtensions();
    public abstract Property<String> getTagFunctionName();
    public abstract Property<String> getTaggedCoverageCategory();

    // layerDisjointness
    public abstract Property<String> getDomainPackagePrefix();
    public abstract RegularFileProperty getInnerCoverageReportXml();
    public abstract ConfigurableFileCollection getOuterCoverageReportXmls();

    // featureDocs -- required section headings, table labels and the criticality label are
    // project vocabulary (like an annotation's FQN), not structural format, so they stay
    // configurable; the structure itself (one heading per requirement, a reference table with
    // ID/reference/note columns, a single criticality line) does not.
    public abstract DirectoryProperty getFeaturesDir();
    public abstract ListProperty<String> getRequiredSections();
    public abstract Property<String> getAcceptanceCriteriaSectionName();
    public abstract Property<String> getReferenceTableSectionName();
    public abstract Property<String> getCriticalitySectionName();
    public abstract Property<String> getCriticalityLabel();
    public abstract Property<String> getBuildOrderSectionName();
    public abstract Property<Integer> getMaxAcceptanceCriteria();
    public abstract ListProperty<String> getAllowedCriticalityLevels();
    public abstract ListProperty<String> getAllowedReferenceTypes();

    // suppressionRegister -- defaults to this plugin's own
    // de.fourteen.gates.annotations.RegisteredSuppression plus JUnit 5's @Disabled (both
    // already on most JVM test classpaths, the latter without any extra dependency at all);
    // add to the list rather than replacing it if a project has further suppression annotations
    // of its own.
    public abstract RegularFileProperty getExceptionsRegisterFile();
    public abstract ListProperty<String> getSuppressionAnnotationFqns();
}
