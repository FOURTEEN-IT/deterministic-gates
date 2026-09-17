#!/usr/bin/env bash
# PreToolUse hook for Bash: enforces two workflow rules, as an executable rule
# instead of a reminder someone has to remember (a rule that depends on memory
# isn't a rule, it's an intention).
#
#   1. Work happens on one branch only (default "main") -- no feature branches.
#   2. Pull before every commit.
#
# Rule 2 isn't checked as a ritual ("did a git pull run before this?") but by
# its effect: if the local branch is behind its upstream, the commit is
# rejected. That's tamper-proof, survives a session switch, and catches
# exactly the damage the rule exists to prevent -- a commit on a stale branch
# that a release/deploy pipeline may be watching.
#
# Configure the protected branch name via GATES_MAIN_BRANCH (default "main").
# Reads the hook JSON on stdin, answers with a PreToolUse decision.

set -uo pipefail

main_branch="${GATES_MAIN_BRANCH:-main}"

input="$(cat)"
command="$(printf '%s' "$input" | jq -r '.tool_input.command // empty')"

[ -z "$command" ] && exit 0

deny() {
    jq -n --arg reason "$1" '{
        hookSpecificOutput: {
            hookEventName: "PreToolUse",
            permissionDecision: "deny",
            permissionDecisionReason: $reason
        }
    }'
    exit 0
}

# --- Rule 1: no creating branches ------------------------------------------
#
# Only the *creating* forms are caught. "git branch" without an argument
# lists, "git branch -d old" deletes, "git checkout <branch>" merely
# switches -- all of that stays allowed. Creation is "git branch" followed
# by a word that doesn't start with "-".
#
# One exception: "git checkout -b local origin/xyz" formally creates a local
# branch, but nothing new -- the branch already exists on the remote, this
# only creates the local view of it. That's what checking out someone else's
# PR branch needs, and "gh pr checkout" does the same thing and doesn't even
# match this pattern. The rule protects against *new* feature branches next
# to the main one, not against looking at something that already exists.
#
# The starting point must actually be a remote, not just a word with a
# slash: checked against "git remote". Otherwise
# "git checkout -b feature/new somewhere/old" would be a loophole.
is_existing_remote_branch() {
    local start remote
    start="$(printf '%s' "$1" | sed -nE 's/.*git +(checkout|switch) +-[bBcC] +[^ ]+ +([^ ;&|]+).*/\2/p')"
    [ -z "$start" ] && return 1
    case "$start" in */*) ;; *) return 1 ;; esac
    remote="${start%%/*}"
    # -C instead of cd: this hook's working directory isn't guaranteed
    # (rule 2 below changes into it itself, for the same reason).
    git -C "${CLAUDE_PROJECT_DIR:-$PWD}" remote 2>/dev/null | grep -qx "$remote"
}

if echo "$command" | grep -Eq '(^|[;&|] *)git +(checkout +-[bB]|switch +-[cC])' \
   && is_existing_remote_branch "$command"; then
    exit 0
fi

if echo "$command" | grep -Eq '(^|[;&|] *)git +(checkout +-[bB]|switch +-[cC]|worktree +add)'; then
    deny "This project's rule: work happens only on '$main_branch', no feature branches and no worktrees. Please repeat the command without creating a branch. Checking out an EXISTING branch is fine: 'git checkout <branch>' directly, 'git checkout -b <local> origin/<branch>' for one that only exists on the remote, or 'gh pr checkout <nr>'."
fi

if echo "$command" | grep -Eq '(^|[;&|] *)git +branch +[^-]'; then
    deny "This project's rule: work happens only on '$main_branch', no feature branches. (Listing and deleting branches is fine -- only creating one isn't.)"
fi

# --- Rule 2: pull before every commit ---------------------------------------
if ! echo "$command" | grep -Eq '(^|[;&|] *)git +(-[^ ]+ +)*commit'; then
    exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-$PWD}" 2>/dev/null || exit 0
git rev-parse --git-dir >/dev/null 2>&1 || exit 0

upstream="$(git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}' 2>/dev/null)"
if [ -z "$upstream" ]; then
    # No upstream (fresh repo, detached branch): nothing to compare against.
    exit 0
fi

if ! git fetch --quiet 2>/dev/null; then
    # Offline, or the remote is unreachable. Blocking here would be
    # obstructive -- the rule protects against stale history, not against a
    # missing network. Let it through, but say so.
    jq -n '{systemMessage: "Note: git fetch failed (offline?). The commit proceeds unchecked -- the \"pull before every commit\" rule could not be verified."}'
    exit 0
fi

behind="$(git rev-list --count "HEAD..$upstream" 2>/dev/null || echo 0)"
if [ "$behind" -gt 0 ]; then
    deny "This project's rule: pull before every commit. HEAD is ${behind} commit(s) behind ${upstream}. Please run 'git pull' first and repeat the commit."
fi

exit 0
