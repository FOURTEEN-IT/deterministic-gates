---
name: open-decisions
description: Use when a question from the project's open-decisions register has actually been answered — by the user, by observation, or by conversation. Carries the answer through every place it needs to land, so it doesn't end up known only inside this chat.
---

# Open Decisions

Most non-trivial projects accumulate a register of not-yet-settled
questions — often something like `docs/open-decisions.md` — precisely so
that nobody quietly settles one in passing. The point of this skill is the
opposite failure mode: an answer that only exists in a conversation is a
decision in waiting, indistinguishable from one that was never made.

Find that register first (check the project's top-level docs or README if
you don't already know where it lives) before doing anything else.

## Steps

1. **Remove the entry from the open-decisions register.** If the question
   is being permanently ruled out rather than answered, move it to a
   "ruled out" section instead of deleting it outright — future you (or a
   future conversation) shouldn't have to wonder whether it was considered
   and rejected, or just never came up.

2. **Write an ADR** for a purely technical decision — use the separate `adr`
   plugin's skill if it's installed, otherwise record it however this
   project normally documents technical decisions. A decision with
   domain/business consequences and a technical one often needs both: a
   short ADR entry plus an update to wherever the project states its actual
   current behavior.

3. **Update the requirements/behavior doc**, if the decision changes what
   the system is supposed to do. Whatever document a project treats as
   "the current state of what we build" needs to move in lockstep with the
   register — a proposal document (if one exists for the feature in
   progress) is the *request*; the requirements doc is the *standing*
   truth, and the two must not diverge.

4. **Update the requirements register**, if this project uses the
   `requirementsCoverage`/`featureDocs` gates (from the separate
   deterministic-gates Gradle plugin) — a newly answered decision often
   creates or changes an ID that a gate now expects a test or feature doc
   to reference.

## Why all four

Any one of these left undone is a second truth quietly drifting out of
sync with the other three. The register exists so nothing gets decided by
accident; this skill exists so an actual decision doesn't stay accidental
by staying undocumented.
