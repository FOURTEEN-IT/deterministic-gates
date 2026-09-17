package de.fourteen.gates.requirements;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * Configuration for the three requirements-register gates: {@code requirementsCoverage},
 * {@code taggedRequirementsCoverage} and {@code featureDocs}. They live in one plugin, not
 * three, because all three read the same {@link #getRequirementsFile()} through the same
 * convention (see the README) -- splitting them further would mean either duplicating that
 * parsing or introducing a fourth artifact just to share it.
 *
 * <p>Each gate still activates independently: only the gate(s) whose own required properties
 * are set attach to {@code check}.
 */
public abstract class RequirementsExtension {

    // Shared by all three gates below.
    public abstract RegularFileProperty getRequirementsFile();

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
}
