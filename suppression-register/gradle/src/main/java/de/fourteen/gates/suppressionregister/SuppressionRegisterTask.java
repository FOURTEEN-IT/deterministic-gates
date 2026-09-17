package de.fourteen.gates.suppressionregister;

import de.fourteen.gates.internal.AnnotatedElements;
import de.fourteen.gates.internal.MarkdownTables;
import de.fourteen.gates.internal.Reports;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Convention: a suppression -- a class or method carrying one of
 * {@link #getSuppressionAnnotationFqns()} -- must have a matching row in
 * {@link #getExceptionsRegisterFile()}, identified by simple class name, or
 * {@code SimpleClassName.methodName} for a method-level suppression. This gate diffs the two
 * directions: a suppression without a register entry is an unexplained exception; a register
 * entry with no matching suppression left in the code is a stale row.
 *
 * <p>A simple name stays enough to write in the register as long as it picks out one
 * suppression. Where two classes in different packages share a name, it doesn't, and the row is
 * rejected as ambiguous rather than quietly counted for both -- which is how a register with one
 * row used to account for two suppressions, waving through the second.
 */
public abstract class SuppressionRegisterTask extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getExceptionsRegisterFile();

    @org.gradle.api.tasks.Input
    public abstract ListProperty<String> getSuppressionAnnotationFqns();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getMainClassesDirs();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getTestClassesDirs();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getClasspath();

    @OutputFile
    public abstract RegularFileProperty getReportFile();

    /**
     * One suppression in the code: {@code qualifiedName} is what makes it unique
     * ({@code pkg.Class} or {@code pkg.Class.method}), {@code simpleName} is how the register is
     * allowed to refer to it when nothing else answers to that name.
     */
    private record Suppression(String qualifiedName, String simpleName) implements Comparable<Suppression> {
        @Override
        public int compareTo(Suppression other) {
            return qualifiedName.compareTo(other.qualifiedName);
        }
    }

    @TaskAction
    public void check() {
        try (URLClassLoader classLoader =
                AnnotatedElements.classLoaderFor(getClasspath().getFiles(), getClass().getClassLoader())) {
            TreeSet<Suppression> suppressions = new TreeSet<>();
            for (String annotationFqn : getSuppressionAnnotationFqns().get()) {
                Class<? extends Annotation> annotationClass;
                try {
                    annotationClass = AnnotatedElements.loadAnnotationClass(classLoader, annotationFqn);
                } catch (RuntimeException e) {
                    // Not every project has all configured suppression annotations on the
                    // classpath at once (e.g. a JUnit @Disabled that a minimal test setup
                    // never pulls in) -- skip it rather than fail the whole gate.
                    continue;
                }
                collectSuppressions(getMainClassesDirs().getFiles(), classLoader, annotationClass, suppressions);
                collectSuppressions(getTestClassesDirs().getFiles(), classLoader, annotationClass, suppressions);
            }

            LinkedHashSet<String> registerEntries = readRegisterEntries(getExceptionsRegisterFile().get().getAsFile());

            Map<String, Suppression> byQualifiedName = new LinkedHashMap<>();
            Map<String, List<Suppression>> bySimpleName = new LinkedHashMap<>();
            for (Suppression suppression : suppressions) {
                byQualifiedName.put(suppression.qualifiedName(), suppression);
                bySimpleName.computeIfAbsent(suppression.simpleName(), unused -> new ArrayList<>()).add(suppression);
            }

            Set<Suppression> claimed = new LinkedHashSet<>();
            List<String> withoutSuppression = new ArrayList<>();
            List<String> ambiguous = new ArrayList<>();
            for (String entry : registerEntries) {
                Suppression exact = byQualifiedName.get(entry);
                if (exact != null) {
                    claimed.add(exact);
                    continue;
                }
                List<Suppression> candidates = bySimpleName.getOrDefault(entry, List.of());
                if (candidates.size() == 1) {
                    claimed.add(candidates.get(0));
                } else if (candidates.size() > 1) {
                    ambiguous.add(entry + " -> " + candidates.stream().map(Suppression::qualifiedName).sorted().toList());
                } else {
                    withoutSuppression.add(entry);
                }
            }
            withoutSuppression.sort(null);
            ambiguous.sort(null);

            List<String> withoutEntry = suppressions.stream()
                    .filter(suppression -> !claimed.contains(suppression))
                    .map(suppression -> display(suppression, bySimpleName))
                    .sorted()
                    .toList();

            StringBuilder report = new StringBuilder();
            report.append("Suppression register: ").append(suppressions.size()).append(" suppression(s) in code, ")
                    .append(registerEntries.size()).append(" entr(y/ies) in the register.\n");
            if (withoutEntry.isEmpty() && withoutSuppression.isEmpty() && ambiguous.isEmpty()) {
                report.append("Code and register agree.\n");
            }
            if (!ambiguous.isEmpty()) {
                report.append("Ambiguous register entr(y/ies) (").append(ambiguous.size()).append("):\n");
                ambiguous.forEach(a -> report.append("  - ").append(a).append("\n"));
            }
            if (!withoutEntry.isEmpty()) {
                report.append("Without a register entry (").append(withoutEntry.size()).append("):\n");
                withoutEntry.forEach(s -> report.append("  - ").append(s).append("\n"));
            }
            if (!withoutSuppression.isEmpty()) {
                report.append("Register entry with no suppression in the code (").append(withoutSuppression.size()).append("):\n");
                withoutSuppression.forEach(s -> report.append("  - ").append(s).append("\n"));
            }
            Reports.write(getReportFile().get().getAsFile(), report.toString());

            if (!ambiguous.isEmpty()) {
                throw new GradleException("Ambiguous register entr(y/ies): " + ambiguous
                        + " -- more than one suppression answers to that name, so the row can't say "
                        + "which one is justified. Write the qualified name instead.");
            }
            if (!withoutEntry.isEmpty()) {
                throw new GradleException("Suppression without a register entry: " + withoutEntry
                        + " -- every suppression needs a dated, justified row in the register.");
            }
            if (!withoutSuppression.isEmpty()) {
                throw new GradleException("Stale register entry: " + withoutSuppression
                        + " -- no matching suppression left in the code, remove the row.");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** The shortest name that still identifies this suppression: simple where unique, qualified otherwise. */
    private static String display(Suppression suppression, Map<String, List<Suppression>> bySimpleName) {
        return bySimpleName.getOrDefault(suppression.simpleName(), List.of()).size() == 1
                ? suppression.simpleName()
                : suppression.qualifiedName();
    }

    private static void collectSuppressions(Iterable<File> classesDirs, URLClassLoader classLoader,
            Class<? extends Annotation> annotationClass, TreeSet<Suppression> found) {
        for (File classesDir : classesDirs) {
            for (String className : AnnotatedElements.classNamesUnder(classesDir)) {
                Class<?> klass;
                try {
                    klass = classLoader.loadClass(className);
                } catch (Throwable e) {
                    continue;
                }
                String simpleName = klass.getSimpleName();
                if (klass.getAnnotation(annotationClass) != null) {
                    found.add(new Suppression(className, simpleName));
                }
                for (Method method : klass.getDeclaredMethods()) {
                    if (method.getAnnotation(annotationClass) != null) {
                        found.add(new Suppression(className + "." + method.getName(),
                                simpleName + "." + method.getName()));
                    }
                }
            }
        }
    }

    private static LinkedHashSet<String> readRegisterEntries(File file) {
        LinkedHashSet<String> entries = new LinkedHashSet<>();
        List<String> lines;
        try {
            lines = Files.readAllLines(file.toPath());
        } catch (IOException e) {
            throw new RuntimeException("Could not read " + file, e);
        }
        for (String row : MarkdownTables.dataRows(lines)) {
            String firstColumn = MarkdownTables.firstColumn(row).replace("`", "").trim();
            // A placeholder row ("_(none yet)_") documents an empty register on purpose and
            // is not itself a suppression entry.
            if (firstColumn.isEmpty() || firstColumn.startsWith("_(")
                    || !MarkdownTables.namesAJavaElement(firstColumn)) {
                continue;
            }
            entries.add(firstColumn);
        }
        return entries;
    }
}
