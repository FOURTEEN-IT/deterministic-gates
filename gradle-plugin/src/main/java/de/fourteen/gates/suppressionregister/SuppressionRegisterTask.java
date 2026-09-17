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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.TreeSet;

/**
 * Convention: a suppression -- a class or method carrying one of
 * {@link #getSuppressionAnnotationFqns()} -- must have a matching row in
 * {@link #getExceptionsRegisterFile()}, identified by simple class name, or
 * {@code SimpleClassName.methodName} for a method-level suppression. This gate diffs the two
 * directions: a suppression without a register entry is an unexplained exception; a register
 * entry with no matching suppression left in the code is a stale row.
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

    @TaskAction
    public void check() {
        try (URLClassLoader classLoader =
                AnnotatedElements.classLoaderFor(getClasspath().getFiles(), getClass().getClassLoader())) {
            TreeSet<String> suppressions = new TreeSet<>();
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

            List<String> withoutEntry = suppressions.stream().filter(s -> !registerEntries.contains(s)).sorted().toList();
            List<String> withoutSuppression = registerEntries.stream().filter(e -> !suppressions.contains(e)).sorted().toList();

            StringBuilder report = new StringBuilder();
            report.append("Suppression register: ").append(suppressions.size()).append(" suppression(s) in code, ")
                    .append(registerEntries.size()).append(" entr(y/ies) in the register.\n");
            if (withoutEntry.isEmpty() && withoutSuppression.isEmpty()) {
                report.append("Code and register agree.\n");
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

    private static void collectSuppressions(Iterable<File> classesDirs, URLClassLoader classLoader,
            Class<? extends Annotation> annotationClass, TreeSet<String> found) {
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
                    found.add(simpleName);
                }
                for (Method method : klass.getDeclaredMethods()) {
                    if (method.getAnnotation(annotationClass) != null) {
                        found.add(simpleName + "." + method.getName());
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
            if (firstColumn.isEmpty() || firstColumn.startsWith("_(")) {
                continue;
            }
            entries.add(firstColumn);
        }
        return entries;
    }
}
