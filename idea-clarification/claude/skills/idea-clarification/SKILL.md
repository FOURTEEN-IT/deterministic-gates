---
name: idea-clarification
description: Use when someone brings a raw feature idea before any feature doc exists for it — asks clarifying questions to surface gaps and unstated assumptions, then writes the resulting top-level feature doc and requirements-register entry. Not for a feature that already has a doc (that's the feature-slicing skill's job) and not for cutting a feature into slices.
---

# Idea Clarification

An idea, as first said out loud, is missing more than it states: who it's
actually for, what's deliberately out of scope, what "done" looks like,
what's being assumed without evidence. Writing a feature doc straight from
that raw idea just encodes the gaps into the doc — and everything downstream
(`feature-slicing`, TDD implementation, acceptance) inherits them. This
skill's only job is to close those gaps *before* the doc exists, then write
the doc itself.

Prerequisite: none — this is the first skill in the chain, the one that
originates a feature. Its output, a doc under `featuresDir`, is what the
`feature-slicing` skill later expects to already exist.

## The question round

Ask **one question at a time**, waiting for an answer before asking the
next — not a single upfront questionnaire. A raw idea rarely survives first
contact with a good follow-up unchanged; asking one at a time lets each
question build on what the previous answer actually revealed, instead of
guessing all the gaps in advance.

A fixed minimum has to be covered on every idea, in whatever order fits the
conversation:

1. **Target user** — who actually uses this, specifically enough that a
   scenario could be written about them.
2. **Scope boundary** — what's deliberately *not* included, stated as
   explicitly as what is. An idea with no stated boundary hasn't been
   thought through, it's just been named.
3. **Success criterion** — how anyone would know this worked, in terms
   observable from outside the system (not "the code is clean").
4. **Assumptions and risks** — what's being taken for granted that hasn't
   been verified (a dependency, a capacity limit, another team's behavior),
   and what could make this harder than it looks.
5. **Criticality** — one of the levels `featureDocs` accepts (LOW/MEDIUM/
   HIGH by default) — ask directly, don't infer it from tone.

Beyond that fixed minimum, keep asking as long as you can find a genuine
gap or unstated assumption in what's been said so far — there's no fixed
question count past the five above. Stop once a further question would be
fishing rather than closing an actual gap you can point to.

**Category** (`backend`, `frontend`, or whatever categories this project's
requirements register uses) is usually obvious from the conversation by the
time the round is done — don't ask it as its own question. Ask only if it
genuinely isn't clear which category applies.

## Writing the result

Once the round is done:

1. **Find the next free top-level ID.** Read the project's requirements
   register (`requirementsFile`, e.g. `docs/requirements.md`) and pick the
   next unused whole-number ID after the highest one already there (e.g.
   the register tops out at `4.x` → this feature becomes `5`). Don't reuse
   or renumber an existing ID, and don't invent a sub-number — a fresh
   top-level feature always gets a fresh whole number.

2. **Add a register row**: `| <id> | <one-line description> | <category> |`,
   appended to the existing table.

3. **Write the feature doc** at `featuresDir/<id>.md` — the filename's
   number must be exactly the register ID, no slug or prefix, since
   `feature-slicing` derives a slice's own ID from this filename. Fill
   every section `featureDocs` requires:
   - `Motivation` — why this is worth doing, from the target-user answer.
   - `Affected Requirements` — one row, this feature's own ID, reference
     type `new`.
   - `Acceptance Criteria` — numbered, derived from the success-criterion
     answer; state the scope boundary as what's explicitly excluded, not
     folded silently into the criteria.
   - `Scenarios` — at least one concrete Given/When/Then for the target
     user.
   - `Criticality` — the level from the question round.
   - `Implemented In` — "None yet" (nothing has been built; the
     TDD-implementation and acceptance steps fill this in later).
   - `Open Questions` — anything raised during the round that couldn't be
     resolved on the spot, so it isn't lost.

4. **Hand off.** The doc now exists; `feature-slicing` picks up from here.

## Why write the doc here rather than leave it to a later step

An answer that only exists in the conversation that produced it is exactly
the failure mode this skill exists to prevent — the same reasoning as the
`open-decisions` skill's "an answer known only in chat is a decision in
waiting." Closing the gaps and then not recording the closed state in the
one place a later skill will read from would just move the gap one step
downstream instead of removing it.
