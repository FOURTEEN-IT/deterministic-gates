---
name: tdd-implementation
description: Use when a leaf slice (a feature-slicing slice doc with no children, status "ready for implementation") still has no passing, annotated test covering it — drives that one slice through red/green/refactor, acceptance criterion by acceptance criterion, until it's fully implemented. Not for deciding how to cut a feature into slices (that's feature-slicing) and not for checking the finished slice against its original description (that's the acceptance step).
---

# TDD Implementation

A slice small enough to implement in one sitting is still not implemented
by having a doc that says so. This skill takes exactly one leaf slice from
"described" to "built, tested, and known to be built" — nothing more,
nothing from any other slice.

Prerequisite: a leaf slice doc already exists with status `ready for
implementation` (`feature-slicing`'s output). This skill never decides
whether a cut is vertical or small enough — it only implements a cut
someone already made.

## Which slice

Find it the same way `feature-slicing` finds the next slice to examine:
walk the slice tree, always the lowest-numbered slice at the deepest level,
until reaching a leaf (`ready for implementation`, no children). Among
leaves, work on the first one that `sliceCoverage` would still flag as
open — no passing test yet carries `@Requirement("<its ID>")`. Never touch
a sibling leaf while this one is still open; that's `feature-slicing`'s
first-slice-only rule continued through implementation, not a new one.

## The cycle

Work through the slice doc's `Acceptance Criteria`, **one at a time, in
order** — not all tests up front, not all criteria implemented in one pass.
For each criterion:

1. **Red.** Write a failing test for this criterion. Annotate it (and any
   other test method you add for this same criterion) with
   `@Requirement("<slice ID>")` — the same marker `requirementsCoverage`
   reads, now claiming the slice's own ID instead of a top-level
   requirement's. One test is the minimum; write more for the same
   criterion when one test can't honestly cover it (an edge case belongs in
   its own test, not folded into the happy path's).

2. **Green.** Write the smallest amount of production code that makes the
   test pass. Resist implementing ahead for a criterion you haven't reached
   yet — that criterion gets its own red step, even if you can already see
   the shape of its implementation.

3. **Refactor.** Clean up now that the test is green, without changing
   behavior. Skip this step only if there's genuinely nothing to clean up,
   not because the code already works.

4. **Verify.** Run this project's `staged-verification` skill (or the
   equivalent staged check order if that skill isn't installed) after the
   green and after the refactor step — not just once at the end. Stop at
   the first red stage; go back and fix it before moving on, same as
   `staged-verification` itself prescribes. A criterion isn't done while
   any earlier stage is red, even one unrelated-looking.

5. Move to the next criterion and repeat from step 1.

## Finishing the slice

A slice is done, not just "tests pass locally," only once:

- every `Acceptance Criteria` line has at least one passing, annotated test
  behind it;
- `sliceStructure` and `sliceCoverage` both actually pass for this slice
  (run them, or the full `check`, don't infer it from the last green test) —
  a locally green test run that the gates haven't confirmed isn't finished.

Once both hold, update the slice doc's `Implemented In` section with the
test class(es)/method(s) that cover it, and stop. Handing the result to the
next step — the next sibling slice, or the acceptance-against-description
check — is a separate decision for whoever invoked this skill, not
something it does on its own.

## Why per-criterion, not per-slice

A slice is already the smallest independently shippable unit
`feature-slicing` could produce, but "smallest shippable" and "smallest
increment worth testing" aren't the same size. Writing every test for the
whole slice before implementing any of them reintroduces, one level down,
the exact problem slicing solved one level up: a batch of changes that's
either all working or all broken, with no smaller checkpoint in between.
