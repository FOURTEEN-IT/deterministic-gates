package de.fourteen.gates.requirements;

import de.fourteen.gates.internal.AnnotatedElements;
import de.fourteen.gates.internal.JUnitResults;
import de.fourteen.gates.internal.Reports;
import de.fourteen.gates.internal.RequirementsTable;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Convention: a marker annotation (named by {@link #getRequirementAnnotationFqn()}, e.g.
 * {@code @Requirement("4.2")}) on a test method ties it to one or more IDs from
 * {@link #getRequirementsFile()}'s register. This gate collects every ID whose row is tagged
 * with {@link #getCoverageCategory()}, then requires that each one is claimed by at least one
 * annotated method that actually passed -- an annotation alone, on a red or deleted test,
 * doesn't count.
 */
public abstract class RequirementsCoverageTask extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getRequirementsFile();

    @Input
    public abstract Property<String> getCoverageCategory();

    @Input
    public abstract Property<String> getRequirementAnnotationFqn();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getTestClassesDirs();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getTestResultsDirs();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getTestRuntimeClasspath();

    @OutputFile
    public abstract RegularFileProperty getReportFile();

    @TaskAction
    public void check() {
        File requirementsFile = getRequirementsFile().get().getAsFile();
        String category = getCoverageCategory().get();
        Set<String> requiredIds = RequirementsTable.idsInCategory(requirementsFile, category);

        Set<String> passedTests = JUnitResults.passedTestMethods(getTestResultsDirs().getFiles());

        try (URLClassLoader classLoader = AnnotatedElements.classLoaderFor(
                getTestRuntimeClasspath().getFiles(), getClass().getClassLoader())) {
            String annotationFqn = getRequirementAnnotationFqn().get();
            Class<? extends Annotation> annotationClass =
                    AnnotatedElements.loadAnnotationClass(classLoader, annotationFqn);
            Method valueMethod = annotationClass.getMethod("value");

            Set<String> claimedIds = new LinkedHashSet<>();
            for (File classesDir : getTestClassesDirs().getFiles()) {
                for (String className : AnnotatedElements.classNamesUnder(classesDir)) {
                    Class<?> klass;
                    try {
                        klass = classLoader.loadClass(className);
                    } catch (Throwable e) {
                        continue;
                    }
                    for (Method method : klass.getDeclaredMethods()) {
                        Annotation annotation = method.getAnnotation(annotationClass);
                        if (annotation == null) {
                            continue;
                        }
                        if (!passedTests.contains(className + "#" + method.getName())) {
                            continue;
                        }
                        claimedIds.addAll(idsFromAnnotationValue(valueMethod.invoke(annotation)));
                    }
                }
            }

            List<String> uncovered = requiredIds.stream()
                    .filter(id -> !claimedIds.contains(id))
                    .sorted()
                    .toList();

            StringBuilder report = new StringBuilder();
            report.append("Requirements coverage (category \"").append(category).append("\"): ")
                    .append(requiredIds.size() - uncovered.size()).append(" of ")
                    .append(requiredIds.size()).append(" requirement(s) covered.\n");
            if (uncovered.isEmpty()) {
                report.append("No open requirements in this category.\n");
            } else {
                report.append("Open (").append(uncovered.size()).append("):\n");
                uncovered.forEach(id -> report.append("  - ").append(id).append("\n"));
            }
            Reports.write(getReportFile().get().getAsFile(), report.toString());

            if (!uncovered.isEmpty()) {
                throw new GradleException("Requirements coverage incomplete: " + uncovered.size()
                        + " open requirement(s) in category \"" + category + "\" -- " + uncovered);
            }
        } catch (ReflectiveOperationException | java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Set<String> idsFromAnnotationValue(Object value) {
        Set<String> ids = new TreeSet<>();
        if (value instanceof String single) {
            ids.add(single);
        } else if (value instanceof String[] many) {
            ids.addAll(List.of(many));
        } else if (value != null) {
            throw new GradleException("Requirement annotation's value() must return String or String[], got: "
                    + value.getClass());
        }
        return ids;
    }
}
