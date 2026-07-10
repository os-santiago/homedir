#!/usr/bin/env bash
# Pipeline Orchestrator - Auto-creates next issue in pipeline sequence
#
# Usage: pipeline-orchestrator.sh <completed_issue_number>
#
# Reads pipeline YAML definitions from .github/pipelines/*.yaml
# When an issue completes, creates and queues the next issue in sequence

set -euo pipefail

REPO="${HOMEDIR_SDLC_REPO:-os-santiago/homedir}"
PIPELINES_DIR="${HOMEDIR_SDLC_PIPELINES_DIR:-.github/pipelines}"
WORKSPACE_ROOT="${HOMEDIR_SDLC_WORKSPACE_ROOT:-$(git rev-parse --show-toplevel 2>/dev/null || echo "$PWD")}"

log() {
  echo "[$(date -u '+%Y-%m-%dT%H:%M:%SZ')] [pipeline-orchestrator] $*" >&2
}

# Find pipeline YAML that references this issue number
find_pipeline_for_issue() {
  local issue_number="$1"
  local pipelines_path="${WORKSPACE_ROOT}/${PIPELINES_DIR}"

  if [[ ! -d "${pipelines_path}" ]]; then
    log "pipelines directory not found: ${pipelines_path}"
    return 1
  fi

  # Search all YAML files for issue number in comments or metadata
  local pipeline_file
  for yaml_file in "${pipelines_path}"/*.yaml; do
    if [[ ! -f "${yaml_file}" ]]; then
      continue
    fi

    # Check if this pipeline tracks this issue via metadata
    if grep -q "issue_number: ${issue_number}" "${yaml_file}" 2>/dev/null; then
      echo "${yaml_file}"
      return 0
    fi

    # Check if issue title matches any in pipeline
    local issue_title
    issue_title=$(gh issue view "${issue_number}" -R "${REPO}" --json title -q '.title' 2>/dev/null || echo "")

    if [[ -n "${issue_title}" ]] && grep -F -q "${issue_title}" "${yaml_file}"; then
      echo "${yaml_file}"
      return 0
    fi
  done

  return 1
}

# Extract issue definition from YAML (simple parser, assumes specific format)
get_next_issue_from_pipeline() {
  local pipeline_file="$1"
  local completed_issue="$2"

  # Get completed issue title
  local completed_title
  completed_title=$(gh issue view "${completed_issue}" -R "${REPO}" --json title -q '.title' 2>/dev/null || echo "")

  if [[ -z "${completed_title}" ]]; then
    log "could not find title for completed issue #${completed_issue}"
    return 1
  fi

  log "completed issue title: ${completed_title}"

  # Simple YAML parser: find the issue block after completed one
  local found_current=false
  local in_next_issue=false
  local next_issue_data=""

  while IFS= read -r line; do
    # Detect start of issue block
    if [[ "$line" =~ ^[[:space:]]*-[[:space:]]+id:[[:space:]]* ]]; then
      if $found_current && ! $in_next_issue; then
        in_next_issue=true
        next_issue_data="${line}"$'\n'
        continue
      elif $in_next_issue; then
        # Hit another issue block, we're done
        break
      fi
    fi

    # Check if this is the completed issue
    if [[ "$line" =~ title:[[:space:]]*\"(.+)\" ]] || [[ "$line" =~ title:[[:space:]]*\'(.+)\' ]]; then
      local title_content="${BASH_REMATCH[1]}"
      if [[ "$title_content" == "$completed_title" ]]; then
        found_current=true
        log "found current issue in pipeline"
      fi
    fi

    # Accumulate next issue data
    if $in_next_issue; then
      # Stop at depends_on or next issue
      if [[ "$line" =~ ^[[:space:]]*-[[:space:]]+id:[[:space:]]* ]]; then
        break
      fi
      next_issue_data+="${line}"$'\n'
    fi
  done < "${pipeline_file}"

  if [[ -n "${next_issue_data}" ]]; then
    echo "${next_issue_data}"
    return 0
  fi

  return 1
}

# Parse issue data and create GitHub issue
create_issue_from_definition() {
  local issue_data="$1"

  # Extract fields using basic parsing
  local title=""
  local body=""
  local labels="ready-to-implement"

  # Parse title
  if [[ "$issue_data" =~ title:[[:space:]]*[\"\']([^\"\']+)[\"\'] ]]; then
    title="${BASH_REMATCH[1]}"
  elif [[ "$issue_data" =~ title:[[:space:]]*([^$'\n']+) ]]; then
    title="${BASH_REMATCH[1]}"
  fi

  # Parse body (everything between body: | and next field)
  local in_body=false
  local body_content=""
  while IFS= read -r line; do
    if [[ "$line" =~ ^[[:space:]]+body:[[:space:]]*\| ]]; then
      in_body=true
      continue
    elif $in_body; then
      # Stop at labels: or depends_on:
      if [[ "$line" =~ ^[[:space:]]+(labels|depends_on):[[:space:]]* ]]; then
        break
      fi
      # Remove leading spaces (YAML indentation)
      local trimmed_line="${line#      }"
      body_content+="${trimmed_line}"$'\n'
    fi
  done <<< "$issue_data"

  body="$body_content"

  # Parse labels
  if [[ "$issue_data" =~ labels: ]]; then
    local in_labels=false
    while IFS= read -r line; do
      if [[ "$line" =~ ^[[:space:]]+labels:[[:space:]]*$ ]]; then
        in_labels=true
        continue
      elif $in_labels; then
        if [[ "$line" =~ ^[[:space:]]+depends_on: ]]; then
          break
        fi
        if [[ "$line" =~ ^[[:space:]]*-[[:space:]]*([a-zA-Z0-9_-]+) ]]; then
          local label="${BASH_REMATCH[1]}"
          if [[ ! "$labels" =~ $label ]]; then
            labels="${labels},${label}"
          fi
        elif [[ ! "$line" =~ ^[[:space:]]*-[[:space:]]+ ]]; then
          break
        fi
      fi
    done <<< "$issue_data"
  fi

  # Validate
  if [[ -z "$title" ]]; then
    log "ERROR: could not parse title from issue data"
    return 1
  fi

  log "creating issue: ${title}"
  log "labels: ${labels}"

  # Create issue
  local new_issue_number
  local create_output
  create_output=$(gh issue create -R "${REPO}" \
    --title "${title}" \
    --body "${body}" \
    --label "${labels}" 2>&1)

  # Extract issue number from URL (gh returns URL in format https://github.com/owner/repo/issues/NUMBER)
  new_issue_number=$(echo "$create_output" | grep -oE 'issues/[0-9]+' | grep -oE '[0-9]+' || echo "")

  if [[ ! "$new_issue_number" =~ ^[0-9]+$ ]]; then
    log "ERROR: failed to create issue: ${create_output}"
    return 1
  fi

  echo "${new_issue_number}"
  return 0
}

# Main orchestration logic
orchestrate_pipeline() {
  local completed_issue="$1"

  log "orchestrating pipeline for completed issue #${completed_issue}"

  # Find pipeline
  local pipeline_file
  if ! pipeline_file=$(find_pipeline_for_issue "${completed_issue}"); then
    log "no pipeline found for issue #${completed_issue}"
    return 0
  fi

  log "found pipeline: ${pipeline_file}"

  # Get next issue definition
  local next_issue_data
  if ! next_issue_data=$(get_next_issue_from_pipeline "${pipeline_file}" "${completed_issue}"); then
    log "no next issue in pipeline (pipeline complete)"
    return 0
  fi

  log "found next issue definition"

  # Create next issue
  local new_issue_number
  if ! new_issue_number=$(create_issue_from_definition "${next_issue_data}"); then
    log "ERROR: failed to create next issue"
    return 1
  fi

  log "created issue #${new_issue_number}"

  # Auto-admit and queue the new issue
  log "auto-admitting and queuing issue #${new_issue_number}"
  gh issue edit "${new_issue_number}" -R "${REPO}" \
    --add-label "scc-accepted,scc-queued" 2>&1 || true

  log "pipeline orchestration complete: created and queued issue #${new_issue_number}"
  return 0
}

# Entry point
main() {
  if [[ $# -lt 1 ]]; then
    echo "Usage: $0 <completed_issue_number>" >&2
    exit 1
  fi

  local completed_issue="$1"

  if ! [[ "$completed_issue" =~ ^[0-9]+$ ]]; then
    log "ERROR: invalid issue number: ${completed_issue}"
    exit 1
  fi

  orchestrate_pipeline "${completed_issue}"
}

main "$@"
