package de.fourteen.gates.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test method as covering one or more requirement IDs from the project's requirements
 * register.
 *
 * <p>Read by the {@code requirementsCoverage} gate in the deterministic-gates Gradle plugin: a
 * method carrying this annotation counts toward covering the requirement(s) it names only if
 * that test method actually passed. An annotation on a red or deleted test claims nothing.
 *
 * <pre>{@code
 * @Test
 * @Requirement("4.2")
 * void aPlayerCanJoinARoom() { ... }
 *
 * @Test
 * @Requirement({"4.2", "4.3"})
 * void joiningTwiceIsRejected() { ... }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Requirement {

    /** One or more requirement IDs from the project's requirements register. */
    String[] value();
}
