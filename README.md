# deterministic-gates

Deterministic, judgment-free build gates for JVM projects — plus a small set
of companion Claude Code skills and hooks — developed for real-world use,
not as an academic exercise.

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
idea-clarification/      claude/                (skill)
open-decisions/          claude/                (skill + hook)
staged-verification/     claude/                (skill)
tdd-implementation/      claude/                (skill)
acceptance/              claude/                (skill + hook)
demo-feedback/           claude/                (skill)
feature-delivery/        claude/                (skill; orchestrates the above five)
commit-discipline/       gradle/  claude/       (git hook install task + skill + 3 hooks)
structure-doc/           gradle/
requirements/            gradle/  annotations/  (three gates + @Requirement)
layer-disjointness/      gradle/
test-layers/             gradle/
suppression-register/    gradle/  annotations/  (gate + @RegisteredSuppression)
criticality/             gradle/  annotations/  (gate + @Criticality)
feature-slicing/         gradle/  claude/       (two gates + skill + hook)
```

Two more top-level directories hold no subject-matter content of their own,
only build definitions that assemble the above into publishable artifacts:

- **`gradle-plugin/`** — `settings.gradle.kts`, `build.gradle.kts`, the
  wrapper, and `src/main/java/.../internal/` (the parsing/reflection helpers
  shared across gates — belongs to no single subject, so it lives with the
  build that shares it). Its `sourceSets` pull each gate's actual Java
  sources from the directories above.
- **`annotations/`** — the same, for `de.fourteen.gates:annotations`; pulls
  `Requirement.java` from `requirements/annotations/`,
  `RegisteredSuppression.java` from `suppression-register/annotations/` and
  `Criticality.java` from `criticality/annotations/`.

Why one Gradle module reaching across directories, rather than N independent
builds matching the layout 1:1: applying `de.fourteen.gates.structuredoc`
already doesn't pull in `suppressionRegister`'s task or extension (checked
with a real, separate build in `PluginIdsFunctionalTest` — different plugin
ids from the same jar stay fully independent at the point that matters, what
a consumer's build sees). Splitting the build itself into one per plugin id would only
multiply wrapper/CI/version bookkeeping for a distinction consumers can't
observe.

## What's inside

| Directory | Gradle | Claude Code |
|-----------|--------|-------------|
| `structure-doc/` | `structureDoc` gate | — |
| `requirements/` | `requirementsCoverage`, `taggedRequirementsCoverage`, `featureDocs` gates + `@Requirement` | — |
| `layer-disjointness/` | `layerDisjointness` gate | — |
| `test-layers/` | `testLayers` gate | — |
| `suppression-register/` | `suppressionRegister` gate + `@RegisteredSuppression` | — |
| `criticality/` | `criticality` gate + `@Criticality` | — |
| `commit-discipline/` | `installGitHooks` (commit-msg format + release/paths) | `release-impact` skill, `main-branch-rule.sh`, `finish-the-work.sh`, `watch-pipeline.sh` hooks |
| `open-decisions/` | — | `open-decisions` skill, `session-start.sh` hook |
| `adr/` | — | `adr` skill |
| `staged-verification/` | — | `staged-verification` skill |
| `feature-slicing/` | `sliceStructure`, `sliceCoverage` gates | `feature-slicing` skill, `slice-structure-check.sh` hook |
| `idea-clarification/` | — | `idea-clarification` skill |
| `tdd-implementation/` | — | `tdd-implementation` skill |
| `acceptance/` | — | `acceptance` skill, `acceptance-gate.sh` hook |
| `demo-feedback/` | — | `demo-feedback` skill |
| `feature-delivery/` | — | `feature-delivery` skill (orchestrates the five above) |

`requirements/` and `commit-discipline/` are the only directories with more
than one thing inside, and each time for a concrete, checked reason, not
convenience:

- `requirementsCoverage`, `taggedRequirementsCoverage` and `featureDocs`
  read the same requirements register through the same parser — splitting
  them further would mean duplicating that parser instead of sharing it.
- `commit-discipline` groups everything about how a commit gets made and
  what it triggers: format and release consequence (`installGitHooks`'s
  git-native `commit-msg` hook, works no matter what tool commits),
  branch/timing (`main-branch-rule.sh`), follow-through
  (`finish-the-work.sh` — a finished commit left sitting locally isn't
  delivered — and `watch-pipeline.sh`, which reports back once the push's
  CI run finishes), and the judgment half (`release-impact`: does the type
  match the change?). Several technologies, one subject: what a commit is,
  and what happens because of it.

Every other pairing was checked and found *not* load-bearing before being
kept separate: `open-decisions` names `adr` as the recommended way to write
one, worded so it degrades gracefully if `adr` isn't installed — the only
cross-reference among the four skills, and cheap enough to not force a
bundle over it. `staged-verification` and `adr` reference nothing else.

## Gradle gates

Ten gates, eight plugin ids, zero runtime dependencies beyond the JDK and the
Gradle API — every gate here parses plain text, JUnit/JaCoCo XML (the JDK's
own `javax.xml.parsers`) or reflects over compiled classes.

| Plugin id | Extension | Task(s) | Checks |
|-----------|-----------|---------|--------|
| `de.fourteen.gates.structuredoc` | `structureDoc {}` | `structureDoc` | An architecture doc names no file that doesn't exist in the project, and mentions every domain type |
| `de.fourteen.gates.requirements` | `requirements {}` | `requirementsCoverage` | Every requirement in a category is claimed by an annotated test method that actually passed |
| | | `taggedRequirementsCoverage` | Every requirement in a category is tagged somewhere in source (for stacks where "which tests passed" isn't uniformly readable, e.g. a separate frontend) |
| | | `featureDocs` | Every feature doc follows the template, states exactly one criticality, stays under the acceptance-criteria limit, and references only real requirement IDs |
| `de.fourteen.gates.layerdisjointness` | `layerDisjointness {}` | `layerDisjointness` | No line of domain code is covered only by an outer-layer test — a gap further in isn't credited to the outer layer |
| `de.fourteen.gates.testlayers` | `testLayers {}` | `testLayers` | Every test method belongs to exactly one layer, and no named layer is empty — the assumption a tag-selected split silently rests on |
| `de.fourteen.gates.suppressionregister` | `suppressionRegister {}` | `suppressionRegister` | Every test/mutation suppression in code has a matching, dated entry in a register, and vice versa |
| `de.fourteen.gates.criticality` | `criticality {}` | `criticality` | Every criticality recorded in the code names requirement IDs that exist, and no level a build derives from is empty |
| `de.fourteen.gates.featureslicing` | `featureSlicing {}` | `sliceStructure` | A feature's slice tree (see the `feature-slicing` skill) is numbered contiguously, each slice's status agrees with its actual folder structure, and each states a non-empty user-outcome statement |
| | | `sliceCoverage` | Every leaf slice (no children of its own) is claimed by an annotated test method that actually passed — `requirementsCoverage`'s guarantee, pushed down to slice granularity |
| `de.fourteen.gates.githooks` | — | `installGitHooks` | (not a gate — see below) |

Each gate checks against a **fixed file convention**, documented below —
adopting a gate means adopting that convention, not configuring a format to
match whatever you already have. What's configurable is only what's
inevitably project-specific: file paths, a package prefix, an annotation's
fully qualified name.

Applying a plugin doesn't force its gate(s) on you: each task only attaches
to `check` once its own required property is actually set (`domainModelDir`,
`requirementsFile`, `exceptionsRegisterFile`, `layers`, ...) — apply
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

`requirementsCoverage`, `suppressionRegister` and `criticality` need a real
annotation on the JVM classpath — not just a string naming one — so this
repo ships three, as one small, dependency-free artifact
(`de.fourteen.gates:annotations`) separate from the Gradle plugin (a
project's *test code* needs this on its compile/test classpath; the Gradle
plugin itself never does).

| Annotation | Read by |
|------------|---------|
| `@Requirement("4.2")` | `requirementsCoverage` — put on a test method |
| `@RegisteredSuppression` | `suppressionRegister` — put on a suppressed class or method |
| `@Criticality(level = HIGH, requirements = "4.2")` | `criticality` — put on a production class or method |

Each gate defaults to its own (`suppressionRegister` also checks
`org.junit.jupiter.api.Disabled` by default, no dependency needed for that
one). Add the dependency and you're done — no extension configuration needed
unless you'd rather use an annotation you already have. `testLayers` needs no
annotation of this kind: it reads the tags your test framework already
understands.

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
    id("de.fourteen.gates.testlayers") version "<tag>"
    id("de.fourteen.gates.suppressionregister") version "<tag>"
    id("de.fourteen.gates.criticality") version "<tag>"
    id("de.fourteen.gates.featureslicing") version "<tag>"
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

testLayers {
    // No default: which layers exist is the one thing the gate can't guess.
    layers.set(listOf("unit", "port", "adapter", "api"))
}

suppressionRegister {
    exceptionsRegisterFile.set(layout.projectDirectory.file("docs/test-exceptions.md"))
    // suppressionAnnotationFqns defaults to [RegisteredSuppression, org.junit.jupiter.api.Disabled]
}

criticality {
    requirementsFile.set(layout.projectDirectory.file("docs/requirements.md"))
    // criticalityAnnotationFqn defaults to de.fourteen.gates.annotations.Criticality
    // allowedLevels defaults to [LOW, MEDIUM, HIGH]
    // levelsThatMustNotBeEmpty defaults to [HIGH]
}

// What the criticality recorded in the code is worth beyond the gate: a target set
// derived from it, instead of a second list beside it that goes stale unnoticed.
pitest {
    targetClasses.set(criticality.classesAt("HIGH"))
}

featureSlicing {
    // featuresDir is deliberately the same directory requirements.featuresDir points at above --
    // a slice tree is rooted at the same feature docs, not a separate directory to configure twice.
    featuresDir.set(layout.projectDirectory.dir("docs/features"))
    // requirementAnnotationFqn defaults to de.fourteen.gates.annotations.Requirement, same as
    // requirementsCoverage -- a leaf slice's ID is claimed the exact same way a requirement's is
    // statusSectionName/statusLabel default to "Status"; allowedStatuses defaults to
    // ["needs splitting", "ready for implementation"]
    // userOutcomeSectionName defaults to "User Outcome" -- presence/non-emptiness checked only
    // acceptanceCriteriaSectionName defaults to "Acceptance Criteria";
    // unsplittabilityReviewThreshold defaults to 12 (a notice, not a failure, past this count)
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

**`criticality`** reads the level off the production code it applies to,
together with the requirement IDs that justify it — the IDs are checked
against the same requirements register as above:

```java
import de.fourteen.gates.annotations.Criticality;

@Criticality(level = Criticality.Level.HIGH, requirements = {"4.2", "7.1"})
public class Settlement { ... }
```

It reads `level()` and `requirements()` off whatever annotation
`criticalityAnnotationFqn` names, so an annotation a project already has
works as long as it offers those two. A class-level and a method-level
annotation are both read; a method-level one is how one class can serve
features of different criticality.

Beyond the gate, `criticality.classesAt("HIGH")` hands the annotated classes
to whatever should act on them (mutation testing, in the example further up)
— derived from the annotation, so there is no second list to go stale. It
fails rather than returning an empty set, and `levelsThatMustNotBeEmpty`
makes the gate itself check the same thing: a target set that silently
becomes empty turns the work it feeds into a pass against nothing, which
reads exactly like a real run.

**`testLayers`** needs no annotation of its own. It reads the tags the test
framework already uses to select each layer, through meta-annotations, so a
project's own layer annotation is seen as the tag it carries:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Tag("unit")
public @interface UnitTest { }
```

With `layers.set(listOf("unit", "port", "adapter", "api"))` it then rejects a
test method carrying none of those tags (it runs in no task, and a build that
never ran it looks exactly like a green one), one carrying two (it runs twice,
and its coverage lands in two execution-data files — which quietly falsifies
`layerDisjointness` above), and a named layer with no test at all.

The gate checks the discipline; it doesn't register the per-layer test tasks
for you. Those stay in your build, where the tag filters, the
`shouldRunAfter` ordering and the JaCoCo report each layer needs are already
spelled out — generating them would mean dictating task names to a build that
already has its own:

```kotlin
tasks.named<Test>("test") { useJUnitPlatform { includeTags("unit", "port") } }
val adapterTest = tasks.register<Test>("adapterTest") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("adapter") }
}
```

**`sliceStructure` / `sliceCoverage`** read a slice tree rooted at each
top-level feature doc under `featuresDir` (see the `feature-slicing` skill):
a slice doc `N.md` may have a same-named sibling directory `N/` holding its
own numbered children, recursively:

```
docs/features/
  4.2.md
  4.2/
    1.md    <- ## Status \n\n **Status:** needs splitting
    1/
      1.md  <- ## Status \n\n **Status:** ready for implementation
      2.md  <- ## Status \n\n **Status:** ready for implementation
    2.md    <- ## Status \n\n **Status:** ready for implementation
```

`sliceStructure` requires numbering under each directory to be contiguous
from 1 (no gaps, no duplicates), and requires each slice doc's `Status`
section to state exactly one of `allowedStatuses` — `needsSplittingStatus`
("needs splitting" by default) only where a child directory actually exists,
any other status (e.g. "ready for implementation") only where none does.

It also requires a non-empty `userOutcomeSectionName` section ("User
Outcome" by default) on every slice — a one-line statement of what a user
(or another system) can do once that slice is implemented that they
couldn't before:

```
## User Outcome

A player can enter a room code and join the room.
```

Only the section's *presence* is checked, never its content — whether the
statement actually holds up (and so whether the slice is really vertical)
stays a human/skill judgment call; see the `feature-slicing` skill for why
this statement is the intended proxy for that judgment.

Separately, `sliceStructure` counts each slice's numbered `Acceptance
Criteria` lines (same convention `featureDocs` uses) and, past
`unsplittabilityReviewThreshold` (12 by default), adds a *notice* to its
report rather than failing — a slice with that many criteria isn't
necessarily still splittable, but it's worth a second look, and neither
this gate nor any other in this repo can check unsplittability directly.

`sliceCoverage` then requires every *leaf* slice (one with no children of
its own — `4.2.1.1`, `4.2.1.2` and `4.2.2` above, not `4.2.1`) to be claimed
by a passed test annotated with its own ID, the same marker annotation
`requirementsCoverage` uses:

```java
import de.fourteen.gates.annotations.Requirement;

@Test
@Requirement("4.2.1.1")
void aPlayerCanEnterARoomCode() { ... }
```

A feature that hasn't been sliced at all yet (no directory next to its
`.md` file) needs no leaf-slice coverage — it's still covered, if at all, by
`requirementsCoverage` at the requirement-ID level.

### Consuming these plugins

This repository doesn't publish to the Gradle Plugin Portal (yet — see
[Status](#status)). All eight plugin ids come from the same `gradle-plugin/`
build, so one dependency resolution covers all seven. Until published:

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

Copies a Conventional-Commits checker into `.git/hooks/commit-msg`. It always
checks *format* (does the subject line parse as `type(scope): ...` with an
Angular-preset type). Name your application paths and it also rejects the
mirror-image mistake — a releasing type whose staged files can't possibly
ship anything:

```
git config gates.appPaths '^(src/main/|frontend/src/|build\.gradle\.kts$|Dockerfile$)'
git config gates.releasingTypes 'feat|fix|perf'      # optional, this is the default
```

A `feat:` on a docs-only change then fails before the commit exists, instead
of triggering a release and a deploy that change nothing about the running
application. `git config` rather than an environment variable because the
hook also runs from an IDE or a GUI client, where an exported shell variable
isn't there; `GATES_APP_PATHS` and `GATES_RELEASING_TYPES` are read as a
fallback. Leave both unset and only the format is checked, as before.

What stays a judgment call, and what the `release-impact` skill is for: a
commit that *does* touch an application path, where the question is whether
the change deserves a release at all.

## Using the Claude Code plugins

Point Claude Code at one of `adr/claude/`, `open-decisions/claude/`,
`staged-verification/claude/`, `commit-discipline/claude/`,
`feature-slicing/claude/`, `idea-clarification/claude/`,
`tdd-implementation/claude/`, `acceptance/claude/`, `demo-feedback/claude/`
or `feature-delivery/claude/` as a plugin directory (locally, or once
published, via a marketplace) to get that directory's skill — install as
many or as few as you want.

| Hook | Lives at | Event | Does |
|------|----------|-------|------|
| `main-branch-rule.sh` | `commit-discipline/claude/hooks/` | `PreToolUse` (Bash) | Blocks creating a new branch/worktree; blocks a commit whose branch is behind its upstream |
| `finish-the-work.sh` | `commit-discipline/claude/hooks/` | `Stop` | At the end of an answer: an unclean working tree, or commits sitting ahead of the upstream, block the stop once — finish it, commit it, push it |
| `watch-pipeline.sh` | `commit-discipline/claude/hooks/` | `PostToolUse` (Bash, async) | After a push, watches that commit's GitHub Actions runs and reports back exactly once, green or red, naming the failing step |
| `session-start.sh` | `open-decisions/claude/hooks/` | `SessionStart` | Surfaces working-tree state and any open decisions at the start of a session |
| `acceptance-gate.sh` | `acceptance/claude/hooks/` | `PreToolUse` (Bash) | Blocks a feat/fix commit that stages a featuresDir doc without that doc's "Accepted: yes" line |
| `slice-structure-check.sh` | `feature-slicing/claude/skills/feature-slicing/scripts/` | `Stop` | A local, JVM-free duplicate of the `sliceStructure` gate's structural checks (numbering, Status, User Outcome) — see below |

Installing a directory as a Claude Code plugin (as described above) wires
its hooks automatically — each one ships a `hooks/hooks.json` at its plugin
root, Claude Code's own default location for a plugin's hooks, loaded and
activated the moment the plugin is enabled. No `.claude/settings.json`
edit needed.

`slice-structure-check.sh` is wired differently from the rest: it's declared
in the `feature-slicing` skill's own SKILL.md frontmatter rather than in a
plugin-wide `hooks/hooks.json`, so it only activates for a session that
actually invoked that skill, and never for a session that didn't. That
makes the `feature-slicing` skill (plus this structural check) usable on
its own — by a domain expert who never runs `./gradlew check` — without
needing the `de.fourteen.gates.featureslicing` Gradle plugin at all. It's a
deliberate duplicate of `sliceStructure`'s logic, not a replacement: where
the Gradle gate is applied, that stays the authoritative check, and this
hook is only an early, local echo of it that can drift from it over time.

If you'd rather copy a script directly into your own project instead of
installing the plugin, each directory's `settings.snippet.json` (in the
same `claude/` directory) shows the equivalent manual wiring for
`.claude/settings.json`. Either way, the hooks read their configurable
values from environment variables (`GATES_MAIN_BRANCH`,
`GATES_VERIFY_COMMAND`, `GATES_OPEN_DECISIONS_FILE`, `GATES_FEATURES_DIR`)
with sensible defaults, rather than from a config file. `watch-pipeline.sh`
needs no configuration, but it does need `curl`, `jq` and a GitHub `origin`;
`GH_TOKEN`/`GITHUB_TOKEN` lifts the unauthenticated rate limit.
`finish-the-work.sh` is the one hook here that deliberately interrupts: wire
it only where finished work is supposed to reach the remote by itself.

## Development process

The gates and skills above aren't independent tools bolted together; they're
meant to support one flow, from a raw feature idea to a shipped, verified
feature. `feature-delivery` is the recommended entry point for actually
running it — it chains the five steps below on its own, launching each as
its own subagent rather than inline, so that a customer only ever sees two
of them (idea clarification, and each round of demo and feedback), and so
that its own context doesn't fill up with the slice-tree/code/gate-output
detail those subagents produce along the way. The five steps themselves:

1. **Idea clarification** — before anything is cut into slices, the
   `idea-clarification` skill interrogates the idea itself, one question at
   a time: target user, scope boundary, success criterion, assumptions and
   risks, and criticality, then keeps asking past that fixed minimum as
   long as it can point to a genuine gap. Its job is to surface gaps while
   they're still cheap to close, not after a slice has already been cut
   around a wrong assumption. It then writes the resulting top-level
   feature doc itself (assigning the next free requirements-register ID)
   rather than leaving the clarified answers to only exist in the
   conversation that produced them — see the skill for why.

2. **Vertical slicing** — the clarified idea is cut into small, atomic,
   independently shippable vertical slices — each one a complete path
   through the system, not a horizontal layer. The `feature-slicing` skill
   and its companion `sliceStructure`/`sliceCoverage` gates (see
   [What's inside](#whats-inside)) do this today:

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
     (step 3), accepted (step 4), and demoed for feedback (step 5) — before
     the next sibling is even looked at. That's not just sequencing: step 5
     is exactly what makes waiting worthwhile, since it can still edit or
     reorder those untouched siblings, or insert a new one between them,
     while they're still just docs.
   - Every slice, leaf or not, states a **User Outcome**: a one-line
     statement of what a user (or another system) can now do that they
     couldn't before. Writing it is the skill's proxy for judging
     verticality — a cut where no honest, specific statement can be written
     probably isn't vertical.
   - **Open point:** how "vertical" gets checked deterministically isn't
     fully decided yet. `sliceStructure` checks that the `User Outcome`
     section exists and isn't empty, but never whether its *content* holds
     up — a hand-waved or generic statement passes the same as a specific
     one. Whether the cut actually is vertical stays the skill's (or a
     human's) judgment call.
   - **Open point:** how "unsplittable" gets checked deterministically isn't
     decided either. `sliceStructure` verifies that a slice's `Status`
     matches what's on disk (no children where "ready", children where
     "needs splitting"), but that only checks the status *label* is
     consistent — it never verifies that a slice marked "ready" actually
     *is* unsplittable. It does flag slices past a configurable acceptance-
     criteria count as worth a second look (a notice, not a failure), but
     that's a heuristic prompt, not proof: a slice far too large to
     implement in one sitting, mislabeled "ready" and just under the
     threshold, still passes the gate exactly the same as a genuinely
     minimal one.

   The `sliceStructure` and `sliceCoverage` gates check what *can* already
   be checked deterministically without solving either open point above:

   - `sliceStructure` — the slice folder/numbering scheme is consistent (no
     gaps, each slice's number matches its position under its parent), and
     each slice doc's status ("needs splitting" vs. "ready for
     implementation") matches what the folder structure actually shows — a
     "ready" slice with child slices already underneath it, or a "needs
     splitting" slice with none, is a contradiction the gate rejects;
   - `sliceCoverage` — `requirementsCoverage`'s check extended down to slice
     granularity: a leaf slice (no children, "ready for implementation")
     needs a passing test that references *that slice's* ID, not just the
     top-level requirement's — the same no-gap guarantee `requirementsCoverage`
     already gives at the requirement level, pushed one level further down
     so a slice can't quietly stay unimplemented once its parent requirement
     shows covered.

   See [Gradle gates](#gradle-gates) for the exact convention both check
   against.

3. **TDD implementation** — the `tdd-implementation` skill finds the first
   open leaf slice (lowest-numbered, `ready for implementation`, not yet
   covered per `sliceCoverage`) and drives it through red/green/refactor,
   one `Acceptance Criteria` line at a time — not all tests up front, not
   the whole slice in one pass. `staged-verification` is the supporting
   check-order this loop runs after every green and refactor step (compile,
   then unit/domain tests, then architecture tests, then the full check,
   stopping at the first red stage); it doesn't drive the test-first cycle
   itself, `tdd-implementation` does. A slice counts as done only once
   every criterion has a passing, `@Requirement`-annotated test *and*
   `sliceStructure`/`sliceCoverage` actually pass — not just once tests are
   locally green. The skill then updates the slice doc's `Implemented In`
   field and stops; handing off to the next sibling slice or to acceptance
   is left to whoever invoked it.

4. **Acceptance against the original description** — the `acceptance` skill
   checks the finished slice back against what was actually asked for, not
   only against the tests written for it: does it match the doc's
   `Motivation`, `Acceptance Criteria`, `Scenarios` and `User Outcome`, or
   did the implementation quietly narrow, widen or reinterpret them along
   the way. `featureDocs`/`sliceStructure` are different, narrower checks —
   they only enforce that a doc follows the required structure, never that
   the implementation matches what it says. This step is triggered by its
   companion `acceptance-gate.sh` hook, which blocks a `fix`/`feat` commit
   (the two Conventional-Commits types that actually change behavior) from
   going through at all while a staged featuresDir doc lacks the skill's
   `**Accepted:** yes` line — reusing the same commit-type signal
   `installGitHooks`'s `commit-msg` hook already parses, but enforced
   *before* the commit rather than only observed after. Like every other
   structural check here, the hook confirms the marker's presence, never
   that the judgment behind it was done honestly — that stays the skill's.

5. **Demo and feedback** — right after acceptance, the `demo-feedback`
   skill launches the app and drives it through the slice's own scenarios
   live, in a real browser, then asks targeted follow-up questions: does
   the real thing match the `User Outcome` as written, does anything about
   the *next* planned slices need to change now that this one is real, did
   anything unanticipated surface. Whatever comes back is folded straight
   into the docs of sibling slices that haven't been split further yet —
   edited in place, or inserted as a brand-new sibling between existing
   ones — never into a slice already implemented. This is the actual reason
   step 2 only ever looks at the first slice at any level: the untouched
   siblings are exactly where this feedback still has somewhere cheap to
   land. Only once no untouched sibling exists anywhere does the feedback
   fall back to the top-level feature doc's `Open Questions`.

All five steps exist today: their skills, step 2's two gates, and step 4's
companion hook.

## Status

This is an early, first-pass version of this layout, done in one pass —
including the directory structure itself, reorganized once already (by
subject matter first, technology second) after the initial technology-first
cut turned out to obscure which pieces actually belonged together. This has
not yet been battle-tested across multiple consuming projects, so expect
the extensions' property names and the exact file conventions to still move
a little as a second and third consuming project exercise them. Semantic
versioning starts in earnest once there's evidence from more than one
consuming project that the shape is right — the `annotations` artifact
especially, since `@Requirement`/`@RegisteredSuppression`/`@Criticality` end
up scattered across a consuming project's code, more expensive to change
later than a Gradle property name.

The second extraction pass from that same origin project added `criticality`,
`testLayers`, the commit-msg hook's application-path check and the two
further Claude Code hooks. `criticality` and `testLayers` are the two gates
here that were generalized rather than lifted: in the origin project the
first is an ArchUnit rule plus a build-script provider, and the second didn't
exist as a check at all — the failure it catches (a test in no layer, silently
never run) had been found there by hand.

Six more Claude Code skills are now included, completing the whole
development-process chain end to end: `idea-clarification`, which
interviews a raw feature idea and writes the resulting top-level feature
doc; `feature-slicing`, which turns that doc into vertically-sliced,
independently shippable pieces of work, along with its companion
`sliceStructure`/`sliceCoverage` Gradle gates; `tdd-implementation`, which
drives one leaf slice through red/green/refactor, one acceptance criterion
at a time; `acceptance`, which checks the finished slice back against its
doc and, via its companion `acceptance-gate.sh` hook, blocks a `fix`/`feat`
commit until that check has actually been done; `demo-feedback`, which
demos the accepted slice live and folds what comes back into the sibling
slices no one has cut further yet — the actual reason `feature-slicing`
only ever looks at the first slice at any level; and `feature-delivery`,
which chains all five of the above into the one loop a customer actually
interacts with — idea clarification once, then a demo-and-feedback round
per finished slice, nothing else surfaced to them, and every step run as
its own subagent so none of their internal detail lingers in
`feature-delivery`'s own context either. See
[Development process](#development-process) for where all six fit.

## License

Apache 2.0 — see [LICENSE](LICENSE). Free to use, modify, redistribute and
build on, commercially included.
