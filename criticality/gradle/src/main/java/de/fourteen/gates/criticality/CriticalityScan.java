package de.fourteen.gates.criticality;

import de.fourteen.gates.internal.AnnotatedElements;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads the criticality annotation off compiled classes and methods. Shared by the
 * {@code criticality} task and by {@link CriticalityExtension#classesAt(String)}, so that the
 * gate and whatever derives from the same annotation (a mutation-testing target set, say)
 * cannot drift apart: both see one scan, not two implementations of one.
 */
final class CriticalityScan {

    private CriticalityScan() {
    }

    /**
     * One annotated element. {@code methodName} is null for a class-level annotation -- the
     * class itself is the finding then.
     */
    record Finding(String className, String methodName, String level, List<String> requirements) {

        String describe() {
            return methodName == null ? className : className + "#" + methodName;
        }
    }

    static List<Finding> scan(Set<File> classesDirs, Set<File> classpath, String annotationFqn) {
        List<Finding> findings = new ArrayList<>();
        try (URLClassLoader classLoader =
                AnnotatedElements.classLoaderFor(classpath, CriticalityScan.class.getClassLoader())) {
            Class<? extends Annotation> annotationClass =
                    AnnotatedElements.loadAnnotationClass(classLoader, annotationFqn);
            for (File classesDir : classesDirs) {
                for (String className : AnnotatedElements.classNamesUnder(classesDir)) {
                    Class<?> klass;
                    try {
                        klass = classLoader.loadClass(className);
                    } catch (Throwable e) {
                        // A class whose own dependencies aren't on the given classpath can't
                        // carry a readable annotation either -- skip it rather than fail the
                        // scan over a classpath question this gate has no opinion on.
                        continue;
                    }
                    Annotation onClass = klass.getAnnotation(annotationClass);
                    if (onClass != null) {
                        findings.add(toFinding(className, null, onClass));
                    }
                    for (Method method : klass.getDeclaredMethods()) {
                        Annotation onMethod = method.getAnnotation(annotationClass);
                        if (onMethod != null) {
                            findings.add(toFinding(className, method.getName(), onMethod));
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return findings;
    }

    /** Class names carrying {@code level} on the class itself or on any of its methods. */
    static Set<String> classNamesAt(List<Finding> findings, String level) {
        Set<String> names = new LinkedHashSet<>();
        for (Finding finding : findings) {
            if (finding.level().equals(level)) {
                names.add(finding.className());
            }
        }
        return names;
    }

    private static Finding toFinding(String className, String methodName, Annotation annotation) {
        // Read through the annotation's own accessors rather than a fixed type: a project that
        // would rather keep its existing criticality annotation only has to offer level() and
        // requirements(), and this gate reads it the same way (see criticalityAnnotationFqn).
        String level = String.valueOf(AnnotatedElements.annotationValue(annotation, "level"));
        Object requirements = AnnotatedElements.annotationValue(annotation, "requirements");
        List<String> ids = requirements instanceof String[] array
                ? Arrays.asList(array)
                : List.of();
        return new Finding(className, methodName, level, ids);
    }
}
