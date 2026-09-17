package de.fourteen.gates.githooks;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Registers {@code installGitHooks} alone -- not wired into {@code check}, since installing a
 * git hook is a one-time, opt-in setup step (run by hand), not something a build gate would
 * enforce on every run.
 */
public class GitHooksPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getTasks().register("installGitHooks", InstallGitHooksTask.class, task -> {
            task.setGroup("verification");
            task.setDescription("Copies the bundled commit-msg hook into .git/hooks.");
            task.getGitRootDir().set(project.getRootDir());
        });
    }
}
