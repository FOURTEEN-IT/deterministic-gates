#!/usr/bin/env bash
# Stop hook for the feature-slicing skill: re-checks the slice tree under
# featuresDir against the same convention the Gradle `sliceStructure` gate
# enforces (see feature-slicing/gradle/.../SliceStructureTask.java) --
# without needing a JVM, Gradle, or this repo's plugin at all.
#
# This is a deliberate duplicate of that gate's logic, not a replacement for
# it: a project without Gradle (or a domain expert driving the skill without
# ever running `./gradlew check`) still gets the same structural feedback
# before the session ends, right where the skill left the slice tree. A
# project that *does* have the Gradle plugin applied still gets the
# authoritative check from `sliceStructure` in CI/`check` -- this hook can
# drift from it over time and was never meant to be trusted instead of it,
# only as an early, local echo of it. See the feature-slicing skill's
# "Relationship to the companion gates" section.
#
# Registered via this skill's own SKILL.md frontmatter, not a plugin-wide
# hooks.json: it only activates for the rest of a session that actually
# invoked feature-slicing, never for a session that never touched slicing.
#
# Checks, per slice node (a top-level feature doc's own numbering issues are
# checked too, but it carries no Status/User Outcome of its own):
#   - numbering under each directory is contiguous from 1, no gaps/duplicates,
#     no file that isn't a <number>.md doc;
#   - exactly one "**Status:** needs splitting|ready for implementation" line
#     inside the "Status" section, agreeing with whether children exist;
#   - a non-empty "User Outcome" section.
# (The Acceptance Criteria unsplittability notice is a report-only extra in
# the Gradle gate, not a failure there either -- skipped here on purpose.)
#
# Configure the features directory via GATES_FEATURES_DIR (default
# docs/features), same convention as this repo's other hooks. Blocks only
# once per stop attempt (stop_hook_active), same guard finish-the-work.sh
# uses, so a slice tree that still doesn't check out doesn't loop forever.

set -uo pipefail

features_dir="${GATES_FEATURES_DIR:-docs/features}"
status_section="Status"
status_label="Status"
needs_splitting="needs splitting"
ready_status="ready for implementation"
user_outcome_section="User Outcome"

input="$(cat)"
active="$(printf '%s' "$input" | jq -r '.stop_hook_active // false')"
[ "$active" = "true" ] && exit 0

cd "${CLAUDE_PROJECT_DIR:-$PWD}" 2>/dev/null || exit 0
[ -d "$features_dir" ] || exit 0

problems_ids=()
problems_text=()

add_problem() {
    local id="$1" text="$2"
    problems_ids+=("$id")
    problems_text+=("$text")
}

# Prints the lines of the named "## <heading>" section of $1, or nothing.
section() {
    local file="$1" heading="$2"
    awk -v heading="## $heading" '
        BEGIN { in_section = 0 }
        { line = $0; sub(/\r$/, "", line) }
        line == heading { in_section = 1; next }
        in_section && line ~ /^## / { in_section = 0 }
        in_section { print line }
    ' "$file"
}

# Numbers, one per line, of the <n>.md docs directly under $1; also appends
# any naming-convention violation to $2 (a name passed by reference is not
# available in plain bash, so the caller re-reads via a global instead).
NAMING_ISSUES=()
numbered_children() {
    local dir="$1"
    NAMING_ISSUES=()
    [ -d "$dir" ] || return 0
    local entry base
    for entry in "$dir"/*; do
        [ -e "$entry" ] || continue
        base="$(basename "$entry")"
        if [ -f "$entry" ] && [[ "$base" =~ ^([0-9]+)\.md$ ]]; then
            echo "${BASH_REMATCH[1]}"
        elif [ -f "$entry" ]; then
            NAMING_ISSUES+=("$(basename "$dir")/$base doesn't match the <number>.md slice-doc naming convention")
        fi
    done
}

numbering_issues() {
    local dir="$1"
    local -a numbers issues
    mapfile -t numbers < <(numbered_children "$dir" | sort -n)
    issues=("${NAMING_ISSUES[@]}")

    local -a distinct=()
    local n prev=""
    for n in $(printf '%s\n' "${numbers[@]}" | sort -n -u); do
        [ -n "$n" ] && distinct+=("$n")
    done
    if [ "${#distinct[@]}" -ne "${#numbers[@]}" ]; then
        issues+=("$(basename "$dir")/ has a duplicate slice number")
    fi
    local expected=1
    for n in "${distinct[@]}"; do
        while [ "$expected" -lt "$n" ]; do
            issues+=("$(basename "$dir")/ is missing slice ${expected}.md -- slice numbering must be contiguous from 1")
            expected=$((expected + 1))
        done
        expected=$((expected + 1))
    done

    printf '%s\n' "${issues[@]}"
    echo "---CHILDREN---"
    printf '%s\n' "${distinct[@]}"
}

check_node() {
    local node_id="$1" doc="$2" child_dir="$3"
    local -a issues=()
    local -a children=()
    local collecting_children=0
    local line
    while IFS= read -r line; do
        if [ "$line" = "---CHILDREN---" ]; then
            collecting_children=1
        elif [ "$collecting_children" = "1" ]; then
            [ -n "$line" ] && children+=("$line")
        elif [ -n "$line" ]; then
            issues+=("$line")
        fi
    done < <(numbering_issues "$child_dir")

    if [ -f "$doc" ]; then
        local -a status_values=()
        while IFS= read -r line; do
            line="$(printf '%s' "$line" | sed 's/[[:space:]]*$//')"
            if [[ "$line" == "**${status_label}:** ${needs_splitting}" || "$line" == "**${status_label}:** ${ready_status}" ]]; then
                status_values+=("${line#\*\*${status_label}:** }")
            fi
        done < <(section "$doc" "$status_section")

        if [ "${#status_values[@]}" -eq 0 ]; then
            issues+=("no \"**${status_label}:** ${needs_splitting}|${ready_status}\" line in section \"${status_section}\"")
        elif [ "${#status_values[@]}" -gt 1 ]; then
            issues+=("${#status_values[@]} status lines")
        else
            local status="${status_values[0]}"
            local has_children="false"
            [ "${#children[@]}" -gt 0 ] && has_children="true"
            if [ "$status" = "$needs_splitting" ] && [ "$has_children" = "false" ]; then
                issues+=("status is \"${needs_splitting}\" but no child slices exist under $(basename "$child_dir")/")
            elif [ "$status" != "$needs_splitting" ] && [ "$has_children" = "true" ]; then
                issues+=("status is \"${status}\" but child slices already exist under $(basename "$child_dir")/ -- should be \"${needs_splitting}\"")
            fi
        fi

        local outcome_stated="false"
        while IFS= read -r line; do
            [ -n "$(printf '%s' "$line" | tr -d '[:space:]')" ] && outcome_stated="true"
        done < <(section "$doc" "$user_outcome_section")
        if [ "$outcome_stated" = "false" ]; then
            issues+=("section \"${user_outcome_section}\" is missing or empty -- state what a user can now do once this slice is implemented")
        fi
    fi

    if [ "${#issues[@]}" -gt 0 ]; then
        add_problem "$node_id" "$(printf '%s; ' "${issues[@]}")"
    fi

    local n
    for n in "${children[@]}"; do
        check_node "${node_id}.${n}" "${child_dir}/${n}.md" "${child_dir}/${n}"
    done
}

for top_doc in "$features_dir"/*.md; do
    [ -e "$top_doc" ] || continue
    base="$(basename "$top_doc")"
    [ "$base" = "_template.md" ] && continue
    id="${base%.md}"
    child_dir="$features_dir/$id"

    top_issues=()
    top_children=()
    collecting=0
    while IFS= read -r line; do
        if [ "$line" = "---CHILDREN---" ]; then
            collecting=1
        elif [ "$collecting" = "1" ]; then
            [ -n "$line" ] && top_children+=("$line")
        elif [ -n "$line" ]; then
            top_issues+=("$line")
        fi
    done < <(numbering_issues "$child_dir")
    if [ "${#top_issues[@]}" -gt 0 ]; then
        add_problem "$id" "$(printf '%s; ' "${top_issues[@]}")"
    fi

    for n in "${top_children[@]}"; do
        check_node "${id}.${n}" "${child_dir}/${n}.md" "${child_dir}/${n}"
    done
done

if [ "${#problems_ids[@]}" -eq 0 ]; then
    exit 0
fi

reason="Slice structure check (feature-slicing skill's own duplicate of the sliceStructure gate) flagged:"
for i in "${!problems_ids[@]}"; do
    reason="$reason
  - ${problems_ids[$i]}: ${problems_text[$i]}"
done
reason="$reason

Fix the flagged slice doc(s) -- numbering, the Status line, or the User Outcome section -- then try to stop again. If this doesn't reproduce with the authoritative sliceStructure Gradle gate (where applied), trust that gate instead."

jq -n --arg reason "$reason" '{decision: "block", reason: $reason}'
