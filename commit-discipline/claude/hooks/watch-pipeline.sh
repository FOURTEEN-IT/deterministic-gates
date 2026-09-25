#!/usr/bin/env bash
# PostToolUse hook (Bash): after a push, watches the GitHub Actions runs for that commit.
#
# Runs in the background (async) and reports back EXACTLY ONCE, green or red: exit 2 wakes the
# model with this script's output, exit 0 stays silent. After that the script is done -- no
# repeated waking for the same push.
#
# An unreachable network, a missing run or a timeout is not a pipeline result, it's observation
# noise -- waking for that would be noise too, and an observer that keeps crying wolf gets
# turned off. Only an actually completed run (green or red) triggers the message.
#
# Needs curl and jq, and a GitHub remote named origin. GH_TOKEN or GITHUB_TOKEN lifts the
# unauthenticated 60-requests-per-hour limit and lets it poll twice as often.
#
# Wire it as a PostToolUse hook on Bash with "asyncRewake": true (see settings.snippet.json).

set -uo pipefail

input="$(cat)"
command="$(printf '%s' "$input" | jq -r '.tool_input.command // empty')"
echo "$command" | grep -Eq '(^|[;&|] *)git +(-[^ ]+ +)*push' || exit 0

cd "${CLAUDE_PROJECT_DIR:-$PWD}" 2>/dev/null || exit 0

origin="$(git remote get-url origin 2>/dev/null)" || exit 0
case "$origin" in
    # Not a GitHub remote: there are no Actions runs to watch, and polling api.github.com with
    # whatever this is would only burn the wait loop below against a 404.
    *github.com*) ;;
    *) exit 0 ;;
esac
repo="$(printf '%s' "$origin" | sed -E 's#^.*github\.com[:/]##; s#\.git$##')"
[ -z "$repo" ] && exit 0
sha="$(git rev-parse HEAD 2>/dev/null)" || exit 0

headers=(-H "Accept: application/vnd.github+json")
interval=30
if [ -n "${GH_TOKEN:-${GITHUB_TOKEN:-}}" ]; then
    headers+=(-H "Authorization: Bearer ${GH_TOKEN:-$GITHUB_TOKEN}")
    interval=15
fi

api() {
    curl -s --max-time 20 "${headers[@]}" "https://api.github.com/repos/$repo/$1"
}

# Wait for the start: seconds pass between a push and a visible run.
found=0
for _ in $(seq 1 10); do
    if api "actions/runs?per_page=20&head_sha=$sha" | jq -e '.workflow_runs | length > 0' >/dev/null 2>&1; then
        found=1
        break
    fi
    sleep 15
done
[ "$found" -eq 0 ] && exit 0

deadline=$((SECONDS + 1500))
while [ $SECONDS -lt $deadline ]; do
    response="$(api "actions/runs?per_page=20&head_sha=$sha")"
    printf '%s' "$response" | jq -e '.workflow_runs' >/dev/null 2>&1 || { sleep "$interval"; continue; }

    pending="$(printf '%s' "$response" | jq -r '[.workflow_runs[] | select(.status != "completed")] | length')"
    if [ "$pending" -gt 0 ]; then
        sleep "$interval"
        continue
    fi

    failed="$(printf '%s' "$response" | jq -r '
        [.workflow_runs[] | select(.conclusion != "success" and .conclusion != "skipped")]')"
    count="$(printf '%s' "$failed" | jq -r 'length')"
    if [ "$count" -eq 0 ]; then
        echo "Pipeline green for ${sha:0:8} in $repo."
        exit 2
    fi

    # From here it's settled: at least one run is red. The message names the failing step, not
    # just the run -- otherwise the investigation starts with the lookup this script already did.
    echo "Pipeline failed for ${sha:0:8} in $repo:"
    echo
    for run_id in $(printf '%s' "$failed" | jq -r '.[].id'); do
        printf '%s' "$failed" | jq -r --arg id "$run_id" '
            .[] | select(.id == ($id | tonumber))
            | "  Run: \(.name) -> \(.conclusion)\n  \(.html_url)"'
        api "actions/runs/$run_id/jobs" | jq -r '
            .jobs[]
            | select(.conclusion != "success" and .conclusion != "skipped")
            | "    Job \(.name) -> \(.conclusion)",
              (.steps[]? | select(.conclusion == "failure") | "      Step \(.number). \(.name)")'
        echo
    done
    exit 2
done

exit 0
