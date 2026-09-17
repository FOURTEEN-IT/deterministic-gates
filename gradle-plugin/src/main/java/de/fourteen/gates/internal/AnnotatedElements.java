package de.fourteen.gates.internal;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Loads compiled classes with a fresh {@link URLClassLoader} and inspects them for a marker
 * annotation. Reflection over the compiled output is used instead of source-text search so
 * that inherited/repeated annotations and annotation attributes are read exactly as the JVM
 * sees them, not approximated by a regular expression.
 */
public final class AnnotatedElements {

    private AnnotatedElements() {
    }

    public static URLClassLoader classLoaderFor(Iterable<File> classpath, ClassLoader parent) {
        List<URL> urls = new ArrayList<>();
        for (File file : classpath) {
            try {
                urls.add(file.toURI().toURL());
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }
        return new URLClassLoader(urls.toArray(new URL[0]), parent);
    }

    public static List<String> classNamesUnder(File classesDir) {
        List<String> names = new ArrayList<>();
        if (classesDir.isDirectory()) {
            collect(classesDir, classesDir, names);
        }
        return names;
    }

    /** Fully qualified names of classes annotated with {@code annotationFqn}, loaded via {@code classLoader}. */
    public static Set<String> classesAnnotatedWith(List<String> classNames, ClassLoader classLoader, String annotationFqn) {
        Set<String> found = new LinkedHashSet<>();
        Class<? extends Annotation> annotationClass = loadAnnotationClass(classLoader, annotationFqn);
        for (String className : classNames) {
            Class<?> klass = tryLoad(classLoader, className);
            if (klass != null && klass.getAnnotation(annotationClass) != null) {
                found.add(className);
            }
        }
        return found;
    }

    /** Methods annotated with {@code annotationFqn} across all given classes, as {@code Class#method}. */
    public static Set<String> methodsAnnotatedWith(List<String> classNames, ClassLoader classLoader, String annotationFqn) {
        Set<String> found = new LinkedHashSet<>();
        Class<? extends Annotation> annotationClass = loadAnnotationClass(classLoader, annotationFqn);
        for (String className : classNames) {
            Class<?> klass = tryLoad(classLoader, className);
            if (klass == null) {
                continue;
            }
            for (Method method : klass.getDeclaredMethods()) {
                if (method.getAnnotation(annotationClass) != null) {
                    found.add(className + "#" + method.getName());
                }
            }
        }
        return found;
    }

    @SuppressWarnings("unchecked")
    public static Class<? extends Annotation> loadAnnotationClass(ClassLoader classLoader, String annotationFqn) {
        try {
            return (Class<? extends Annotation>) classLoader.loadClass(annotationFqn);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Annotation class not found on the classpath: " + annotationFqn, e);
        }
    }

    public static Object annotationValue(Object annotation, String attribute) {
        try {
            Method method = annotation.getClass().getMethod(attribute);
            return method.invoke(annotation);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Class<?> tryLoad(ClassLoader classLoader, String className) {
        try {
            return classLoader.loadClass(className);
        } catch (Throwable e) {
            return null;
        }
    }

    private static void collect(File root, File dir, List<String> names) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collect(root, child, names);
            } else if (child.getName().endsWith(".class") && !child.getName().contains("$")) {
                String relative = root.toPath().relativize(child.toPath()).toString();
                names.add(relative.substring(0, relative.length() - ".class".length()).replace(File.separatorChar, '.'));
            }
        }
    }
}
