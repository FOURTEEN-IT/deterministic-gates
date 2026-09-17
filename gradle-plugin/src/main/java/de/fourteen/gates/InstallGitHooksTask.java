package de.fourteen.gates;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;

/**
 * Copies the bundled commit-msg hook into {@code .git/hooks}. Not wired into {@code check} --
 * installing a git hook is a one-time, opt-in setup step, run by hand:
 * {@code ./gradlew installGitHooks}.
 */
public abstract class InstallGitHooksTask extends DefaultTask {

    @TaskAction
    public void install() {
        Path gitDir = getProject().getRootDir().toPath().resolve(".git");
        if (!Files.isDirectory(gitDir)) {
            throw new GradleException("No .git directory found at " + gitDir + " -- run this from a git checkout.");
        }
        Path hooksDir = gitDir.resolve("hooks");
        try {
            Files.createDirectories(hooksDir);
            Path target = hooksDir.resolve("commit-msg");
            try (InputStream source = getClass().getResourceAsStream("/git-hooks/commit-msg")) {
                if (source == null) {
                    throw new GradleException("Bundled commit-msg hook resource not found");
                }
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.setPosixFilePermissions(target, Set.of(
                    java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                    java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                    java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE,
                    java.nio.file.attribute.PosixFilePermission.GROUP_READ,
                    java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE,
                    java.nio.file.attribute.PosixFilePermission.OTHERS_READ,
                    java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE));
            getLogger().lifecycle("Installed commit-msg hook at {}", target);
        } catch (IOException | UnsupportedOperationException e) {
            throw new RuntimeException("Could not install commit-msg hook", e);
        }
    }
}
