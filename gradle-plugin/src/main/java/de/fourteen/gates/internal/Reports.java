package de.fourteen.gates.internal;

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
}
