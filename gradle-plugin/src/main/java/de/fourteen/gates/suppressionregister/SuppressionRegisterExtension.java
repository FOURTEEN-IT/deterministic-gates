package de.fourteen.gates.suppressionregister;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;

/** Configuration for the {@code suppressionRegister} gate. */
public abstract class SuppressionRegisterExtension {

    public abstract RegularFileProperty getExceptionsRegisterFile();

    // Defaults to this plugin's own de.fourteen.gates.annotations.RegisteredSuppression plus
    // JUnit 5's @Disabled (both already on most JVM test classpaths, the latter without any
    // extra dependency at all); add to the list rather than replacing it if a project has
    // further suppression annotations of its own.
    public abstract ListProperty<String> getSuppressionAnnotationFqns();
}
