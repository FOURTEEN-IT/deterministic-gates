package de.fourteen.gates.annotations;

import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The requirementsCoverage and suppressionRegister gates load these annotations by name and
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
    void requirementValueAcceptsOneOrMoreIds() throws NoSuchMethodException {
        assertTrue(Requirement.class.getMethod("value").getReturnType().isArray());
    }

    private static ElementType[] targetOf(Class<?> annotationType) {
        return annotationType.getAnnotation(java.lang.annotation.Target.class).value();
    }
}
