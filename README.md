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

## Layout

Organized by subject matter first, technology second — a directory is
`requirements/` or `commit-discipline/`, not `gradle-plugin/` or
`claude-plugin/`. Within a subject, its Gradle-side and Claude-side pieces
(where both exist) sit next to each other under `gradle/`, `claude/` and
`annotations/`:

```
adr/                     claude/                (skill)
open-decisions/          claude/                (skill + hook)
staged-verification/     claude/                (skill)
commit-discipline/       gradle/  claude/       (git hook install task + skill + hook)
structure-doc/           gradle/
requirements/            gradle/  annotations/  (three gates + @Requirement)
layer-disjointness/      gradle/
suppression-register/    gradle/  annotations/  (gate + @RegisteredSuppression)
```

Two more top-level directories hold no subject-matter content of their own,
only build definitions that assemble the above into publishable artifacts:

- **`gradle-plugin/`** — `settings.gradle.kts`, `build.gradle.kts`, the
  wrapper, and `src/main/java/.../internal/` (the parsing/reflection helpers
  shared across gates — belongs to no single subject, so it lives with the
  build that shares it). Its `sourceSets` pull each gate's actual Java
  sources from the directories above.
- **`annotations/`** — the same, for `de.fourteen.gates:annotations`; pulls
  `Requirement.java` from `requirements/annotations/` and
  `RegisteredSuppression.java` from `suppression-register/annotations/`.

Why one Gradle module reaching across directories, rather than N independent
builds matching the layout 1:1: applying `de.fourteen.gates.structuredoc`
already doesn't pull in `suppressionRegister`'s task or extension (checked
with a real, separate build in `PluginIdsFunctionalTest` — different plugin
ids from the same jar stay fully independent at the point that matters, what
a consumer's build sees). Splitting the build itself into five would only
multiply wrapper/CI/version bookkeeping for a distinction consumers can't
observe.

## What's inside

| Directory | Gradle | Claude Code |
|-----------|--------|-------------|
| `structure-doc/` | `structureDoc` gate | — |
| `requirements/` | `requirementsCoverage`, `taggedRequirementsCoverage`, `featureDocs` gates + `@Requirement` | — |
| `layer-disjointness/` | `layerDisjointness` gate | — |
| `suppression-register/` | `suppressionRegister` gate + `@RegisteredSuppression` | — |
| `commit-discipline/` | `installGitHooks` (commit-msg format) | `release-impact` skill, `main-branch-rule.sh` hook |
| `open-decisions/` | — | `open-decisions` skill, `session-start.sh` hook |
| `adr/` | — | `adr` skill |
| `staged-verification/` | — | `staged-verification` skill |

`requirements/` and `commit-discipline/` are the only directories with more
than one thing inside, and each time for a concrete, checked reason, not
convenience:

- `requirementsCoverage`, `taggedRequirementsCoverage` and `featureDocs`
  read the same requirements register through the same parser — splitting
  them further would mean duplicating that parser instead of sharing it.
- `commit-discipline` groups everything about how a commit gets made and
  what it triggers: format (`installGitHooks`'s git-native `commit-msg`
  hook, works no matter what tool commits), branch/timing
  (`main-branch-rule.sh`, only while Claude Code itself runs `git`) and
  release consequence (`release-impact`, judgment about whether the type
  matches the change). Three different technologies, one subject.

Every other pairing was checked and found *not* load-bearing before being
kept separate: `open-decisions` names `adr` as the recommended way to write
one, worded so it degrades gracefully if `adr` isn't installed — the only
cross-reference among the four skills, and cheap enough to not force a
bundle over it. `staged-verification` and `adr` reference nothing else.

## Gradle gates

Six gates, five plugin ids, zero runtime dependencies beyond the JDK and the
Gradle API — every gate here parses plain text, JUnit/JaCoCo XML (the JDK's
own `javax.xml.parsers`) or reflects over compiled classes.

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

### The `annotations` artifact

`requirementsCoverage` and `suppressionRegister` need a real marker
annotation on the JVM classpath — not just a string naming one — so this
repo ships two, as one small, dependency-free artifact
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
each gate's `*Extension` class, under `<subject>/gradle/src/main/java/`, for
the full list); `requirements`'s test-classes, test-results and classpath
inputs for `requirementsCoverage` default to the `test` source set if the
`java` plugin is applied, additively — add more with `.from(...)` for extra
test suites (integration, contract, etc.).

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

`requirementsCoverage` needs the marker annotation (or your own, see
`requirementAnnotationFqn`) on the test side:

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

**`suppressionRegister`** looks for `@RegisteredSuppression` (plus JUnit 5's
`@Disabled`, or your own via `suppressionAnnotationFqns`) on a class or
method:

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
changed. That judgment call is what the `release-impact` skill is for.

## Using the Claude Code plugins

Point Claude Code at one of `adr/claude/`, `open-decisions/claude/`,
`staged-verification/claude/` or `commit-discipline/claude/` as a plugin
directory (locally, or once published, via a marketplace) to get that
directory's skill — install as many or as few as you want.

To use a hook, copy its script into your project and wire it in
`.claude/settings.json`:

| Hook | Lives at | Event | Does |
|------|----------|-------|------|
| `main-branch-rule.sh` | `commit-discipline/claude/hooks/` | `PreToolUse` (Bash) | Blocks creating a new branch/worktree; blocks a commit whose branch is behind its upstream |
| `session-start.sh` | `open-decisions/claude/hooks/` | `SessionStart` | Surfaces working-tree state and any open decisions at the start of a session |

Each has its own `settings.snippet.json` next to it (in the same `claude/`
directory) showing the exact wiring. Both hooks read their one configurable
value from an environment variable (`GATES_MAIN_BRANCH`,
`GATES_OPEN_DECISIONS_FILE`) with a sensible default, rather than from a
config file.

## Status

This is a first extraction from a single origin project, done in one pass —
including this directory layout itself, reorganized once already (by
subject matter first, technology second) after the initial technology-first
cut turned out to obscure which pieces actually belonged together. Expect
the extensions' property names and the exact file conventions to still move
a little as a second and third consuming project exercise them. Semantic
versioning starts in earnest once there's evidence beyond the original
project that the shape is right — the `annotations` artifact especially,
since `@Requirement`/`@RegisteredSuppression` end up scattered across a
consuming project's test code, more expensive to change later than a Gradle
property name.

A fifth Claude Code skill — turning an idea into vertically-sliced,
independently shippable pieces of work — is planned but deliberately not
included yet; its design is still being worked out.

## License

Apache 2.0 with the Commons Clause — see [LICENSE](LICENSE). In short: free
to use, modify and redistribute, not to sell as a standalone product.
