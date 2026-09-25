package de.fourteen.gates.testlayers;

import de.fourteen.gates.internal.AnnotatedElements;
import de.fourteen.gates.internal.Reports;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * Convention: a project that splits its tests into layers -- domain, port, adapter, api, or
 * whatever it calls them -- selects each layer by tag, and runs each in its own task. This gate
 * checks the assumption that split rests on: every test method belongs to <em>exactly</em> one
 * layer, counting a tag on the class and a tag on the method together.
 *
 * <p>It also rejects a layer with no test method at all, for the same reason: the task
 * selecting it passes on an empty result, which reads exactly like a real run.
 *
 * <p>None of these failures is visible from a build log otherwise. A method with no layer tag runs in
 * no task at all -- a green build that silently never executed it looks exactly like one that
 * did. A method with two layer tags runs twice, and its coverage lands in two execution-data
 * files, which quietly falsifies anything that compares layers against each other (a
 * layer-disjointness gate above all).
 */
public abstract class TestLayersTask extends DefaultTask {

    @Input
    public abstract ListProperty<String> getLayers();

    @Input
    public abstract ListProperty<String> getTagAnnotationFqns();

    @Input
    public abstract ListProperty<String> getTestMethodAnnotationFqns();

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
        List<String> layers = getLayers().get();
        Set<String> tagAnnotations = new LinkedHashSet<>(getTagAnnotationFqns().get());
        Set<String> testMethodAnnotations = new LinkedHashSet<>(getTestMethodAnnotationFqns().get());

        List<String> withoutLayer = new ArrayList<>();
        List<String> withSeveralLayers = new ArrayList<>();
        TreeMap<String, Integer> perLayer = new TreeMap<>();
        layers.forEach(layer -> perLayer.put(layer, 0));
        int testMethods = 0;

        try (URLClassLoader classLoader =
                AnnotatedElements.classLoaderFor(getClasspath().getFiles(), getClass().getClassLoader())) {
            for (File classesDir : getTestClassesDirs().getFiles()) {
                for (String className : AnnotatedElements.classNamesUnder(classesDir)) {
                    Class<?> klass;
                    try {
                        klass = classLoader.loadClass(className);
                    } catch (Throwable e) {
                        continue;
                    }
                    Set<String> classTags = tagsOn(klass, tagAnnotations);
                    for (Method method : klass.getDeclaredMethods()) {
                        if (!isTestMethod(method, testMethodAnnotations)) {
                            continue;
                        }
                        testMethods++;
                        Set<String> tags = new LinkedHashSet<>(classTags);
                        tags.addAll(tagsOn(method, tagAnnotations));
                        tags.retainAll(layers);
                        String name = className + "#" + method.getName();
                        if (tags.isEmpty()) {
                            withoutLayer.add(name);
                        } else if (tags.size() > 1) {
                            withSeveralLayers.add(name + " is " + tags);
                        } else {
                            String layer = tags.iterator().next();
                            perLayer.merge(layer, 1, Integer::sum);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        List<String> emptyLayers = perLayer.entrySet().stream()
                .filter(entry -> entry.getValue() == 0)
                .map(java.util.Map.Entry::getKey)
                .toList();

        Reports.write(getReportFile().get().getAsFile(),
                report(testMethods, perLayer, withoutLayer, withSeveralLayers, emptyLayers));

        if (!withoutLayer.isEmpty()) {
            throw new GradleException("Test method in no layer: " + withoutLayer
                    + " -- a test no layer selects runs in no task, and a build that never ran it "
                    + "looks exactly like a green one.");
        }
        if (!withSeveralLayers.isEmpty()) {
            throw new GradleException("Test method in more than one layer: " + withSeveralLayers
                    + " -- it runs twice and its coverage lands in two execution-data files.");
        }
        if (!emptyLayers.isEmpty()) {
            throw new GradleException("Layer without a single test method: " + emptyLayers
                    + " -- the task selecting that layer passes on an empty result, which reads "
                    + "exactly like a real run. Drop the layer or give it tests.");
        }
    }

    private static boolean isTestMethod(Method method, Set<String> testMethodAnnotations) {
        for (Annotation annotation : method.getAnnotations()) {
            if (testMethodAnnotations.contains(annotation.annotationType().getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tag names on an element, resolved through meta-annotations: a project's own
     * {@code @UnitTest} that itself carries {@code @Tag("unit")} is how the layer and its
     * report name are kept from drifting apart, and reading only the direct annotations would
     * miss exactly that.
     */
    private static Set<String> tagsOn(AnnotatedElement element, Set<String> tagAnnotations) {
        Set<String> tags = new LinkedHashSet<>();
        Set<String> visited = new LinkedHashSet<>();
        for (Annotation annotation : element.getAnnotations()) {
            collectTags(annotation, tagAnnotations, tags, visited);
        }
        return tags;
    }

    private static void collectTags(Annotation annotation, Set<String> tagAnnotations, Set<String> tags,
            Set<String> visited) {
        Class<? extends Annotation> type = annotation.annotationType();
        // java.lang.annotation.* is where meta-annotation cycles live (@Retention is itself
        // @Documented, and so on) and it never carries a tag.
        if (type.getName().startsWith("java.lang.annotation.") || !visited.add(type.getName())) {
            return;
        }
        if (tagAnnotations.contains(type.getName())) {
            addValue(annotation, tagAnnotations, tags, visited);
            return;
        }
        for (Annotation meta : type.getAnnotations()) {
            collectTags(meta, tagAnnotations, tags, visited);
        }
    }

    private static void addValue(Annotation annotation, Set<String> tagAnnotations, Set<String> tags,
            Set<String> visited) {
        Object value;
        try {
            value = AnnotatedElements.annotationValue(annotation, "value");
        } catch (RuntimeException e) {
            return;
        }
        if (value instanceof String tag) {
            tags.add(tag);
        } else if (value instanceof Annotation[] nested) {
            // A container annotation (@Tags) holds the repeated ones.
            for (Annotation each : nested) {
                visited.remove(each.annotationType().getName());
                collectTags(each, tagAnnotations, tags, visited);
            }
        }
    }

    private static String report(int testMethods, TreeMap<String, Integer> perLayer,
            List<String> withoutLayer, List<String> withSeveralLayers, List<String> emptyLayers) {
        StringBuilder report = new StringBuilder();
        report.append("Test layers: ").append(testMethods).append(" test method(s).\n");
        perLayer.forEach((layer, count) -> report.append("  ").append(layer).append(": ")
                .append(count).append(" method(s)\n"));
        if (withoutLayer.isEmpty() && withSeveralLayers.isEmpty() && emptyLayers.isEmpty()) {
            report.append("Every test method belongs to exactly one layer, and every layer has tests.\n");
        }
        appendAll(report, "In no layer", withoutLayer);
        appendAll(report, "In more than one layer", withSeveralLayers);
        appendAll(report, "Layer without a single test method", emptyLayers);
        return report.toString();
    }

    private static void appendAll(StringBuilder report, String heading, List<String> entries) {
        if (entries.isEmpty()) {
            return;
        }
        report.append(heading).append(" (").append(entries.size()).append("):\n");
        entries.forEach(entry -> report.append("  - ").append(entry).append("\n"));
    }
}
