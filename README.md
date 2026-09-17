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
feature-slicing/         claude/                (skill; gate planned)
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
| `feature-slicing/` | (planned gate) | `feature-slicing` skill |

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
to `check` once what it actually needs is there (`domainModelDir`,
`requirementsFile`, `exceptionsRegisterFile`, ...) — apply
`de.fourteen.gates.requirements` and configure only `featuresDir`, and only
`featureDocs` runs. Applying a plugin and configuring nothing is a no-op,
not a guaranteed build failure.

`requirementsFile` alone doesn't decide this for the three requirements
gates, since all three read it: `requirementsCoverage` additionally needs
test classes (i.e. the `java` plugin, or `testClassesDirs` set by hand),
`taggedRequirementsCoverage` needs `taggedSourceDirs`, and `featureDocs`
needs `featuresDir`.

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
    // Point these at the report *tasks*, not at the paths they happen to write to: a literal
    // path carries no task dependency, so Gradle is free to run the gate before the reports
    // exist -- and, since 8.x, refuses the build outright rather than judging a stale file.
    innerCoverageReportXml.set(
        tasks.named<JacocoReport>("jacocoTestReport").flatMap { it.reports.xml.outputLocation })
    outerCoverageReportXmls.from(
        tasks.named<JacocoReport>("jacocoIntegrationTestReport").flatMap { it.reports.xml.outputLocation })
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

When you do add one, hand `testResultsDirs` the *task*, not the path it
writes to — `.from(tasks.named<Test>("integrationTest"))` rather than
`.from(layout.buildDirectory.dir("test-results/integrationTest"))`. The
default for `test` is wired that way for the same reason: a literal path
carries no task dependency, and a gate that reads test results before the
tests have run is not a gate.

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
whose first column names the suppressed class or `Class.method`. A simple
name is enough as long as one suppression answers to it; where two classes
in different packages share a name, qualify it
(`demo.domain.PaymentGateway.retry`) — an ambiguous row fails the gate
rather than counting for both. Rows whose first column can't name a class or
method at all are skipped, so an unrelated table in the same file doesn't
turn into stale entries:

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

## Development process

The gates and skills above aren't independent tools bolted together; they're
meant to support one flow, from a raw feature idea to a shipped, verified
feature:

1. **Idea clarification** (planned) — before anything is cut into slices, a
   skill interrogates the idea itself: what's actually being asked for, what's
   assumed but unstated, where the edges are. Its job is to surface gaps
   while they're still cheap to close, not after a slice has already been cut
   around a wrong assumption.

2. **Vertical slicing** — the clarified idea is cut into small, atomic,
   independently shippable vertical slices — each one a complete path
   through the system, not a horizontal layer. The `feature-slicing` skill
   (see [What's inside](#whats-inside)) does this today; its companion gate
   is still planned:

   - A top-level feature is one entry in the existing requirements register
     (e.g. requirement `4.2`), documented in full up front by the
     idea-clarification skill above, before any slicing starts.
   - The slicing skill takes that feature and cuts it, one level at a time,
     into slices — each slice gets its own doc, one folder level deeper and
     numbered from its parent (`4.2/1/`, then `4.2/1/2/`, ...), the same
     nesting repeating at every level a slice needs splitting further.
   - Only the *first* slice at any level is ever examined: is it vertical,
     and is it still worth splitting further? If a candidate cut isn't
     vertical, that level isn't done — cut again. If it is vertical but
     still splittable, it's split again, one level deeper. This repeats
     until a vertical slice is reached that can't be split further —
     vertical-and-unsplittable is the stopping condition, not a size
     threshold. Sibling slices at every level are left untouched until the
     first slice has been carried all the way through — split, implemented
     (step 3) and accepted (step 4) — before the next sibling is even looked
     at.
   - How "vertical" gets checked deterministically isn't decided yet; for
     now it's the skill's (or a human's) judgment call.

   A companion Gradle gate is planned alongside this skill, checking what
   *can* already be checked deterministically without solving the
   vertical-check problem first:

   - the slice folder/numbering scheme is consistent (no gaps, each slice's
     number matches its position under its parent);
   - each slice doc's status (e.g. "needs further slicing" vs. "ready for
     implementation") matches what the folder structure actually shows —
     a "ready" slice with child slices already underneath it, or a "needs
     splitting" slice with none, is a contradiction the gate rejects;
   - `requirementsCoverage`'s check extended down to slice granularity: a
     leaf slice (no children, "ready for implementation") needs a passing
     test that references *that slice's* ID, not just the top-level
     requirement's — the same no-gap guarantee `requirementsCoverage`
     already gives at the requirement level, pushed one level further down
     so a slice can't quietly stay unimplemented once its parent requirement
     shows covered.

3. **TDD implementation** (planned) — a skill drives one slice at a time
   through red/green/refactor: write the failing test, make it pass with the
   smallest change that does so, then refactor. `staged-verification` is
   only the supporting check-order for this loop (compile, then
   unit/domain tests, then architecture tests, then the full check, stopping
   at the first red stage) — it doesn't drive the test-first cycle itself,
   so this is a separate, not-yet-built skill.

4. **Acceptance against the original description** (planned) — once a slice
   is implemented, it's checked back against what was actually asked for,
   not only against the tests written for it: does the finished feature do
   what the original description said, or did the implementation quietly
   narrow, widen or reinterpret it along the way. `featureDocs` is a
   different, narrower check — it only enforces that a feature doc follows
   the required structure and references real requirement IDs, not that the
   implementation matches the doc. This step triggers automatically whenever
   a `fix` or `feat` commit is made (the two Conventional-Commits types that
   actually change behavior), the same commit `installGitHooks`'s
   `commit-msg` hook already parses the type of — so the acceptance check
   piggybacks on a signal that's already there rather than needing a new
   trigger. Not yet covered by any gate or skill here.

Steps 1, 3 and 4 are not built yet, and step 2's own gate isn't either —
they're named here so the gap is visible, not to claim tooling that doesn't
exist.

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

A fifth Claude Code skill, `feature-slicing` — turning a feature into
vertically-sliced, independently shippable pieces of work — is now included;
its companion Gradle gate is planned but not built yet. An idea-clarifying
skill meant to precede it (writing the feature doc `feature-slicing` starts
from) is planned but doesn't exist yet either. See
[Development process](#development-process) for where both fit.

## License

Apache 2.0 — see [LICENSE](LICENSE). Free to use, modify, redistribute and
build on, commercially included.
