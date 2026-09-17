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
commit-discipline/       gradle/  claude/       (git hook install task + skill + 3 hooks)
structure-doc/           gradle/
requirements/            gradle/  annotations/  (three gates + @Requirement)
layer-disjointness/      gradle/
test-layers/             gradle/
suppression-register/    gradle/  annotations/  (gate + @RegisteredSuppression)
criticality/             gradle/  annotations/  (gate + @Criticality)
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

Eight gates, seven plugin ids, zero runtime dependencies beyond the JDK and the
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

### Consuming these plugins

This repository doesn't publish to the Gradle Plugin Portal (yet — see
[Status](#status)). All seven plugin ids come from the same `gradle-plugin/`
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
`staged-verification/claude/` or `commit-discipline/claude/` as a plugin
directory (locally, or once published, via a marketplace) to get that
directory's skill — install as many or as few as you want.

To use a hook, copy its script into your project and wire it in
`.claude/settings.json`:

| Hook | Lives at | Event | Does |
|------|----------|-------|------|
| `main-branch-rule.sh` | `commit-discipline/claude/hooks/` | `PreToolUse` (Bash) | Blocks creating a new branch/worktree; blocks a commit whose branch is behind its upstream |
| `finish-the-work.sh` | `commit-discipline/claude/hooks/` | `Stop` | At the end of an answer: an unclean working tree, or commits sitting ahead of the upstream, block the stop once — finish it, commit it, push it |
| `watch-pipeline.sh` | `commit-discipline/claude/hooks/` | `PostToolUse` (Bash, async) | After a push, watches that commit's GitHub Actions runs and reports back exactly once, green or red, naming the failing step |
| `session-start.sh` | `open-decisions/claude/hooks/` | `SessionStart` | Surfaces working-tree state and any open decisions at the start of a session |

Each directory has its own `settings.snippet.json` (in the same `claude/`
directory) showing the exact wiring for all of its hooks. They read their
configurable values from environment variables (`GATES_MAIN_BRANCH`,
`GATES_VERIFY_COMMAND`, `GATES_OPEN_DECISIONS_FILE`) with sensible defaults,
rather than from a config file. `watch-pipeline.sh` needs no configuration,
but it does need `curl`, `jq` and a GitHub `origin`; `GH_TOKEN`/`GITHUB_TOKEN`
lifts the unauthenticated rate limit. `finish-the-work.sh` is the one hook
here that deliberately interrupts: wire it only where finished work is
supposed to reach the remote by itself.

## Status

This is a first extraction from a single origin project, done in one pass —
including this directory layout itself, reorganized once already (by
subject matter first, technology second) after the initial technology-first
cut turned out to obscure which pieces actually belonged together. Expect
the extensions' property names and the exact file conventions to still move
a little as a second and third consuming project exercise them. Semantic
versioning starts in earnest once there's evidence beyond the original
project that the shape is right — the `annotations` artifact especially,
since `@Requirement`/`@RegisteredSuppression`/`@Criticality` end up scattered
across a consuming project's code, more expensive to change later than a
Gradle property name.

The second extraction pass from that same origin project added `criticality`,
`testLayers`, the commit-msg hook's application-path check and the two
further Claude Code hooks. `criticality` and `testLayers` are the two gates
here that were generalized rather than lifted: in the origin project the
first is an ArchUnit rule plus a build-script provider, and the second didn't
exist as a check at all — the failure it catches (a test in no layer, silently
never run) had been found there by hand.

A fifth Claude Code skill — turning an idea into vertically-sliced,
independently shippable pieces of work — is planned but deliberately not
included yet; its design is still being worked out.

## License

Apache 2.0 with the Commons Clause — see [LICENSE](LICENSE). In short: free
to use, modify and redistribute, not to sell as a standalone product.
