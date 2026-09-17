---
name: adr
description: Use when a technical decision has been made — architecture, stack, library, data format — that should outlive the current change. Records it as an Architecture Decision Record with the next free number, in Context/Decision/Consequences form, and carries the back-reference forward.
---

# ADR

For every technical decision meant to outlive the current code change — not
every implementation-detail choice, but the kind someone will want to
understand in six months without reading the commit.

## Steps

1. **Find the ADR log.** Most projects keep one file (often
   `docs/adrs.md` or `docs/adr/`) or one file per decision under
   `docs/adr/NNNN-title.md` (the MADR convention). If you don't already know
   which this project uses, check its README or top-level docs before
   guessing.

2. **Determine the next number.** Find the highest existing `ADR-0NN` (or
   equivalent) and continue the sequence without gaps — don't hardcode a
   number, the log has grown since you last looked.

3. **Add the entry** to the log's overview (if it has one), e.g.:
   `| ADR-0NN | <decision, one sentence> | Accepted |`
   (or `Proposed`, if the decision is recommended but not yet confirmed —
   follow whatever status vocabulary the log already uses.)

4. **Write the section** in the format the file itself already establishes —
   typically Context → Decision → Consequences. Don't invent a new format;
   match the existing entries.

5. **Carry the back-reference forward** wherever the decision becomes
   visible in the build — a new file, a new directory, a new convention
   documented elsewhere (an architecture doc, a README section). A
   structure-doc gate (if this project uses one) only checks that named
   files exist and that domain types are mentioned — not that the
   *reasoning* is current. That part is still on you.

## What doesn't need an ADR

A decision already implied by an existing one, a pure refactor with no
externally visible consequence, or a choice so local it wouldn't survive
being generalized past this one function. When in doubt, ask: would a
future reader be confused without this record? If no, skip it.
