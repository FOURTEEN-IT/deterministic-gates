package de.fourteen.gates;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Locks in the plugin's default conventions as actual, checked facts -- in particular that
 * requirementsCoverage and suppressionRegister point at this repo's own {@code annotations}
 * module by default, so a project needs zero configuration beyond adding that one dependency.
 */
class GatesPluginConventionsTest {

    private GatesExtension extensionOfFreshlyAppliedPlugin() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply(GatesPlugin.class);
        return project.getExtensions().getByType(GatesExtension.class);
    }

    @Test
    void requirementAnnotationDefaultsToTheShippedAnnotation() {
        assertEquals("de.fourteen.gates.annotations.Requirement",
                extensionOfFreshlyAppliedPlugin().getRequirementAnnotationFqn().get());
    }

    @Test
    void suppressionAnnotationsDefaultToTheShippedAnnotationPlusJUnitDisabled() {
        assertEquals(List.of("de.fourteen.gates.annotations.RegisteredSuppression", "org.junit.jupiter.api.Disabled"),
                extensionOfFreshlyAppliedPlugin().getSuppressionAnnotationFqns().get());
    }
}
