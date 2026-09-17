package de.fourteen.gates.structuredoc;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;

/**
 * Configuration for the {@code structureDoc} gate. The gate itself checks against a fixed
 * convention (see the README): these properties only say where that convention lives.
 */
public abstract class StructureDocExtension {

    public abstract RegularFileProperty getArchitectureDocFile();
    public abstract DirectoryProperty getDomainModelDir();
    public abstract ListProperty<String> getAllowedMissingNames();
}
