package de.fourteen.gates.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class or method as a deliberate, registered exception to a normally-enforced testing
 * rule -- an equivalent mutant excluded from mutation testing, for example.
 *
 * <p>Read by the {@code suppressionRegister} gate in the deterministic-gates Gradle plugin:
 * every use must have a matching, dated, justified row in the project's exceptions register.
 * The reason lives in that register, not as an attribute here, so it's visible without opening
 * the source file, and a stale entry (register row with no suppression left in the code) is
 * exactly as visible as a suppression with no entry.
 *
 * <p>{@code org.junit.jupiter.api.Disabled} is checked by the same gate by default, alongside
 * this annotation -- most JVM test suites already suppress with both, and neither should need a
 * project to reinvent it.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RegisteredSuppression {
}
