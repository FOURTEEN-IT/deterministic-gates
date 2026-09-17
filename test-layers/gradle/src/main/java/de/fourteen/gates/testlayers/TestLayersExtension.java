package de.fourteen.gates.testlayers;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;

import java.util.List;

/** Configuration for the {@code testLayers} gate. */
public abstract class TestLayersExtension {

    /**
     * The tag names that stand for this project's test layers, e.g. {@code ["unit", "port",
     * "adapter", "api"]}. No default: which layers a project has is the one thing this gate
     * can't guess, and setting it is what "configured" means here.
     */
    public abstract ListProperty<String> getLayers();

    /**
     * Annotations whose {@code value()} carries a tag name. Defaults to JUnit 5's {@code @Tag}
     * and {@code @Tags}, plus jqwik's own {@code @Tag} -- a property-test engine that only
     * knows its own tag type is exactly how a whole layer goes quietly unrun.
     */
    public abstract ListProperty<String> getTagAnnotationFqns();

    /** Annotations that make a method a test. Defaults to the JUnit 5 and jqwik ones. */
    public abstract ListProperty<String> getTestMethodAnnotationFqns();

    public abstract ConfigurableFileCollection getTestClassesDirs();

    public abstract ConfigurableFileCollection getClasspath();

    static final List<String> DEFAULT_TAG_ANNOTATIONS = List.of(
            "org.junit.jupiter.api.Tag",
            "org.junit.jupiter.api.Tags",
            "net.jqwik.api.Tag");

    static final List<String> DEFAULT_TEST_METHOD_ANNOTATIONS = List.of(
            "org.junit.jupiter.api.Test",
            "org.junit.jupiter.api.RepeatedTest",
            "org.junit.jupiter.api.TestFactory",
            "org.junit.jupiter.params.ParameterizedTest",
            "net.jqwik.api.Property",
            "net.jqwik.api.Example");
}
