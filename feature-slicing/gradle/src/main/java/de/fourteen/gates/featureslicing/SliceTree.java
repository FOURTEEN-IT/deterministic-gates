package de.fourteen.gates.featureslicing;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Walks the feature-slicing folder convention (see the {@code feature-slicing} skill): a slice
 * doc {@code N.md} may have a same-named sibling directory {@code N/} holding its own numbered
 * children ({@code N/1.md}, {@code N/2.md}, ...), recursively. A node with no such directory (or
 * an empty one) is a leaf -- a slice small enough that it hasn't been split further.
 *
 * <p>Structural problems (a gap in the numbering, a file that isn't a {@code <number>.md} doc)
 * are collected per node rather than thrown here, so a caller can report every problem in the
 * tree in one pass instead of stopping at the first one.
 */
final class SliceTree {

    record Node(String id, File doc, File childDir, List<Node> children, List<String> issues) {
    }

    private static final Pattern NUMBERED_DOC = Pattern.compile("([0-9]+)\\.md");

    private SliceTree() {
    }

    /** One tree per top-level feature doc directly under {@code featuresDir} (skips {@code _template.md}). */
    static List<Node> forest(File featuresDir) {
        File[] files = featuresDir.listFiles(
                f -> f.isFile() && f.getName().endsWith(".md") && !f.getName().equals("_template.md"));
        List<Node> forest = new ArrayList<>();
        if (files == null) {
            return forest;
        }
        for (File doc : List.of(files).stream().sorted(Comparator.comparing(File::getName)).toList()) {
            String id = doc.getName().substring(0, doc.getName().length() - ".md".length());
            forest.add(walk(id, doc, new File(featuresDir, id)));
        }
        return forest;
    }

    private static Node walk(String id, File doc, File childDir) {
        List<String> issues = new ArrayList<>();
        List<Integer> numbers = new ArrayList<>();

        if (childDir.isDirectory()) {
            File[] entries = childDir.listFiles();
            if (entries != null) {
                for (File entry : List.of(entries).stream().sorted(Comparator.comparing(File::getName)).toList()) {
                    Matcher matcher = NUMBERED_DOC.matcher(entry.getName());
                    if (entry.isFile() && matcher.matches()) {
                        numbers.add(Integer.parseInt(matcher.group(1)));
                    } else if (entry.isFile()) {
                        issues.add(childDir.getName() + "/" + entry.getName()
                                + " doesn't match the <number>.md slice-doc naming convention");
                    }
                    // A subdirectory is visited when its own numbered doc is walked below.
                }
            }
        }

        List<Integer> distinct = numbers.stream().distinct().sorted().toList();
        if (distinct.size() != numbers.size()) {
            issues.add(childDir.getName() + "/ has a duplicate slice number");
        }
        for (int expected = 1; expected <= distinct.size(); expected++) {
            if (!distinct.contains(expected)) {
                issues.add(childDir.getName() + "/ is missing slice " + expected
                        + ".md -- slice numbering must be contiguous from 1");
            }
        }

        List<Node> children = new ArrayList<>();
        for (int n : distinct) {
            String childId = id + "." + n;
            children.add(walk(childId, new File(childDir, n + ".md"), new File(childDir, String.valueOf(n))));
        }
        return new Node(id, doc, childDir, children, issues);
    }
}
