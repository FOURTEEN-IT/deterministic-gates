package de.fourteen.gates.requirements;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Locks in the plugin's default {@code requirementAnnotationFqn} as an actual, checked fact: it
 * points at this repo's own {@code annotations} module, so a project needs zero
 * {@code requirements {}} configuration for {@code requirementsCoverage} beyond adding that one
 * dependency.
 */
class RequirementsPluginConventionsTest {

    @Test
    void requirementAnnotationDefaultsToTheShippedAnnotation() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply(RequirementsPlugin.class);
        RequirementsExtension extension = project.getExtensions().getByType(RequirementsExtension.class);

        assertEquals("de.fourteen.gates.annotations.Requirement", extension.getRequirementAnnotationFqn().get());
    }
}
