#!/usr/bin/env bash
# Stop hook: at the end of an answer, checks whether the work that was started is actually
# finished -- and if it is, makes the agent commit AND push it rather than leave it lying
# around locally.
#
# Two cases, because the rule has two steps:
#
#   1. The working tree isn't clean -> review the change, commit it if it's done, push.
#   2. The working tree is clean, but commits sit ahead of the upstream -> push.
#
# Case 2 is the one that's easy to miss: a hook that only looks at the working tree exits
# happily on a clean one, and eight finished, unpushed commits are invisible to it -- although
# that is exactly the state the rule exists to prevent. A finished commit that never leaves the
# machine isn't delivered.
#
# Blocks only ONCE per stop attempt (stop_hook_active) -- otherwise the agent loops when it
# deliberately decides not to commit, e.g. because the work is visibly unfinished.
#
# The commit and push themselves happen in the agent's next turn through its ordinary tools, so
# whatever else guards committing (a commit-msg hook, a pull-before-commit rule) still applies.
#
# Configure the verification command it asks for via GATES_VERIFY_COMMAND (e.g. "./gradlew
# check", "npm test"); without it the hook just names it generically.

set -uo pipefail

# No apostrophe in the default: bash reads a single quote inside "${x:-...}" as opening a
# quoted string, and the script would not parse.
verify_command="${GATES_VERIFY_COMMAND:-the verification command of this project}"

input="$(cat)"
active="$(printf '%s' "$input" | jq -r '.stop_hook_active // false')"
[ "$active" = "true" ] && exit 0

cd "${CLAUDE_PROJECT_DIR:-$PWD}" 2>/dev/null || exit 0
git rev-parse --git-dir >/dev/null 2>&1 || exit 0

dirty="$(git status --porcelain)"

# Without an upstream there is nothing to push -- only case 1 applies.
if git rev-parse --abbrev-ref '@{upstream}' >/dev/null 2>&1; then
    ahead="$(git rev-list --count '@{upstream}..HEAD' 2>/dev/null || echo 0)"
else
    ahead="0"
fi

[ -z "$dirty" ] && [ "$ahead" = "0" ] && exit 0

if [ -n "$dirty" ]; then
    jq -n --arg verify "$verify_command" '{
        decision: "block",
        reason: ("The working tree is not clean. Check: is the change you started actually complete? If it is, run " + $verify + " in full; if it comes back green, commit the change with a Conventional Commits message and then push -- the two belong together, a finished commit left sitting locally is not delivered. If verification fails, fix the cause or end the answer without committing and tell the user why. If the work is visibly unfinished (work in progress, next step still open), do NOT commit and stop normally.")
    }'
else
    jq -n --arg ahead "$ahead" '{
        decision: "block",
        reason: ("The working tree is clean, but " + $ahead + " commit(s) sit ahead of the upstream. Finished work belongs pushed, not offered to the user as a question. Push now. Keep in mind what the push sets off: a releasing type (feat:/fix:/perf:) among those commits may start a release and a deploy, where docs:/chore:/test: only run the build. If you deliberately do not want to push because the series is not finished yet, stop normally and tell the user why.")
    }'
fi
