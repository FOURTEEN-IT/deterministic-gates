package de.fourteen.gates.featureslicing;

import de.fourteen.gates.internal.AnnotatedElements;
import de.fourteen.gates.internal.JUnitResults;
import de.fourteen.gates.internal.Reports;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Extends {@code requirementsCoverage}'s guarantee down to slice granularity. A leaf slice (a
 * slice doc with no child slices underneath it -- see {@link SliceTree}) needs at least one
 * passed test annotated with the slice's own ID, using the same marker annotation
 * {@code requirementsCoverage} reads. A parent slice or feature showing covered says nothing
 * about whether its leaves actually got implemented; this gate closes that gap, so a leaf can't
 * stay silently unimplemented once its top-level requirement shows covered.
 *
 * <p>A feature or slice that hasn't been split yet (no children at all) isn't a leaf slice in
 * this sense -- it's covered, if at all, by {@code requirementsCoverage} at the requirement-ID
 * level, since it hasn't reached "ready for implementation" as its own, independently sliced
 * piece.
 */
public abstract class SliceCoverageTask extends DefaultTask {

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getFeaturesDir();

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
        List<String> leafIds = new ArrayList<>();
        for (SliceTree.Node root : SliceTree.forest(getFeaturesDir().get().getAsFile())) {
            collectLeaves(root, true, leafIds);
        }

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
                        if (annotation == null || !passedTests.contains(className + "#" + method.getName())) {
                            continue;
                        }
                        claimedIds.addAll(idsFromAnnotationValue(valueMethod.invoke(annotation)));
                    }
                }
            }

            List<String> uncovered = leafIds.stream().filter(id -> !claimedIds.contains(id)).sorted().toList();

            StringBuilder report = new StringBuilder();
            report.append("Slice coverage: ").append(leafIds.size() - uncovered.size()).append(" of ")
                    .append(leafIds.size()).append(" leaf slice(s) covered.\n");
            if (uncovered.isEmpty()) {
                report.append("No open leaf slices.\n");
            } else {
                report.append("Open (").append(uncovered.size()).append("):\n");
                uncovered.forEach(id -> report.append("  - ").append(id).append("\n"));
            }
            Reports.write(getReportFile().get().getAsFile(), report.toString());

            if (!uncovered.isEmpty()) {
                throw new GradleException("Slice coverage incomplete: " + uncovered.size()
                        + " open leaf slice(s) -- " + uncovered);
            }
        } catch (ReflectiveOperationException | java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void collectLeaves(SliceTree.Node node, boolean isFeatureRoot, List<String> leafIds) {
        if (node.children().isEmpty()) {
            if (!isFeatureRoot) {
                leafIds.add(node.id());
            }
            return;
        }
        for (SliceTree.Node child : node.children()) {
            collectLeaves(child, false, leafIds);
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
