package de.fourteen.gates.structuredoc;

import de.fourteen.gates.internal.Reports;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Convention: {@link #getArchitectureDocFile()} is a markdown/text file that names source files
 * by extension (e.g. {@code Room.java}, {@code build.gradle.kts}) and, in prose, the type names
 * that live under {@link #getDomainModelDir()}. This gate fails if the doc names a file that no
 * longer exists anywhere in the project, or omits a domain type that does exist -- catching the
 * two ways such a doc silently rots.
 */
public abstract class StructureDocTask extends DefaultTask {

    private static final Set<String> IGNORED_DIRECTORIES =
            Set.of("node_modules", "build", ".git", ".gradle", "bin");

    private static final Pattern NAMED_FILE = Pattern.compile(
            "\\b(\\w[\\w.-]*\\.(?:java|jsx|js|kts|md|toml|yml))\\b");

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getArchitectureDocFile();

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getDomainModelDir();

    @Input
    public abstract ListProperty<String> getAllowedMissingNames();

    @OutputFile
    public abstract RegularFileProperty getReportFile();

    @TaskAction
    public void check() {
        File docFile = getArchitectureDocFile().get().getAsFile();
        String text = readFile(docFile);
        Set<String> allowedMissing = new HashSet<>(getAllowedMissingNames().get());

        Set<String> namedFiles = new TreeSet<>();
        Matcher matcher = NAMED_FILE.matcher(text);
        while (matcher.find()) {
            namedFiles.add(matcher.group(1));
        }

        Set<String> existingNames = new HashSet<>();
        collectFileNames(getProject().getProjectDir(), existingNames);

        List<String> missing = namedFiles.stream()
                .filter(name -> !existingNames.contains(name) && !allowedMissing.contains(name))
                .sorted()
                .collect(Collectors.toList());

        File domainDir = getDomainModelDir().get().getAsFile();
        Set<String> domainTypes = new TreeSet<>();
        collectDomainTypeNames(domainDir, domainTypes);
        List<String> unmentioned = domainTypes.stream()
                .filter(type -> !Pattern.compile("\\b" + Pattern.quote(type) + "\\b").matcher(text).find())
                .sorted()
                .collect(Collectors.toList());

        StringBuilder report = new StringBuilder();
        report.append("Structure doc: ").append(namedFiles.size()).append(" file(s) named, ")
                .append(domainTypes.size()).append(" domain type(s) found.\n");
        if (missing.isEmpty() && unmentioned.isEmpty()) {
            report.append("The doc and the tree agree.\n");
        }
        if (!missing.isEmpty()) {
            report.append("Named in the doc but not found in the project (").append(missing.size()).append("):\n");
            missing.forEach(name -> report.append("  - ").append(name).append("\n"));
        }
        if (!unmentioned.isEmpty()) {
            report.append("Domain type not mentioned in the doc (").append(unmentioned.size()).append("):\n");
            unmentioned.forEach(name -> report.append("  - ").append(name).append("\n"));
        }
        Reports.write(getReportFile().get().getAsFile(), report.toString());

        if (!missing.isEmpty() || !unmentioned.isEmpty()) {
            StringBuilder message = new StringBuilder("Structure doc out of sync with the project: ");
            if (!missing.isEmpty()) {
                message.append("doc names files that don't exist: ").append(missing);
            }
            if (!unmentioned.isEmpty()) {
                if (!missing.isEmpty()) {
                    message.append("; ");
                }
                message.append("domain type(s) missing from the doc: ").append(unmentioned);
            }
            throw new GradleException(message.toString());
        }
    }

    private static void collectFileNames(File dir, Set<String> names) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                if (!IGNORED_DIRECTORIES.contains(child.getName())) {
                    collectFileNames(child, names);
                }
            } else {
                names.add(child.getName());
            }
        }
    }

    private static void collectDomainTypeNames(File dir, Set<String> names) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collectDomainTypeNames(child, names);
            } else if (child.getName().endsWith(".java") && !child.getName().equals("package-info.java")) {
                names.add(child.getName().substring(0, child.getName().length() - ".java".length()));
            }
        }
    }

    private static String readFile(File file) {
        try {
            return Files.readString(file.toPath());
        } catch (IOException e) {
            throw new RuntimeException("Could not read " + file, e);
        }
    }
}
