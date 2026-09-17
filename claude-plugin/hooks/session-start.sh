#!/usr/bin/env bash
# SessionStart hook: feeds in, at the start of a session, what currently
# holds -- working-tree state and any open decisions.
#
# The point: a rule like "don't silently settle an open question, ask
# instead" only works if the agent knows what's open. Without this hook, it
# has to read the open-decisions file first -- which means only once it
# already suspects a question is open. That's backwards: the whole purpose
# of that file is to prevent a decision from being made silently, and
# someone who settles things silently doesn't check first.
#
# Configure the open-decisions file via GATES_OPEN_DECISIONS_FILE (default
# docs/open-decisions.md). Deliberately terse: one heading per open item,
# not the full text -- a hook that dumps seventy lines into every session
# start gets skimmed past, not read.

set -uo pipefail

open_decisions_file="${GATES_OPEN_DECISIONS_FILE:-docs/open-decisions.md}"

cd "${CLAUDE_PROJECT_DIR:-$PWD}" 2>/dev/null || exit 0
git rev-parse --git-dir >/dev/null 2>&1 || exit 0

branch="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo '?')"
dirty_count="$(git status --porcelain | wc -l | tr -d ' ')"
if [ "$dirty_count" = "0" ]; then
    tree_state="clean"
else
    tree_state="$dirty_count changed file(s) -- see git status"
fi

# Standing relative to upstream: the same measure main-branch-rule.sh rejects
# a commit on (pull before commit). No upstream, no claim made.
if git rev-parse --abbrev-ref '@{upstream}' >/dev/null 2>&1; then
    behind="$(git rev-list --count 'HEAD..@{upstream}' 2>/dev/null || echo 0)"
    ahead="$(git rev-list --count '@{upstream}..HEAD' 2>/dev/null || echo 0)"
    upstream_state="$ahead commit(s) ahead, $behind behind the upstream"
    [ "$behind" != "0" ] && upstream_state="$upstream_state -- pull before the next commit"
else
    upstream_state="no upstream set"
fi

commits="$(git log --format='  %h %s' -5 2>/dev/null)"

if [ -f "$open_decisions_file" ]; then
    # Convention: open items are paragraph headings ("**Title.** ...") under
    # a heading whose name contains "open" (case-insensitive); a later
    # section whose name contains "ruled out" (or ends the open items) marks
    # entries that are settled-as-excluded rather than pending -- those don't
    # count as open.
    open_items="$(awk '
        BEGIN { IGNORECASE = 1 }
        /^## .*ruled out.*/ { in_open = 0 }
        in_open && /^\*\*/  { line = $0
                              sub(/^\*\*/, "", line); sub(/\*\*.*$/, "", line)
                              print "  - " line }
        /^## .*open.*/ && !/ruled out/ { in_open = 1; next }
    ' "$open_decisions_file")"
    ruled_out_count="$(awk '
        BEGIN { IGNORECASE = 1 }
        /^## .*ruled out.*/ { in_section = 1; next }
        in_section && /^- \*\*/ { n++ }
        END { print n + 0 }
    ' "$open_decisions_file")"
else
    open_items=""
    ruled_out_count="0"
fi

[ -z "$open_items" ] && open_items="  (none)"

text="Working state (SessionStart hook)

Branch: $branch | Working tree: $tree_state | $upstream_state

Recent commits:
$commits

Open decisions ($open_decisions_file) -- don't settle these silently,
ask instead:
$open_items

Plus $ruled_out_count item(s) deliberately ruled out in the same document --
check there before reopening one."

jq -n --arg text "$text" '{
    hookSpecificOutput: {
        hookEventName: "SessionStart",
        additionalContext: $text
    }
}'
