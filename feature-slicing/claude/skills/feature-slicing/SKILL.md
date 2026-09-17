---
name: feature-slicing
description: Use when a feature doc under featuresDir needs to be cut into small, atomic, vertical slices before implementation starts, or when the first not-yet-implemented slice under an existing feature needs to be re-examined for size and verticality. Not for writing the top-level feature doc itself (that's the idea-clarification skill) and not for implementing a slice once it's small enough (that's the TDD-implementation skill).
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
`Implemented In`, `Open Questions`), scoped down to just that slice, plus one
more field: **Status**, either `needs splitting` or `ready for
implementation`.

## Steps

Always work on the lowest-numbered slice at the deepest level that hasn't
reached "ready for implementation" yet — start at the feature itself if it
has no slices under it yet, otherwise descend into slice `1`, then that
slice's own `1`, and so on, until you reach a slice with no children.

1. **Read that slice (or the feature, at the top) in full.** Its current
   `Acceptance Criteria` and `Scenarios` are what gets cut up next.

2. **Ask: is a further cut still vertical?** A cut is vertical if the
   resulting pieces each still describe a complete, independently
   observable path through the system (something a user or another system
   could notice working end-to-end), not a layer of one. If every way you
   can think of to cut this slice further would produce a horizontal piece
   instead (a piece that does nothing observable on its own — a schema
   change with nothing reading it yet, a UI with nothing behind it), the
   slice is done: set its status to `ready for implementation` and stop —
   this is a leaf, and it hands off to the TDD-implementation skill next.

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
   until slice `1` at that level has been split down to a leaf,
   implemented, and accepted (see the TDD-implementation and
   acceptance-against-description steps) — working on a sibling before
   that isn't cutting smaller, it's starting a second slice in parallel.

## Why only ever the first slice

Cutting every sibling up front before implementing any of them defeats the
point of slicing: the whole reason to slice is to get one small, complete,
shippable piece of value out the door before deciding exactly how to cut
the next one — later slices are easier to cut correctly once the first one
has actually been built and something was learned from it. Slicing all
siblings eagerly is just re-doing feature decomposition, one level lower.

## Relationship to the planned gate

A companion Gradle gate (not built yet) is meant to catch what doesn't need
judgment: the folder/numbering scheme staying consistent, a slice's
`Status` matching what's actually on disk (no `ready for implementation`
slice with children underneath it, no `needs splitting` slice without any),
and `requirementsCoverage` extended down to leaf-slice IDs so a leaf can't
stay silently unimplemented once its top-level requirement shows covered.
Whether a given cut is actually *vertical* stays this skill's judgment call
until that can be checked deterministically too.
