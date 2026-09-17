package de.fourteen.gates.suppressionregister;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Locks in the plugin's default {@code suppressionAnnotationFqns} as an actual, checked fact: it
 * points at this repo's own {@code annotations} module plus JUnit 5's {@code @Disabled}, so a
 * project needs zero {@code suppressionRegister {}} configuration beyond adding one dependency.
 */
class SuppressionRegisterPluginConventionsTest {

    @Test
    void suppressionAnnotationsDefaultToTheShippedAnnotationPlusJUnitDisabled() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply(SuppressionRegisterPlugin.class);
        SuppressionRegisterExtension extension = project.getExtensions().getByType(SuppressionRegisterExtension.class);

        assertEquals(List.of("de.fourteen.gates.annotations.RegisteredSuppression", "org.junit.jupiter.api.Disabled"),
                extension.getSuppressionAnnotationFqns().get());
    }
}
