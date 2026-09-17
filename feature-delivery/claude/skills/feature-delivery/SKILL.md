---
name: feature-delivery
description: Use when a customer brings a raw feature idea and wants it delivered end to end — the single entry point that chains idea-clarification, then loops feature-slicing, tdd-implementation, acceptance and demo-feedback automatically until the whole feature is done. The customer only sees idea-clarification's questions and demo-feedback's demos/questions; every internal step runs without exposing its detail to them. Not a replacement for any of the five skills it calls — it only sequences them.
---

# Feature Delivery

A customer doesn't ask for a slice tree, a `sliceStructure` report, or a
red/green/refactor cycle. They ask for something, and then want to see
pieces of it work, one at a time, until it's done. Everything this repo's
other development-process skills do in between is real and necessary — it's
just not theirs to watch. This skill is the seam: it talks to the customer
exactly twice per cycle (once up front, once per finished slice) and runs
everything else itself.

Prerequisite: none to start — this is the entry point a customer's raw idea
comes in through. It calls the other five skills; it doesn't duplicate
their judgment.

## What the customer sees

Only two points of contact, repeated as needed:

1. **Once, at the start** — the `idea-clarification` question round, run
   exactly as that skill describes it. This is a real conversation, not a
   formality to rush through.
2. **Once per finished leaf slice** — the `demo-feedback` demo and its
   follow-up questions, run exactly as that skill describes them.

Between those points, say only what's needed to keep the customer oriented
("Building the next piece now — back with something to look at shortly"),
never the internal detail (slice IDs, gate output, test names, red/green
cycles). A customer who wants that detail can ask for it; don't offer it
unprompted.

## The loop

1. **Clarify.** Run `idea-clarification` on the customer's idea. This
   creates the top-level feature doc and its requirements-register entry.

2. **Advance the tree.** Run `feature-slicing` on that feature. It finds
   the next slice needing attention (the lowest-numbered one not yet
   `ready for implementation`) and cuts it, possibly recursively, until it
   either reaches a leaf or determines — see "When the feature is done"
   below — that nothing is left to cut anywhere in the tree.

3. **Done already?** Check the completion condition below. If it holds,
   skip to "When the feature is done."

4. **Implement.** Run `tdd-implementation`. It finds the first open leaf
   (there is one now, from step 2) and drives it to green,
   `sliceStructure`/`sliceCoverage`-passing completion.

5. **Accept.** Run `acceptance` on that leaf. If it comes back
   `**Accepted:** no`, fix what it found (return to step 4, or ask a human
   if the fix itself needs a decision) and re-run acceptance — silently,
   from the customer's side; this loop is not theirs to see. Only once it's
   `yes` does this step count as done.

6. **Demo and collect feedback.** Run `demo-feedback` on the now-accepted
   leaf — the customer-facing step. Whatever it folds into the untouched
   siblings (edited docs, a newly inserted sibling) is exactly what step 2
   picks up next time around.

7. **Repeat from step 2** for the next slice `feature-slicing` finds.

## When the feature is done

The loop ends when `feature-slicing`, asked to advance the tree, finds
nothing left to do: every node under the feature is either a `needs
splitting` slice with children, or a leaf that is both `ready for
implementation` *and* already carries `**Accepted:** yes` — i.e.
`sliceStructure` and `sliceCoverage` both pass clean for this feature's
whole tree, and no leaf is missing its acceptance marker (`sliceCoverage`
alone doesn't prove that — a leaf can have a passing test and still not
have gone through `acceptance`/`demo-feedback` yet, if the loop was
interrupted).

At that point, tell the customer the feature is complete — this is the one
other thing worth surfacing to them beyond the two contact points above.

## Why chain rather than let the customer drive each step

Asking a customer to decide when to invoke `feature-slicing` versus
`tdd-implementation` hands them a decision that was never theirs — those
steps have exactly one right order and no branching a customer could
usefully choose between. What they can usefully decide is what they think
of a working piece of the thing they asked for, which is exactly what
`idea-clarification` and `demo-feedback` already ask them, in full. This
skill's only job is making sure nothing else leaks into that conversation.
