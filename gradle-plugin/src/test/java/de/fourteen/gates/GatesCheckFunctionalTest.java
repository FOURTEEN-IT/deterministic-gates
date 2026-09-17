package de.fourteen.gates;

import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One green {@code check} per gate, in a real consumer project, with the configuration cache on.
 *
 * <p>Every gate here was previously covered only by tests that call the task's {@code check()}
 * method directly. Two defects lived in exactly the gap that leaves: {@code requirementsCoverage}
 * read the test results without depending on the task that writes them, so {@code check} could
 * judge the register before the tests had run; and {@code structureDoc} reached for the project
 * during execution, which fails as soon as a configuration-cache entry is reused. Neither is
 * visible without running a real build, so that is what these tests do.
 */
class GatesCheckFunctionalTest {

    private static final String REQUIREMENTS_REGISTER = """
            | ID  | Reference           | Category |
            |-----|---------------------|----------|
            | 4.2 | Players join a room | backend  |
            | 5.1 | Room code is shown  | frontend |
            """;

    private static final String REQUIREMENT_ANNOTATION = """
            package demo;
            import java.lang.annotation.*;
            @Retention(RetentionPolicy.RUNTIME)
            @Target({ElementType.METHOD, ElementType.TYPE})
            public @interface Req { String[] value() default {}; }
            """;

    @Test
    void structureDocPassesWhenDocAndTreeAgree(@TempDir Path projectDir) throws IOException {
        new GateFixture(projectDir)
                .plugin("de.fourteen.gates.structuredoc")
                .config("""
                        structureDoc {
                            architectureDocFile.set(layout.projectDirectory.file("ARCHITECTURE.md"))
                            domainModelDir.set(layout.projectDirectory.dir("src/main/java/demo/domain"))
                        }
                        """)
                .file("src/main/java/demo/domain/Room.java", "package demo.domain; public class Room {}")
                .file("ARCHITECTURE.md", "The domain has one aggregate, Room, defined in Room.java.\n")
                .runTwiceWithConfigurationCache("check");
    }

    @Test
    void structureDocFailsWhenTheDocNamesAFileThatIsGone(@TempDir Path projectDir) throws IOException {
        BuildResult result = new GateFixture(projectDir)
                .plugin("de.fourteen.gates.structuredoc")
                .config("""
                        structureDoc {
                            architectureDocFile.set(layout.projectDirectory.file("ARCHITECTURE.md"))
                            domainModelDir.set(layout.projectDirectory.dir("src/main/java/demo/domain"))
                        }
                        """)
                .file("src/main/java/demo/domain/Room.java", "package demo.domain; public class Room {}")
                .file("ARCHITECTURE.md", "Room lives in Room.java, the lobby in Lobby.java.\n")
                .runExpectingFailure("check");

        assertTrue(result.getOutput().contains("Lobby.java"),
                "the failure should name the file that no longer exists");
    }

    /**
     * The regression test for the ordering defect: a green, requirement-annotated test must be
     * credited by {@code requirementsCoverage} within a single {@code check} run. Before the fix
     * the gate ran first, found no results on disk, and reported every requirement as open.
     */
    @Test
    void requirementsCoverageSeesResultsOfTestsRunInTheSameCheck(@TempDir Path projectDir) throws IOException {
        BuildResult result = new GateFixture(projectDir)
                .plugin("de.fourteen.gates.requirements")
                .withJava()
                .config("""
                        requirements {
                            requirementsFile.set(layout.projectDirectory.file("docs/requirements.md"))
                            requirementAnnotationFqn.set("demo.Req")
                        }
                        """)
                .file("docs/requirements.md", REQUIREMENTS_REGISTER)
                .file("src/test/java/demo/Req.java", REQUIREMENT_ANNOTATION)
                .file("src/test/java/demo/JoinRoomTest.java", """
                        package demo;
                        import org.junit.jupiter.api.Test;
                        class JoinRoomTest {
                            @Test @Req("4.2") void aPlayerCanJoinARoom() {}
                        }
                        """)
                .runTwiceWithConfigurationCache("check");

        assertTrue(result.getOutput().indexOf(":test") < result.getOutput().indexOf(":requirementsCoverage"),
                "test must run before the gate that reads its results, output was:\n" + result.getOutput());
    }

    @Test
    void requirementsCoverageFailsWhenARequirementIsUnclaimed(@TempDir Path projectDir) throws IOException {
        BuildResult result = new GateFixture(projectDir)
                .plugin("de.fourteen.gates.requirements")
                .withJava()
                .config("""
                        requirements {
                            requirementsFile.set(layout.projectDirectory.file("docs/requirements.md"))
                            requirementAnnotationFqn.set("demo.Req")
                        }
                        """)
                .file("docs/requirements.md", REQUIREMENTS_REGISTER)
                .file("src/test/java/demo/Req.java", REQUIREMENT_ANNOTATION)
                .file("src/test/java/demo/JoinRoomTest.java", """
                        package demo;
                        import org.junit.jupiter.api.Test;
                        class JoinRoomTest {
                            @Test void aPlayerCanJoinARoom() {}
                        }
                        """)
                .runExpectingFailure("check");

        assertTrue(result.getOutput().contains("4.2"), "the failure should name the open requirement");
    }

    @Test
    void taggedRequirementsCoveragePassesWhenEveryIdIsTagged(@TempDir Path projectDir) throws IOException {
        new GateFixture(projectDir)
                .plugin("de.fourteen.gates.requirements")
                .config("""
                        requirements {
                            requirementsFile.set(layout.projectDirectory.file("docs/requirements.md"))
                            taggedSourceDirs.from(layout.projectDirectory.dir("web"))
                        }
                        """)
                .file("docs/requirements.md", REQUIREMENTS_REGISTER)
                .file("web/room.test.js", """
                        test("the room code is shown", () => {
                          requirement("5.1");
                        });
                        """)
                .runTwiceWithConfigurationCache("check");
    }

    @Test
    void featureDocsPassesForADocFollowingTheTemplate(@TempDir Path projectDir) throws IOException {
        new GateFixture(projectDir)
                .plugin("de.fourteen.gates.requirements")
                .config("""
                        requirements {
                            requirementsFile.set(layout.projectDirectory.file("docs/requirements.md"))
                            featuresDir.set(layout.projectDirectory.dir("docs/features"))
                        }
                        """)
                .file("docs/requirements.md", REQUIREMENTS_REGISTER)
                .file("docs/features/join-room.md", """
                        # Join a room

                        ## Motivation

                        A player with a code should reach the room it names.

                        ## Affected Requirements

                        | ID  | Reference | Note      |
                        |-----|-----------|-----------|
                        | 4.2 | existing  | join flow |

                        ## Acceptance Criteria

                        1. A player can enter a room code.
                        2. An invalid code shows an error.

                        ## Scenarios

                        Given a valid code, when the player submits it, they are in the room.

                        ## Criticality

                        **Level:** HIGH

                        ## Implemented In

                        Not yet.

                        ## Open Questions

                        None.
                        """)
                .runTwiceWithConfigurationCache("check");
    }

    @Test
    void layerDisjointnessPassesWhenTheInnerLayerCoversEveryDomainLine(@TempDir Path projectDir) throws IOException {
        new GateFixture(projectDir)
                .plugin("de.fourteen.gates.layerdisjointness")
                .config("""
                        layerDisjointness {
                            domainPackagePrefix.set("demo/domain")
                            innerCoverageReportXml.set(layout.projectDirectory.file("coverage/unit.xml"))
                            outerCoverageReportXmls.from(layout.projectDirectory.file("coverage/integration.xml"))
                        }
                        """)
                .file("coverage/unit.xml", jacocoReport(7, 8))
                .file("coverage/integration.xml", jacocoReport(7))
                .runTwiceWithConfigurationCache("check");
    }

    @Test
    void layerDisjointnessFailsOnADomainLineOnlyAnOuterTestReaches(@TempDir Path projectDir) throws IOException {
        BuildResult result = new GateFixture(projectDir)
                .plugin("de.fourteen.gates.layerdisjointness")
                .config("""
                        layerDisjointness {
                            domainPackagePrefix.set("demo/domain")
                            innerCoverageReportXml.set(layout.projectDirectory.file("coverage/unit.xml"))
                            outerCoverageReportXmls.from(layout.projectDirectory.file("coverage/integration.xml"))
                        }
                        """)
                .file("coverage/unit.xml", jacocoReport(7))
                .file("coverage/integration.xml", jacocoReport(7, 8))
                .runExpectingFailure("check");

        assertTrue(result.getOutput().contains("Room.java:8"),
                "the failure should name the line only the outer layer reaches");
    }

    @Test
    void suppressionRegisterPassesWhenCodeAndRegisterAgree(@TempDir Path projectDir) throws IOException {
        new GateFixture(projectDir)
                .plugin("de.fourteen.gates.suppressionregister")
                .withJava()
                .config("""
                        suppressionRegister {
                            exceptionsRegisterFile.set(layout.projectDirectory.file("docs/test-exceptions.md"))
                            suppressionAnnotationFqns.set(listOf("demo.Req"))
                        }
                        """)
                .file("src/test/java/demo/Req.java", REQUIREMENT_ANNOTATION)
                .file("src/test/java/demo/PaymentGatewayTest.java", """
                        package demo;
                        import org.junit.jupiter.api.Test;
                        class PaymentGatewayTest {
                            @Req void retry() {}
                            @Test void chargesACard() {}
                        }
                        """)
                .file("docs/test-exceptions.md", """
                        | Suppressed               | Reason                      | Date       |
                        |--------------------------|-----------------------------|------------|
                        | PaymentGatewayTest.retry | flaky third-party API in CI | 2026-03-01 |
                        """)
                .runTwiceWithConfigurationCache("check");
    }

    @Test
    void suppressionRegisterFailsOnAnUnregisteredSuppression(@TempDir Path projectDir) throws IOException {
        BuildResult result = new GateFixture(projectDir)
                .plugin("de.fourteen.gates.suppressionregister")
                .withJava()
                .config("""
                        suppressionRegister {
                            exceptionsRegisterFile.set(layout.projectDirectory.file("docs/test-exceptions.md"))
                            suppressionAnnotationFqns.set(listOf("demo.Req"))
                        }
                        """)
                .file("src/test/java/demo/Req.java", REQUIREMENT_ANNOTATION)
                .file("src/test/java/demo/PaymentGatewayTest.java", """
                        package demo;
                        import org.junit.jupiter.api.Test;
                        class PaymentGatewayTest {
                            @Req void retry() {}
                            @Test void chargesACard() {}
                        }
                        """)
                .file("docs/test-exceptions.md", """
                        | Suppressed | Reason | Date |
                        |------------|--------|------|
                        | _(none yet)_ |      |      |
                        """)
                .runExpectingFailure("check");

        assertTrue(result.getOutput().contains("PaymentGatewayTest.retry"),
                "the failure should name the unregistered suppression");
    }

    /**
     * The README tells adopters to point {@code layerDisjointness} at the report *task* rather
     * than the path it writes to. This checks that advice holds: a report produced during the
     * build, wired in as a provider, makes Gradle order the producer before the gate. Stood up
     * with a plain copying task instead of JaCoCo so the fixture stays off the network -- what
     * is under test is the wiring, and JaCoCo's report task is an ordinary task like this one.
     */
    @Test
    void layerDisjointnessWaitsForTheTaskThatWritesTheReport(@TempDir Path projectDir) throws IOException {
        BuildResult result = new GateFixture(projectDir)
                .plugin("de.fourteen.gates.layerdisjointness")
                .config("""
                        abstract class WriteCoverage : DefaultTask() {
                            @get:InputFile abstract val source: RegularFileProperty
                            @get:OutputFile abstract val report: RegularFileProperty
                            @TaskAction fun write() {
                                report.get().asFile.writeText(source.get().asFile.readText())
                            }
                        }

                        val innerReport = tasks.register<WriteCoverage>("innerReport") {
                            source.set(layout.projectDirectory.file("unit-source.xml"))
                            report.set(layout.buildDirectory.file("coverage/unit.xml"))
                        }

                        layerDisjointness {
                            domainPackagePrefix.set("demo/domain")
                            innerCoverageReportXml.set(innerReport.flatMap { it.report })
                            outerCoverageReportXmls.from(layout.projectDirectory.file("integration.xml"))
                        }
                        """)
                .file("unit-source.xml", jacocoReport(7, 8))
                .file("integration.xml", jacocoReport(7))
                .runTwiceWithConfigurationCache("check");

        assertTrue(result.getOutput().indexOf(":innerReport") < result.getOutput().indexOf(":layerDisjointness"),
                "the report task must run before the gate reading it, output was:\n" + result.getOutput());
    }

    /** A JaCoCo XML report covering the given lines of {@code demo/domain/Room.java}. */
    private static String jacocoReport(int... coveredLines) {
        StringBuilder lines = new StringBuilder();
        for (int line : coveredLines) {
            lines.append("      <line nr=\"").append(line).append("\" mi=\"0\" ci=\"3\" mb=\"0\" cb=\"0\"/>\n");
        }
        // The DOCTYPE is what a real JaCoCo report carries, and points at a DTD that isn't
        // shipped next to it -- the reason JacocoReport turns off external DTD loading.
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "report.dtd">
                <report name="fixture">
                  <package name="demo/domain">
                    <sourcefile name="Room.java">
                %s    </sourcefile>
                  </package>
                </report>
                """.formatted(lines);
    }
}
