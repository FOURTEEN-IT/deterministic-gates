package de.fourteen.gates.criticality;

import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

/** Configuration for the {@code criticality} gate. */
public abstract class CriticalityExtension {

    /** The requirements register the annotation's IDs are checked against. */
    public abstract RegularFileProperty getRequirementsFile();

    public abstract Property<String> getCriticalityAnnotationFqn();

    public abstract ListProperty<String> getAllowedLevels();

    /**
     * Levels that must have at least one annotated class or method. An empty set at the level a
     * build derives work from -- mutation testing, a stricter review, an extra test task -- is
     * the most dangerous outcome there is: that work then runs against nothing and reports
     * success, indistinguishable from a real run.
     */
    public abstract ListProperty<String> getLevelsThatMustNotBeEmpty();

    public abstract ConfigurableFileCollection getMainClassesDirs();

    public abstract ConfigurableFileCollection getClasspath();

    /**
     * The classes annotated at {@code level}, for a build that derives a target set from the
     * criticality recorded in the code instead of keeping a second, hand-maintained list beside
     * it (which is the one that silently goes stale). Typical use:
     *
     * <pre>{@code
     * pitest {
     *     targetClasses.set(criticality.classesAt("HIGH"))
     * }
     * }</pre>
     *
     * Fails rather than returning an empty set, for the reason given at
     * {@link #getLevelsThatMustNotBeEmpty()}.
     */
    public Provider<Set<String>> classesAt(String level) {
        // Derived from the file collections rather than assembled by hand, so the provider
        // carries their task dependencies: whatever consumes it (pitest.targetClasses, say)
        // makes the classes get compiled first instead of scanning an empty output directory.
        return getMainClassesDirs().getElements().zip(getClasspath().getElements(), (classesDirs, classpath) -> {
            Set<String> classNames = CriticalityScan.classNamesAt(
                    CriticalityScan.scan(
                            filesOf(classesDirs),
                            filesOf(classpath),
                            getCriticalityAnnotationFqn().get()),
                    level);
            if (classNames.isEmpty()) {
                throw new GradleException("No class is annotated " + level
                        + " -- whatever derives its target set from this would run against nothing "
                        + "and report success. Either the annotation is gone or collecting it is broken.");
            }
            return classNames;
        });
    }

    private static Set<File> filesOf(Set<? extends FileSystemLocation> locations) {
        Set<File> files = new LinkedHashSet<>();
        locations.forEach(location -> files.add(location.getAsFile()));
        return files;
    }
}
