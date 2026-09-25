package de.fourteen.gates.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * How badly it hurts when this code is wrong -- likelihood times damage -- recorded at the
 * production code it applies to, not in a list beside it. The {@code requirements} attribute
 * points at IDs in the requirements register, so the reasoning behind the level can be looked
 * up rather than taken on trust; the {@code criticality} gate checks that every ID named here
 * actually exists there.
 *
 * <p>Applicable to a type <b>and</b> a method: one class can serve features of different
 * criticality, and the level belongs to the feature, not blanket to the file.
 *
 * <p>The point of recording it in the code is that other tooling can derive from it instead of
 * keeping a second, hand-maintained copy -- see {@code CriticalityExtension#classesAt} for the
 * mutation-testing target set that does exactly that.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Criticality {

    /** Nested so that a package holding this annotation holds nothing but annotations. */
    enum Level {
        LOW, MEDIUM, HIGH
    }

    Level level();

    /** Requirement IDs, as spelled in the requirements register, that justify this level. */
    String[] requirements();
}
