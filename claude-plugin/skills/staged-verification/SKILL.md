---
name: staged-verification
description: Use when you want to know whether the current state holds — before a commit, after a larger change, or before blindly running the full build. Runs the cheapest, most localized checks first and stops at the first failure, instead of waiting minutes for a full run to fail somewhere in the middle.
---

# Staged Verification

No broader scope than running the full build/check command — just a
different order, so a mistake surfaces in seconds instead of after the
full, multi-minute run (mutation testing and all, if this project has it).

## The idea

Order your project's checks from cheapest/most-localized to most
expensive/broadest, and run them one stage at a time, stopping at the
first red one. A typical JVM project using this plugin's gates might look
like:

1. **Compile** — seconds, catches type errors and (if configured)
   null-safety violations. Red here means stop; nothing below this line is
   worth running yet.

2. **Unit/domain tests** — the fast, no-framework layer.

3. **Architecture/structure tests** — package-dependency rules, DDD
   stereotypes, whatever this project's `ArchUnit`-equivalent checks.

4. **Full check** — everything: integration/adapter tests, `structureDoc`,
   `requirementsCoverage`, `taggedRequirementsCoverage`,
   `layerDisjointness`, `featureDocs`, `suppressionRegister` (this
   plugin's gates), mutation testing, coverage reports.

Adapt the stage list to what this project actually has — the shape (cheap
and local first, expensive and broad last) is what matters, not this exact
four-step list.

## The rule

Only start a stage once the previous one is green. Don't run the full
check and then dig through a long, mixed failure list — most of what a
full run reports would already have been visible at stage 1 or 2.

On red: read that stage's own report before guessing from console output.
This plugin's gates each write a plain-text report under
`build/reports/gates/<gate-name>.txt` for exactly this purpose.
