package de.fourteen.gates.internal;

import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/** Writes a gate's plain-text report to disk and echoes it to the console. */
public final class Reports {

    private Reports() {
    }

    public static void write(File file, String content) {
        try {
            Files.createDirectories(file.getParentFile().toPath());
            Files.writeString(file.toPath(), content);
            System.out.println(content);
        } catch (IOException e) {
            throw new RuntimeException("Could not write report to " + file, e);
        }
    }

    /** The conventional {@code build/reports/gates/<gateName>.txt} location every gate writes to. */
    public static Provider<RegularFile> conventionFile(Project project, String gateName) {
        return project.getLayout().getBuildDirectory().file("reports/gates/" + gateName + ".txt");
    }
}
