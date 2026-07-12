#!/usr/bin/env bash
# Auto-split multi-criteria issue into atomic issues
#
# Usage: split-multi-criteria-issue.sh <parent_issue_number>
#
# Reads parent issue, extracts each acceptance criterion,
# creates one child issue per criterion, closes parent

set -euo pipefail

REPO="${HOMEDIR_SDLC_REPO:-os-santiago/homedir}"

log() {
  echo "[$(date -u '+%Y-%m-%dT%H:%M:%SZ')] [split-issue] $*" >&2
}

# Extract issue title without prefix
extract_base_title() {
  local title="$1"
  # Remove common prefixes
  title="${title#\[*\] }"
  title="${title#feat: }"
  title="${title#fix: }"
  title="${title#docs: }"
  echo "$title"
}

# Extract acceptance criteria from issue body
extract_criteria() {
  local body="$1"
  echo "$body" | grep '^\- \[ \]' | sed 's/^- \[ \] //'
}

# Generate child issue body with single criterion
generate_child_body() {
  local parent_body="$1"
  local criterion="$2"
  local parent_number="$3"

  # Extract problem statement
  local problem=""
  if echo "$parent_body" | grep -q "## Problem Statement"; then
    problem=$(echo "$parent_body" | sed -n '/## Problem Statement/,/^##/p' | sed '1d;$d' | head -5)
  fi

  # Extract expected behavior
  local expected=""
  if echo "$parent_body" | grep -q "## Expected Behavior"; then
    expected=$(echo "$parent_body" | sed -n '/## Expected Behavior/,/^##/p' | sed '1d;$d' | head -5)
  fi

  # Generate body
  cat <<EOF
## Problem Statement
${problem:-Auto-split from #${parent_number}}

## Expected Behavior
${expected:-Implement specific criterion from parent issue}

## Acceptance Criteria
- [ ] ${criterion}

## Complexity
- [x] Simple (auto-split from multi-criteria issue)

## Notes
- Auto-split from issue #${parent_number}
- Part of atomic implementation per ADEV principles
- Original issue had multiple criteria, split for autonomous processing
EOF
}

# Main split logic
split_issue() {
  local parent_number="$1"

  log "splitting issue #${parent_number}"

  # Get parent issue details
  local parent_data
  parent_data=$(gh issue view "${parent_number}" -R "${REPO}" --json title,body,labels 2>&1)

  if [[ ! "$parent_data" =~ ^\{ ]]; then
    log "ERROR: failed to fetch parent issue: ${parent_data}"
    return 1
  fi

  local parent_title
  parent_title=$(echo "$parent_data" | jq -r '.title')

  local parent_body
  parent_body=$(echo "$parent_data" | jq -r '.body')

  local parent_labels
  parent_labels=$(echo "$parent_data" | jq -r '.labels[].name' 2>/dev/null | grep -v '^scc-' || true)
  local child_labels
  if [[ -n "${parent_labels}" ]]; then
    child_labels="${parent_labels},scc-auto-split"
  else
    child_labels="scc-auto-split"
  fi

  # Extract criteria
  local -a criteria
  mapfile -t criteria < <(extract_criteria "$parent_body")

  local criteria_count="${#criteria[@]}"

  if [[ "$criteria_count" -eq 0 ]]; then
    log "ERROR: no acceptance criteria found in issue #${parent_number}"
    return 1
  fi

  log "found ${criteria_count} acceptance criteria"

  # Extract base title
  local base_title
  base_title=$(extract_base_title "$parent_title")

  # Create child issues
  local -a child_numbers
  for i in "${!criteria[@]}"; do
    local criterion="${criteria[$i]}"
    local child_index=$((i + 1))

    log "creating child issue ${child_index}/${criteria_count}"

    # Generate child title
    local child_title="[auto-split ${child_index}/${criteria_count}] ${base_title}"

    # Generate child body
    local child_body
    child_body=$(generate_child_body "$parent_body" "$criterion" "$parent_number")

    # Create child issue
    local child_number
    local create_output
    create_output=$(gh issue create -R "${REPO}" \
      --title "${child_title}" \
      --body "${child_body}" \
      --label "${child_labels}" 2>&1)

    # Extract issue number from URL (gh returns URL in format https://github.com/owner/repo/issues/NUMBER)
    child_number=$(echo "$create_output" | grep -oE 'issues/[0-9]+' | grep -oE '[0-9]+' || echo "")

    if [[ ! "$child_number" =~ ^[0-9]+$ ]]; then
      log "ERROR: failed to create child issue: ${create_output}"
      continue
    fi

    child_numbers+=("$child_number")
    log "created child issue #${child_number}"

    # Link to parent
    gh issue comment "${child_number}" -R "${REPO}" --body "Auto-split from #${parent_number} (criterion ${child_index}/${criteria_count})" >/dev/null 2>&1 || true

    # Auto-admit and queue first child immediately, others will wait
    if [[ $i -eq 0 ]]; then
      log "auto-admitting and queuing first child issue #${child_number}"
      gh issue edit "${child_number}" -R "${REPO}" --add-label "scc-accepted,scc-queued" >/dev/null 2>&1 || true
    else
      # Just accept, don't queue (orchestrator will handle sequencing)
      gh issue edit "${child_number}" -R "${REPO}" --add-label "scc-accepted" >/dev/null 2>&1 || true
    fi
  done

  # Comment on parent with links to children
  local child_links=""
  for child in "${child_numbers[@]}"; do
    child_links+="- #${child}"$'\n'
  done

  gh issue comment "${parent_number}" -R "${REPO}" --body "This issue has ${criteria_count} acceptance criteria and was auto-split into ${#child_numbers[@]} atomic issues for autonomous processing:

${child_links}
Per ADEV principles, each child issue has exactly 1 acceptance criterion.

First issue (#${child_numbers[0]}) has been queued for immediate processing. Remaining issues will be processed sequentially via pipeline orchestration." >/dev/null 2>&1 || true

  # Close parent and mark as split
  log "closing parent issue #${parent_number}"
  gh issue edit "${parent_number}" -R "${REPO}" --add-label "scc-auto-split-parent" >/dev/null 2>&1 || true
  gh issue close "${parent_number}" -R "${REPO}" --reason "not planned" >/dev/null 2>&1 || true

  log "split complete: created ${#child_numbers[@]} child issues"

  # Output child numbers for pipeline
  echo "${child_numbers[@]}"
  return 0
}

# Entry point
main() {
  if [[ $# -lt 1 ]]; then
    echo "Usage: $0 <parent_issue_number>" >&2
    exit 1
  fi

  local parent_number="$1"

  if ! [[ "$parent_number" =~ ^[0-9]+$ ]]; then
    log "ERROR: invalid issue number: ${parent_number}"
    exit 1
  fi

  split_issue "${parent_number}"
}

main "$@"
