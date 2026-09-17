package de.fourteen.gates.internal;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

/** Reads JUnit-style XML result files and reports which test methods passed. */
public final class JUnitResults {

    private JUnitResults() {
    }

    /** Returns every passed test method as {@code "fully.qualified.ClassName#methodName"}. */
    public static Set<String> passedTestMethods(Iterable<File> resultDirs) {
        Set<String> passed = new LinkedHashSet<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        for (File dir : resultDirs) {
            File[] files = dir.listFiles((d, name) -> name.endsWith(".xml"));
            if (files == null) {
                continue;
            }
            for (File xmlFile : files) {
                try {
                    Document document = factory.newDocumentBuilder().parse(xmlFile);
                    NodeList testcases = document.getElementsByTagName("testcase");
                    for (int i = 0; i < testcases.getLength(); i++) {
                        Element testcase = (Element) testcases.item(i);
                        boolean failed = testcase.getElementsByTagName("failure").getLength() > 0
                                || testcase.getElementsByTagName("error").getLength() > 0;
                        if (failed) {
                            continue;
                        }
                        String className = testcase.getAttribute("classname");
                        String methodName = testcase.getAttribute("name");
                        int parenIndex = methodName.indexOf('(');
                        if (parenIndex >= 0) {
                            methodName = methodName.substring(0, parenIndex);
                        }
                        passed.add(className + "#" + methodName);
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Could not parse JUnit result file: " + xmlFile, e);
                }
            }
        }
        return passed;
    }
}
