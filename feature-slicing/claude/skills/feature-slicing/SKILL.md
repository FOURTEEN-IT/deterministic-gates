---
name: feature-slicing
description: Use when a feature doc under featuresDir needs to be cut into small, atomic, vertical slices before implementation starts, or when the first not-yet-implemented slice under an existing feature needs to be re-examined for size and verticality. Not for writing the top-level feature doc itself (that's the idea-clarification skill) and not for implementing a slice once it's small enough (that's the TDD-implementation skill).
hooks:
  Stop:
    - hooks:
        - type: command
          command: "\"${CLAUDE_SKILL_DIR}\"/scripts/slice-structure-check.sh"
          timeout: 30
          statusMessage: "Checking slice structure"
---

# Feature Slicing

A feature doc under `featuresDir` (see the `requirements` Gradle plugin's
`featureDocs` gate) describes a whole feature — everything it must do. That's
the right size for describing an idea, and the wrong size for implementing
one in a single pass. This skill cuts a feature, one level at a time, down
to slices small enough to implement test-first in one sitting, each one a
complete vertical path through the system rather than a horizontal layer
(only the UI, only a migration, only a domain type with nothing wired to
it).

Prerequisite: the feature doc already exists, in full, written by the
idea-clarification skill (or by hand). This skill never originates a
feature — it only cuts one that's already been described.

## Layout

A feature lives at `featuresDir/<id>.md` as today (e.g. `4.2.md` for
requirement `4.2`). Slicing it creates a same-named directory next to that
file, one level per split, each slice numbered from its parent:

```
docs/features/
  4.2.md              <- the feature doc, unchanged
  4.2/
    1.md               <- first slice
    2.md               <- second slice (untouched until slice 1 is done)
    1/
      1.md              <- slice 4.2.1 split further: 4.2.1.1
      2.md              <- 4.2.1.2, untouched until 4.2.1.1 is done
```

A slice's own ID is its path from the feature (`4.2.1`, `4.2.1.2`, ...). A
slice doc uses the same headings as a feature doc (`Motivation`,
`Affected Requirements`, `Acceptance Criteria`, `Scenarios`, `Criticality`,
`Implemented In`, `Open Questions`), scoped down to just that slice, plus two
more fields:

- **Status**, either `needs splitting` or `ready for implementation`.
- **User Outcome**, a one-line statement of what a user (or another system)
  can do once this slice is implemented that they couldn't before —
  required on *every* slice, whether it's a leaf or gets split further. If
  you can't write this statement so it actually makes sense, that's the
  signal the cut you're looking at isn't vertical: go back to step 2.

## Steps

Always work on the lowest-numbered slice at the deepest level that hasn't
reached "ready for implementation" yet — start at the feature itself if it
has no slices under it yet, otherwise descend into slice `1`, then that
slice's own `1`, and so on, until you reach a slice with no children.

1. **Read that slice (or the feature, at the top) in full.** Its current
   `Acceptance Criteria` and `Scenarios` are what gets cut up next.

2. **Ask: is a further cut still vertical, and would it still be
   unsplittable?** A cut is vertical if the resulting pieces each still
   describe a complete, independently observable path through the system
   (something a user or another system could notice working end-to-end),
   not a layer of one. If every way you can think of to cut this slice
   further would produce a horizontal piece instead (a piece that does
   nothing observable on its own — a schema change with nothing reading it
   yet, a UI with nothing behind it), the slice is done: set its status to
   `ready for implementation` and stop — this is a leaf, and it hands off to
   the TDD-implementation skill next.

   Write this slice's `User Outcome` statement now, before deciding whether
   it needs splitting further. If you find yourself unable to state, in one
   sentence, what a user can now do — without hedging, without describing an
   internal mechanism instead of an observable outcome — that's evidence the
   cut isn't vertical yet, whatever level you're at.

   **Open point:** neither "vertical" nor "unsplittable" (small enough that
   no further vertical cut is worth making) is checked deterministically
   anywhere in this repo yet. The `User Outcome` statement's *presence* is
   checked by `sliceStructure`; whether its *content* actually holds up is
   still this skill's (or a human's) judgment call — see "Relationship to
   the companion gates" below.

3. **If a vertical cut is still possible, cut it.** Split the current
   slice's `Acceptance Criteria`/`Scenarios` into the smallest number of
   vertical pieces that still cover everything the slice described — two is
   usually enough; more only when the slice genuinely bundles that many
   independent paths. Write each piece as its own doc, one folder level
   deeper, numbered in the order they should be implemented. Set the
   slice that was just split to `needs splitting` (it no longer stands on
   its own — its children do).

4. **Recurse into the first new slice.** Go back to step 1 with slice `1`
   at the new, deeper level. Never look at slice `2`, `3`, ... at any level
   until slice `1` at that level has been split down to a leaf, implemented,
   accepted, and demoed (see the TDD-implementation, acceptance, and
   `demo-feedback` steps) — working on a sibling before that isn't cutting
   smaller, it's starting a second slice in parallel.

## Why only ever the first slice

Cutting every sibling up front before implementing any of them defeats the
point of slicing: the whole reason to slice is to get one small, complete,
shippable piece of value out the door before deciding exactly how to cut
the next one. Concretely, that "deciding" happens in `demo-feedback`: once
the first slice is built, accepted, and demoed live, whatever a real
reaction to it reveals gets folded straight into the untouched siblings —
edited, reordered, or a new one inserted between existing ones — while
they're still just docs and cost nothing to change. Slicing all siblings
eagerly, before any of that feedback exists, is just re-doing feature
decomposition on guesses, one level lower.

## Relationship to the companion gates

This skill also registers its own `Stop` hook
(`scripts/slice-structure-check.sh`, wired via this file's own frontmatter,
so it's only active for a session that actually invoked this skill) — a
deliberate duplicate of the `sliceStructure` gate's structural checks below,
so the slice tree gets checked even without Gradle in the loop, e.g. for a
domain expert running this skill on its own. Where the Gradle plugin is
applied, `sliceStructure` stays the authoritative check; this hook is only
an early, local echo of it and can drift from it over time.

The `de.fourteen.gates.featureslicing` Gradle plugin's `sliceStructure` and
`sliceCoverage` gates (see the deterministic-gates README) catch what
doesn't need judgment:

- the folder/numbering scheme staying consistent;
- a slice's `Status` matching what's actually on disk (no `ready for
  implementation` slice with children underneath it, no `needs splitting`
  slice without any);
- a slice's `User Outcome` section existing and being non-empty;
- `requirementsCoverage` extended down to leaf-slice IDs so a leaf can't
  stay silently unimplemented once its top-level requirement shows covered.

All four are structural or presence checks, not correctness ones — none of
them read what a section actually *says*. `sliceStructure` confirms the
`Status` *label* agrees with whether children exist on disk, never whether
"ready for implementation" was the right call; it confirms a `User Outcome`
statement exists, never whether it actually describes an observable,
end-to-end outcome (i.e. whether the cut is really *vertical*) rather than
empty hedging.

Two things stay genuinely open, checked by neither gate:

- **Verticality.** Writing the `User Outcome` statement is this skill's
  attempt at a deterministic-*enough* proxy — a cut where no honest,
  specific statement can be written is almost certainly not vertical — but
  `sliceStructure` only checks the statement exists, not that it holds up.
  Whether it actually does stays this skill's (or a reviewer's) judgment
  call.
- **Unsplittability.** `sliceStructure` also counts each slice's numbered
  `Acceptance Criteria` lines and, past `unsplittabilityReviewThreshold`
  (12 by default), adds a *notice* to its report — not a failure, since
  "too many criteria" doesn't prove a slice is actually splittable further,
  only that it's worth a second look. A slice under the threshold passes
  either way; nothing here proves a slice really is as small as it could
  be.

A slice far too large to implement in one sitting, mislabeled "ready" with
a hand-waved `User Outcome` and just under the criteria threshold, still
passes `sliceStructure` exactly the same as a genuinely minimal one — both
open points stay judgment calls until someone works out how to check them
more directly.
