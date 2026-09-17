---
name: release-impact
description: Use right before `git commit`, when deciding a commit's Conventional Commits type. Makes visible what a releasing type (feat/fix/perf, or whatever your release tooling watches) actually triggers, and whether the chosen type matches the change — a deploy decision, not just a label.
---

# Release Impact

If this project uses Conventional Commits together with an automated
release tool (semantic-release or similar) and a deploy pipeline wired to
that release, the commit type you type is not decoration — it decides
whether this change ships, and when.

`installGitHooks` (this plugin's git hook) checks that the type is *valid*
Conventional Commits format — not that it *matches* the actual change.
That gap is deliberately not automated (matching the change to the type
requires judgment: too many legitimate exceptions for a mechanical rule to
get right) — hence a skill here, not a gate.

## What each type typically triggers

Adjust this to whatever this project's release tooling actually does; the
Angular-preset defaults most semantic-release setups start from are:

- **`feat:` / `fix:` / `perf:`** → a release is cut, and if a deploy
  pipeline is wired to it, this goes live within minutes.
- **`docs:` / `chore:` / `style:` / `test:` / `refactor:` / `build:` /
  `ci:`** → no release, no deploy. Stays unreleased until the next
  releasing commit pulls it along.
- **`!` after the type, or `BREAKING CHANGE:` in the body** → a major
  version bump in addition to the release.

## Before committing

State the chosen type and its effect out loud, once, explicitly: "This is
`<type>:` — that means <ships immediately / stays unreleased>." Two
failure directions this catches:

- A pure documentation or tooling change typed as `fix:` — an unnecessary
  release/deploy for nothing that changed at runtime.
- A real behavior change typed as `chore:` or `refactor:` — stays
  wrongly unreleased when it should have shipped.

If the type doesn't match the change: split the commit or correct the
type. Don't leave a mismatched type in place "because it passes the
format check" — that check was never checking for this.
