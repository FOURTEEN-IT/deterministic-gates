#!/usr/bin/env bash
# PreToolUse hook for Bash: blocks a "feat"/"fix" commit that touches a
# feature-slicing doc (feature or slice, under featuresDir) unless that doc's
# staged content already carries "**Accepted:** yes" -- the acceptance skill's
# own marker for "I compared the implementation against this doc's Motivation,
# Acceptance Criteria, Scenarios and User Outcome, and it holds up."
#
# What this hook checks is deliberately shallow, the same way sliceStructure's
# Status check is: it confirms the marker is *present* in the doc that's about
# to be committed, never that the acceptance behind it was done honestly. That
# judgment stays the acceptance skill's (or a reviewer's); this hook only
# makes sure the step wasn't skipped.
#
# Only fires on "feat"/"fix" commits (the two Conventional-Commits types that
# actually change behavior) that also stage a change under featuresDir. A
# feature-slicing-only commit (cutting a feature into slices, no production
# code yet) should carry a type other than feat/fix -- see the
# commit-discipline plugin's release-impact skill for that judgment call;
# this hook doesn't second-guess the type, only reads it.
#
# Configure the features directory via GATES_FEATURES_DIR (default
# docs/features). Reads the hook JSON on stdin, answers with a PreToolUse
# decision.

set -uo pipefail

features_dir="${GATES_FEATURES_DIR:-docs/features}"

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

if ! printf '%s\n' "$command" | grep -Eq '(^|[;&|] *)git +(-[^ ]+ +)*commit'; then
    exit 0
fi

# The Conventional-Commits type is read from the first line, anywhere in the
# command, that looks like a real subject line -- covers both a plain
# `-m "type: subject"` and this project's own heredoc convention
# (`-m "$(cat <<'EOF' ... EOF)"`, where the subject is the heredoc's first
# line). Splitting on quote characters first turns `-m "feat: ..."` into
# "feat: ..." as its own line, the same shape a heredoc body's first line
# already has, so one anchored pattern covers both.
quote_chars=$'"\''
subject_line="$(printf '%s\n' "$command" | tr "$quote_chars" '\n' \
    | grep -E '^[[:space:]]*(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)(\([^)]*\))?!?:' \
    | head -1)"
commit_type="$(printf '%s' "$subject_line" | sed -E 's/^[[:space:]]*([a-z]+).*/\1/')"

case "$commit_type" in
    feat|fix) ;;
    *) exit 0 ;;
esac

cd "${CLAUDE_PROJECT_DIR:-$PWD}" 2>/dev/null || exit 0
git rev-parse --git-dir >/dev/null 2>&1 || exit 0

unaccepted=()
while IFS= read -r file; do
    [ -z "$file" ] && continue
    if ! git show ":$file" 2>/dev/null | grep -qE '^\*\*Accepted:\*\*[[:space:]]+yes[[:space:]]*$'; then
        unaccepted+=("$file")
    fi
done < <(git diff --cached --name-only --diff-filter=ACM -- "$features_dir" 2>/dev/null)

if [ "${#unaccepted[@]}" -gt 0 ]; then
    deny "This project's rule: a feat/fix commit that touches a feature-slicing doc needs that doc's implementation accepted first (a \"**Accepted:** yes\" line, written by the acceptance skill after comparing the implementation against the doc's Motivation/Acceptance Criteria/Scenarios/User Outcome). Missing on: $(IFS=,; echo "${unaccepted[*]}"). Run the acceptance skill, then repeat the commit."
fi

exit 0
