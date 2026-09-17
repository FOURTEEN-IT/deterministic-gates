package de.fourteen.gates.layerdisjointness;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;

/** Configuration for the {@code layerDisjointness} gate. */
public abstract class LayerDisjointnessExtension {

    public abstract Property<String> getDomainPackagePrefix();
    public abstract RegularFileProperty getInnerCoverageReportXml();
    public abstract ConfigurableFileCollection getOuterCoverageReportXmls();
}
