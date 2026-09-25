package de.fourteen.gates.annotations;

import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The requirementsCoverage, suppressionRegister and criticality gates load these annotations by name and
 * reflect over them at build time (see the gradle-plugin module) -- retention must stay RUNTIME
 * or that reflection finds nothing, silently turning every gate green for the wrong reason.
 */
class AnnotationShapeTest {

    @Test
    void requirementIsRuntimeRetainedOnMethods() {
        assertEquals(RetentionPolicy.RUNTIME, Requirement.class.getAnnotation(java.lang.annotation.Retention.class).value());
        assertEquals(Set.of(ElementType.METHOD), Set.of(targetOf(Requirement.class)));
    }

    @Test
    void registeredSuppressionIsRuntimeRetainedOnTypesAndMethods() {
        assertEquals(RetentionPolicy.RUNTIME,
                RegisteredSuppression.class.getAnnotation(java.lang.annotation.Retention.class).value());
        assertEquals(Set.of(ElementType.TYPE, ElementType.METHOD), Set.of(targetOf(RegisteredSuppression.class)));
    }

    @Test
    void criticalityIsRuntimeRetainedOnTypesAndMethods() {
        assertEquals(RetentionPolicy.RUNTIME,
                Criticality.class.getAnnotation(java.lang.annotation.Retention.class).value());
        assertEquals(Set.of(ElementType.TYPE, ElementType.METHOD), Set.of(targetOf(Criticality.class)));
    }

    @Test
    void criticalityCarriesALevelAndTheRequirementsJustifyingIt() throws NoSuchMethodException {
        // The gate reads both by name off whatever annotation it is pointed at, so these two
        // accessors are the contract -- renaming either breaks every consumer silently.
        assertEquals(Criticality.Level.class, Criticality.class.getMethod("level").getReturnType());
        assertTrue(Criticality.class.getMethod("requirements").getReturnType().isArray());
    }

    @Test
    void requirementValueAcceptsOneOrMoreIds() throws NoSuchMethodException {
        assertTrue(Requirement.class.getMethod("value").getReturnType().isArray());
    }

    private static ElementType[] targetOf(Class<?> annotationType) {
        return annotationType.getAnnotation(java.lang.annotation.Target.class).value();
    }
}
