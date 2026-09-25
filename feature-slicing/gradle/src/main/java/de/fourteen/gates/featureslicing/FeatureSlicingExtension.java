package de.fourteen.gates.featureslicing;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * Configuration for the two slice-tree gates, {@code sliceStructure} and {@code sliceCoverage}.
 * Both read the same {@link #getFeaturesDir()} slice tree (see the {@code feature-slicing} skill
 * for the folder/status convention), so they share one extension the same way {@code
 * requirements}' three gates share {@code RequirementsExtension}.
 *
 * <p>Deliberately the same {@link #getFeaturesDir()} the {@code requirements} plugin's {@code
 * featureDocs} gate reads -- a slice tree is rooted at the same feature docs, not a separate
 * directory a project would have to configure twice.
 */
public abstract class FeatureSlicingExtension {

    // Shared by both gates.
    public abstract DirectoryProperty getFeaturesDir();

    // sliceStructure
    public abstract Property<String> getStatusSectionName();
    public abstract Property<String> getStatusLabel();
    public abstract Property<String> getNeedsSplittingStatus();
    public abstract ListProperty<String> getAllowedStatuses();

    // sliceStructure -- the vertical-slicing skill's "what can a user now do" statement. Its
    // *presence* is a structural fact this gate can check; whether the statement actually makes
    // sense (i.e. whether the slice really is vertical) stays a judgment call no gate makes here.
    public abstract Property<String> getUserOutcomeSectionName();

    // sliceStructure -- not a hard limit like featureDocs' maxAcceptanceCriteria: a slice past
    // this count still passes, but the report flags it as worth re-examining for unsplittability,
    // since neither gate can check unsplittability itself.
    public abstract Property<String> getAcceptanceCriteriaSectionName();
    public abstract Property<Integer> getUnsplittabilityReviewThreshold();

    // sliceCoverage -- reuses the same marker annotation requirementsCoverage does by default,
    // since a leaf slice's ID is claimed the exact same way a requirement's ID is.
    public abstract Property<String> getRequirementAnnotationFqn();
    public abstract ConfigurableFileCollection getTestClassesDirs();
    public abstract ConfigurableFileCollection getTestResultsDirs();
    public abstract ConfigurableFileCollection getTestRuntimeClasspath();
}
