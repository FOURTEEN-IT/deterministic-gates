# deterministic-gates

Deterministic, judgment-free build gates for JVM projects — plus a small set
of companion Claude Code skills and hooks — extracted from real use in a
production project.

## The idea

A build has two kinds of checks. **Gates** are deterministic: given the same
input, they always give the same verdict, and they can't be skipped — a
compiler error, a failing test, an architecture rule, a template a document
must follow. **Skills** need judgment: does this commit's type actually match
what it does, is this the right way to slice a feature, is the story behind
an exception still accurate. A skill can be skipped by a careless agent or a
rushed human; a gate can't.

This repository packages the gate half of that distinction so it doesn't
have to be reinvented per project, and a few of the judgment-half skills
that pair naturally with it.

## What's inside

### Gradle plugins (`gradle-plugin/`)

Six gates, five plugin ids, zero runtime dependencies beyond the JDK and the
Gradle API. Five, not one and not six: `structureDoc`, `layerDisjointness`
and `suppressionRegister` check three unrelated things and split cleanly;
`requirementsCoverage`, `taggedRequirementsCoverage` and `featureDocs` stay
together in one plugin because all three read the same requirements register
through the same parser — splitting those three further would mean
duplicating that parser instead of sharing it.

| Plugin id | Extension | Task(s) | Checks |
|-----------|-----------|---------|--------|
| `de.fourteen.gates.structuredoc` | `structureDoc {}` | `structureDoc` | An architecture doc names no file that doesn't exist in the project, and mentions every domain type |
| `de.fourteen.gates.requirements` | `requirements {}` | `requirementsCoverage` | Every requirement in a category is claimed by an annotated test method that actually passed |
| | | `taggedRequirementsCoverage` | Every requirement in a category is tagged somewhere in source (for stacks where "which tests passed" isn't uniformly readable, e.g. a separate frontend) |
| | | `featureDocs` | Every feature doc follows the template, states exactly one criticality, stays under the acceptance-criteria limit, and references only real requirement IDs |
| `de.fourteen.gates.layerdisjointness` | `layerDisjointness {}` | `layerDisjointness` | No line of domain code is covered only by an outer-layer test — a gap further in isn't credited to the outer layer |
| `de.fourteen.gates.suppressionregister` | `suppressionRegister {}` | `suppressionRegister` | Every test/mutation suppression in code has a matching, dated entry in a register, and vice versa |
| `de.fourteen.gates.githooks` | — | `installGitHooks` | (not a gate — see below) |

Each gate checks against a **fixed file convention**, documented below —
adopting a gate means adopting that convention, not configuring a format to
match whatever you already have. What's configurable is only what's
inevitably project-specific: file paths, a package prefix, an annotation's
fully qualified name.

Applying a plugin doesn't force its gate(s) on you: each task only attaches
to `check` once its own required property is actually set (`domainModelDir`,
`requirementsFile`, `exceptionsRegisterFile`, ...) — apply
`de.fourteen.gates.requirements` and configure only `featuresDir`, and only
`featureDocs` runs. Applying a plugin and configuring nothing is a no-op,
not a guaranteed build failure.

`de.fourteen.gates.githooks` registers `installGitHooks`, a one-time opt-in
task that copies a Conventional-Commits-checking `commit-msg` git hook into
`.git/hooks` — not itself a gate, so it's never wired into `check`.

### Annotations library (`annotations/`)

`requirementsCoverage` and `suppressionRegister` need a real marker
annotation on the JVM classpath — not just a string naming one — so this
repo ships one, as its own small, dependency-free artifact
(`de.fourteen.gates:annotations`) separate from the Gradle plugin (a
project's *test code* needs this on its compile/test classpath; the Gradle
plugin itself never does).

| Annotation | Read by |
|------------|---------|
| `@Requirement("4.2")` | `requirementsCoverage` — put on a test method |
| `@RegisteredSuppression` | `suppressionRegister` — put on a suppressed class or method |

Both gates default to these two (`suppressionRegister` also checks
`org.junit.jupiter.api.Disabled` by default, no dependency needed for that
one). Add the dependency and you're done — no extension configuration needed
for either gate unless you'd rather use an annotation you already have.

### Claude Code plugin (`claude-plugin/`)

| Skill | For |
|-------|-----|
| `adr` | Recording a technical decision as an Architecture Decision Record |
| `open-decisions` | Carrying an answered open question through every place it needs to land |
| `staged-verification` | Running cheap, local checks before expensive, broad ones — stop at the first red |
| `release-impact` | Making a commit's release/deploy consequence explicit before typing its Conventional Commits type |

| Hook | Event | Does |
|------|-------|------|
| `main-branch-rule.sh` | `PreToolUse` (Bash) | Blocks creating a new branch/worktree; blocks a commit whose branch is behind its upstream |
| `session-start.sh` | `SessionStart` | Surfaces working-tree state and any open decisions at the start of a session |

A fifth skill, for turning an idea into vertically-sliced, independently
buildable pieces, is planned but not yet written — see [Status](#status).

## Using the Gradle plugins

Apply only the ones you want — they don't depend on each other.

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        maven("https://jitpack.io")
        gradlePluginPortal()
    }
}
```

```kotlin
// build.gradle.kts
plugins {
    id("de.fourteen.gates.structuredoc") version "<tag>"
    id("de.fourteen.gates.requirements") version "<tag>"
    id("de.fourteen.gates.layerdisjointness") version "<tag>"
    id("de.fourteen.gates.suppressionregister") version "<tag>"
    id("de.fourteen.gates.githooks") version "<tag>"
    // see "Consuming these plugins" below for what <tag> resolves against
}

repositories {
    mavenCentral()
    maven("https://jitpack.io") // for the annotations dependency below, until it's on Central
}

dependencies {
    testImplementation("de.fourteen.gates:annotations:<tag>")
}

structureDoc {
    architectureDocFile.set(layout.projectDirectory.file("ARCHITECTURE.md"))
    domainModelDir.set(layout.projectDirectory.dir("src/main/java/com/example/domain"))
}

requirements {
    requirementsFile.set(layout.projectDirectory.file("docs/requirements.md"))
    // requirementAnnotationFqn defaults to de.fourteen.gates.annotations.Requirement
    // coverageCategory defaults to "backend"
    featuresDir.set(layout.projectDirectory.dir("docs/features"))
}

layerDisjointness {
    domainPackagePrefix.set("com/example/domain")
    innerCoverageReportXml.set(layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml"))
    outerCoverageReportXmls.from(layout.buildDirectory.file("reports/jacoco/integrationTest/report.xml"))
}

suppressionRegister {
    exceptionsRegisterFile.set(layout.projectDirectory.file("docs/test-exceptions.md"))
    // suppressionAnnotationFqns defaults to [RegisteredSuppression, org.junit.jupiter.api.Disabled]
}
```

Every property not shown above has a sensible convention-based default (see
each plugin's `*Extension` class for the full list); `requirements`'s
test-classes, test-results and classpath inputs for `requirementsCoverage`
default to the `test` source set if the `java` plugin is applied,
additively — add more with `.from(...)` for extra test suites (integration,
contract, etc.).

### The conventions, with an example row each

**`structureDoc`** — no fixed row format; it scans the doc's prose for
filenames (`\w[\w.-]*\.(java|jsx|js|kts|md|toml|yml)`) and the project's
domain directory for `*.java` type names, then cross-checks both directions.

**`requirementsCoverage` / `taggedRequirementsCoverage` / `featureDocs`** all
read the same requirements register: a markdown table anywhere in
`requirementsFile`, matched by row shape alone (no heading required):

```
| ID  | Reference           | Category |
|-----|----------------------|----------|
| 4.2 | Players join a room | backend  |
```

`requirementsCoverage` needs the marker annotation from `annotations/` (or
your own, see `requirementAnnotationFqn`) on the test side:

```java
import de.fourteen.gates.annotations.Requirement;

@Test
@Requirement("4.2")
void aPlayerCanJoinARoom() { ... }
```

`taggedRequirementsCoverage` instead expects a call tagged in source, useful
for stacks where "which tests passed" can't be read uniformly (e.g. a
separate frontend test runner):

```js
test("a player can join a room", () => {
  requirement("4.2");
  // ...
});
```

**`featureDocs`** expects each doc under `featuresDir` to have the headings
`Motivation`, `Affected Requirements`, `Acceptance Criteria`, `Scenarios`,
`Criticality`, `Implemented In`, `Open Questions` (all configurable via
`RequirementsExtension`, defaults shown), with:

```
## Affected Requirements

| ID  | Reference | Note      |
|-----|-----------|-----------|
| 4.2 | existing  | join flow |

## Acceptance Criteria

1. A player can enter a room code.
2. An invalid code shows an error.

## Criticality

**Level:** HIGH
```

`Reference` must be one of `existing`, `changed`, `new`, `reverted`
(configurable); unless it's `new`, the ID must already be in the
requirements register.

**`suppressionRegister`** looks for `@RegisteredSuppression` from
`annotations/` (plus JUnit 5's `@Disabled`, or your own via
`suppressionAnnotationFqns`) on a class or method:

```java
import de.fourteen.gates.annotations.RegisteredSuppression;

@RegisteredSuppression
void aKnownFlakyTest() { ... }
```

and expects a markdown table (again matched by shape, any heading text)
whose first column names the suppressed class or `Class.method`:

```
| Suppressed            | Reason                       | Date       |
|------------------------|-------------------------------|------------|
| PaymentGateway.retry   | flaky third-party API in CI  | 2026-03-01 |
```

### Consuming these plugins

This repository doesn't publish to the Gradle Plugin Portal (yet — see
[Status](#status)). All five plugin ids come from the same `gradle-plugin/`
build, so one dependency resolution covers all five. Until published:

1. **JitPack** (least setup): add `maven("https://jitpack.io")` to
   `pluginManagement.repositories`, then depend on a tagged commit's version.
2. **GitHub Packages**: cleaner, but needs a token configured in the
   consuming project's `pluginManagement.repositories`.
3. **`includeBuild`**: for working on the plugins and a consumer together —
   point a composite build at a local checkout of this repo's
   `gradle-plugin/` directory.

The same applies to `de.fourteen.gates:annotations` (the `annotations/`
directory) — it's a separate, ordinary Maven-coordinate dependency, resolved
the same three ways, not bundled inside any plugin jar.

### Installing the commit-msg hook

With `de.fourteen.gates.githooks` applied:

```
./gradlew installGitHooks
```

Copies a Conventional-Commits-format checker into `.git/hooks/commit-msg`.
It checks *format* only (does the subject line parse as `type(scope): ...`
with an Angular-preset type) — not whether the type matches what actually
changed. That judgment call is what the `release-impact` skill below is for.

## Using the Claude Code plugin

Point Claude Code at `claude-plugin/` as a plugin directory (locally, or
once published, via a marketplace) to get its four skills. To use only the
two hooks without the rest, copy `claude-plugin/hooks/*.sh` into your
project and wire them in `.claude/settings.json` — see
`claude-plugin/settings.snippet.json` for the exact shape. Both hooks read
their one configurable value from an environment variable
(`GATES_MAIN_BRANCH`, `GATES_OPEN_DECISIONS_FILE`) with a sensible default,
rather than from a config file.

## Status

This is a first extraction from a single origin project, done in one pass.
Expect the extensions' property names and the exact file conventions to
still move a little as a second and third consuming project exercise them.
Semantic versioning starts in earnest once there's evidence beyond the
original project that the shape is right — the `annotations` module
especially, since `@Requirement`/`@RegisteredSuppression` end up scattered
across a consuming project's test code, more expensive to change later than
a Gradle property name.

A fifth Claude Code skill — turning an idea into vertically-sliced,
independently shippable pieces of work — is planned but deliberately not
included yet; its design is still being worked out.

## License

Apache 2.0 with the Commons Clause — see [LICENSE](LICENSE). In short: free
to use, modify and redistribute, not to sell as a standalone product.
