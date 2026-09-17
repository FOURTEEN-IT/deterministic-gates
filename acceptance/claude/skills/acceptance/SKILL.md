---
name: acceptance
description: Use before a feat/fix commit that touches a feature or slice doc under featuresDir — compares the actual implementation against that doc's Motivation, Acceptance Criteria, Scenarios and User Outcome, and records the verdict in the doc itself. Not for judging whether a cut is vertical or small enough (feature-slicing) and not for driving the implementation (tdd-implementation) — only for checking a finished one against what was originally asked for.
---

# Acceptance

A passing, `@Requirement`-annotated test proves the code the test itself
describes works. It doesn't prove that code is what the feature or slice
doc actually asked for — a test can pass while quietly narrowing, widening,
or reinterpreting the original description along the way. This skill is
the check against the *description*, not against the tests written for it.

Prerequisite: `tdd-implementation` has finished a slice (every acceptance
criterion covered, `sliceStructure`/`sliceCoverage` green) and just updated
its `Implemented In` field — or, for a feature with no slices, the
equivalent point for `featureDocs`. That updated doc is this skill's input.

## What it checks

Read the doc (feature or slice) whose `Implemented In` field was just
updated, in full, then read the actual diff/code/tests behind it. For each
of the following, ask whether the implementation genuinely matches, not
just whether some code exists that's related to it:

- **Motivation** — does the change actually address the stated reason for
  doing this, or something adjacent to it?
- **Acceptance Criteria** — does each one hold, observably, not just
  "a test with a similar name passes"?
- **Scenarios** — does the concrete Given/When/Then actually happen when
  exercised, including the "when" and "then" as stated, not a simplified
  version of them?
- **User Outcome** (slices only) — can the user actually now do what the
  statement says, end to end, not just in the layer the changed code
  happens to sit in?

This is a judgment call, not a mechanical diff — the same way
`featureDocs` and `sliceStructure` check that a description's *structure*
is followed without ever reading whether its *content* is true. This skill
is what reads the content.

## Recording the verdict

Add (or update) an `## Acceptance` section in the doc, with a `**Accepted:**
yes` or `**Accepted:** no` line, the same label-line convention `Status` and
`Criticality` already use:

```
## Acceptance

**Accepted:** yes
```

- **Holds up:** write `**Accepted:** yes`. Nothing else is required, though
  a one-line note on what was checked doesn't hurt.
- **Doesn't hold up:** write `**Accepted:** no`, plus what's actually
  wrong — specific enough that whoever picks this up next (very possibly
  you, in the same session) doesn't have to redo the comparison from
  scratch. Add unresolved questions to the doc's own `Open Questions`
  section rather than only stating them in chat.

Only write `yes` when the implementation actually holds up. Writing it
regardless defeats the entire point of this skill — the companion
`acceptance-gate.sh` hook (see below) trusts this line exactly as much as a
person reviewing the doc would.

## The companion hook

`acceptance-gate.sh` (a `PreToolUse` hook on `Bash`) blocks a `feat`/`fix`
commit that stages a change to any doc under `featuresDir` unless that
doc's staged content already contains `**Accepted:** yes`. It's a
structural check, not a content one — exactly like `sliceStructure`'s
`Status` check, it confirms the marker is present, never that the
acceptance behind it was done honestly. Running this skill (and writing
`yes` truthfully) is what satisfies it; there's no way to satisfy the hook
without going through the judgment above.

A commit whose type is something other than `feat`/`fix` (e.g. `chore` or
`docs` for a feature-slicing-only commit that doesn't touch production
code) isn't gated at all — see the `commit-discipline` plugin's
`release-impact` skill for the separate judgment of whether a commit's type
actually matches what it does.

Configure the features directory the hook reads via `GATES_FEATURES_DIR`
(default `docs/features`), same convention as this repo's other hooks.
