---
name: demo-feedback
description: Use right after the acceptance skill marks a leaf slice "Accepted: yes" — demonstrates the just-implemented slice live in the browser, asks targeted follow-up questions, and folds what comes back into the docs of the sibling slices that haven't been split further yet. Not for the acceptance check itself (that's the acceptance skill) and not for cutting slices (that's feature-slicing) — this only feeds what was just learned back into slices no one has looked at yet.
---

# Demo & Feedback

A passing test and an `Accepted: yes` line prove the slice does what its
doc said. Neither one tells anyone how it actually feels to use, and
neither one gives a real person a chance to notice something the doc's
author couldn't have known before the thing existed. This skill is that
chance — and the reason `feature-slicing` only ever looks at the first
slice at any level: the untouched siblings are exactly where what gets
learned here still has somewhere cheap to land.

Prerequisite: `acceptance` has just written `**Accepted:** yes` on a leaf
slice. This skill runs before anyone (a human or `feature-slicing` itself)
touches that slice's next sibling.

## Demo

Launch the project and drive it through the slice's own `Scenarios` in a
real browser session — use this project's `run` skill (or whatever this
project actually uses to launch and exercise its app) if one is available,
rather than describing what the code does. The point is a real person
seeing the `User Outcome` statement actually hold, not a second reading of
the doc. Keep it to this one slice's scenarios; a demo that wanders into
adjacent, unimplemented slices sets an expectation nothing behind it can
meet yet.

## Feedback round

Ask **one targeted question at a time**, the same dialogue style
`idea-clarification` uses, not a single open "any thoughts?". Cover, in
whatever order fits the reaction to the demo:

1. **Match** — does what was just shown actually match the `User Outcome`
   statement as written, or did seeing it reveal a gap between the words
   and the real thing?
2. **Downstream effect** — now that this is real rather than planned, does
   anything about the *next* planned slices need to change — a criterion
   that no longer makes sense, a scenario that's now clearly missing, an
   assumption the demo just disproved?
3. **New, unanticipated need** — did the demo surface something worth
   building that nothing in the feature doc anticipated at all?

Keep asking past this minimum as long as the reaction to the demo keeps
surfacing something concrete; stop once further questions would be
fishing rather than following up on an actual reaction.

## Applying the feedback

Feedback only ever changes slices **nobody has touched yet** — the current
slice's own untouched siblings at its level, and, once those run out, the
parent slice's untouched siblings one level up, and so on. Never edit a
slice that's already `ready for implementation` with a passing
implementation, and never reopen the slice that was just demoed itself; if
feedback calls either into question, that's a decision for a human, not
something this skill resolves by rewriting settled work.

For each piece of feedback that does apply to an untouched sibling, use
whichever fits:

- **Edit the sibling's own doc** — update its `Acceptance Criteria`,
  `Scenarios`, `User Outcome`, or add to its `Open Questions`, when the
  feedback refines a slice that already exists.
- **Insert a new sibling slice**, renumbered into its place among the
  existing ones (its later siblings shift up by one), when the feedback
  describes something no existing sibling covers. This is safe precisely
  *because* siblings past the first are never touched before their turn —
  none of them can yet have a passing, annotated test referencing their
  old number, so renumbering them costs nothing no gate would catch anyway.

If no untouched sibling exists anywhere in the tree (this was the feature's
last slice), record the feedback in the top-level feature doc's
`Open Questions` instead — there's nothing left to insert it into, but it
still shouldn't only exist in this conversation.

## Why this, and not eager feedback on the whole plan up front

Feedback on a slice that doesn't exist yet is a guess about a guess. This
skill deliberately waits for something real to react to, then spends that
reaction on the smallest possible next decision — the sibling that hasn't
been cut yet — instead of trying to revise a whole feature's worth of
still-hypothetical slices at once.
