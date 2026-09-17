package de.fourteen.gates.internal;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

/** Reads a JaCoCo XML report and reports which lines under a package prefix were covered. */
public final class JacocoReport {

    private JacocoReport() {
    }

    /** Returns every covered line under {@code packagePrefix} as {@code "pkg/File.java:nr"}. */
    public static Set<String> coveredLines(File reportXml, String packagePrefix) {
        Set<String> lines = new LinkedHashSet<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            // JaCoCo's XML report references an external DTD that does not ship alongside the
            // report; without disabling external DTD loading, the parser tries to fetch it
            // over the network and fails.
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            Document document = factory.newDocumentBuilder().parse(reportXml);
            NodeList packages = document.getElementsByTagName("package");
            for (int p = 0; p < packages.getLength(); p++) {
                Element pkg = (Element) packages.item(p);
                String pkgName = pkg.getAttribute("name");
                if (!pkgName.startsWith(packagePrefix)) {
                    continue;
                }
                NodeList sourceFiles = pkg.getElementsByTagName("sourcefile");
                for (int s = 0; s < sourceFiles.getLength(); s++) {
                    Element sourceFile = (Element) sourceFiles.item(s);
                    String fileName = sourceFile.getAttribute("name");
                    NodeList lineNodes = sourceFile.getElementsByTagName("line");
                    for (int l = 0; l < lineNodes.getLength(); l++) {
                        Element line = (Element) lineNodes.item(l);
                        if (Integer.parseInt(line.getAttribute("ci")) > 0) {
                            lines.add(pkgName + "/" + fileName + ":" + line.getAttribute("nr"));
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not parse JaCoCo report: " + reportXml, e);
        }
        return lines;
    }
}
