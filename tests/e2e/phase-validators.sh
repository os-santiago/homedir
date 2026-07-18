#!/bin/bash
# SDLC E2E Phase Validators
# Individual validators for each phase of the SDLC pipeline

set -euo pipefail

REPO="${HOMEDIR_SDLC_REPO:-os-santiago/homedir}"

# Validate admission phase
validate_admission() {
  local issue="$1"

  echo "Validating admission phase for issue #${issue}..."

  # Check issue exists and is open
  local state
  state=$(gh issue view "${issue}" -R "${REPO}" --json state --jq '.state')

  if [[ "${state}" != "OPEN" ]]; then
    echo "❌ Issue is not open (state: ${state})"
    return 1
  fi

  # Check for admission labels
  local labels
  labels=$(gh issue view "${issue}" -R "${REPO}" --json labels --jq '.labels[].name' | tr '\n' ',')

  if ! echo "${labels}" | grep -q "ready-to-implement"; then
    echo "❌ Missing 'ready-to-implement' label"
    return 1
  fi

  if ! echo "${labels}" | grep -q "scc-accepted"; then
    echo "⚠ Missing 'scc-accepted' label (admission may be in progress)"
    return 1
  fi

  # Check for rejection
  if echo "${labels}" | grep -qE "scc-rejected"; then
    echo "❌ Issue was rejected during admission"
    return 1
  fi

  echo "✓ Admission validation passed"
  return 0
}

# Validate queue phase
validate_queue() {
  local issue="$1"

  echo "Validating queue phase for issue #${issue}..."

  local labels
  labels=$(gh issue view "${issue}" -R "${REPO}" --json labels --jq '.labels[].name' | tr '\n' ',')

  if ! echo "${labels}" | grep -q "scc-queued"; then
    echo "❌ Missing 'scc-queued' label"
    return 1
  fi

  # Check state directory on VPS (if accessible)
  if command -v ssh &>/dev/null && [[ -n "${SDLC_VPS_HOST:-}" ]]; then
    local state_file="/home/homedir-sdlc/.local/state/homedir-sdlc/issues/${issue}.json"
    if ssh "${SDLC_VPS_HOST}" "[[ -f '${state_file}' ]]" 2>/dev/null; then
      echo "✓ Issue state file exists on VPS"
    fi
  fi

  echo "✓ Queue validation passed"
  return 0
}

# Validate worker processing phase
validate_running() {
  local issue="$1"

  echo "Validating worker processing for issue #${issue}..."

  local labels
  labels=$(gh issue view "${issue}" -R "${REPO}" --json labels --jq '.labels[].name' | tr '\n' ',')

  if ! echo "${labels}" | grep -q "scc-running"; then
    echo "❌ Missing 'scc-running' label"
    return 1
  fi

  # Check for worker heartbeat (if VPS accessible)
  if command -v ssh &>/dev/null && [[ -n "${SDLC_VPS_HOST:-}" ]]; then
    local heartbeat_file="/home/homedir-sdlc/.local/state/homedir-sdlc/heartbeat.json"
    if ssh "${SDLC_VPS_HOST}" "[[ -f '${heartbeat_file}' ]]" 2>/dev/null; then
      local heartbeat_age
      heartbeat_age=$(ssh "${SDLC_VPS_HOST}" "stat -c %Y '${heartbeat_file}'" 2>/dev/null || echo "0")
      local now
      now=$(date +%s)
      local age=$((now - heartbeat_age))

      if [[ ${age} -lt 300 ]]; then
        echo "✓ Worker heartbeat is fresh (${age}s old)"
      else
        echo "⚠ Worker heartbeat is stale (${age}s old)"
      fi
    fi
  fi

  echo "✓ Worker processing validation passed"
  return 0
}

# Validate PR creation
validate_pr_creation() {
  local issue="$1"

  echo "Validating PR creation for issue #${issue}..."

  # Find PR for this issue
  local pr_json
  pr_json=$(gh pr list -R "${REPO}" --search "in:title closes #${issue}" --json number,state,title --limit 1)

  if [[ "${pr_json}" == "[]" ]]; then
    echo "❌ No PR found for issue #${issue}"
    return 1
  fi

  local pr_number
  pr_number=$(echo "${pr_json}" | jq -r '.[0].number')

  local pr_state
  pr_state=$(echo "${pr_json}" | jq -r '.[0].state')

  local pr_title
  pr_title=$(echo "${pr_json}" | jq -r '.[0].title')

  echo "✓ PR #${pr_number} found: ${pr_title} (${pr_state})"

  # Validate PR body contains issue reference
  local pr_body
  pr_body=$(gh pr view "${pr_number}" -R "${REPO}" --json body --jq '.body')

  if echo "${pr_body}" | grep -qE "#${issue}|closes.*${issue}|fixes.*${issue}"; then
    echo "✓ PR body references issue #${issue}"
  else
    echo "⚠ PR body does not clearly reference issue #${issue}"
  fi

  # Validate branch naming
  local branch
  branch=$(gh pr view "${pr_number}" -R "${REPO}" --json headRefName --jq '.headRefName')

  if echo "${branch}" | grep -qE "scc/issue-${issue}|^issue-${issue}"; then
    echo "✓ Branch name follows convention: ${branch}"
  else
    echo "⚠ Branch name may not follow convention: ${branch}"
  fi

  echo "${pr_number}"
  return 0
}

# Validate CI checks
validate_checks() {
  local pr="$1"

  echo "Validating CI checks for PR #${pr}..."

  local checks_json
  checks_json=$(gh pr view "${pr}" -R "${REPO}" --json statusCheckRollup)

  local total_checks
  total_checks=$(echo "${checks_json}" | jq -r '.statusCheckRollup | length')

  if [[ ${total_checks} -eq 0 ]]; then
    echo "⚠ No checks configured for this PR"
    return 0
  fi

  echo "Found ${total_checks} check(s)"

  # Count by status
  local pending
  pending=$(echo "${checks_json}" | jq -r '[.statusCheckRollup[] | select(.status == "PENDING" or .status == "IN_PROGRESS")] | length')

  local passed
  passed=$(echo "${checks_json}" | jq -r '[.statusCheckRollup[] | select(.conclusion == "SUCCESS")] | length')

  local failed
  failed=$(echo "${checks_json}" | jq -r '[.statusCheckRollup[] | select(.conclusion == "FAILURE")] | length')

  echo "  Passed: ${passed}"
  echo "  Failed: ${failed}"
  echo "  Pending: ${pending}"

  if [[ ${failed} -gt 0 ]]; then
    echo "Failed checks:"
    echo "${checks_json}" | jq -r '.statusCheckRollup[] | select(.conclusion == "FAILURE") | "  - \(.name): \(.conclusion)"'
  fi

  if [[ ${passed} -eq ${total_checks} ]]; then
    echo "✓ All checks passed"
    return 0
  elif [[ ${failed} -gt 0 ]]; then
    echo "❌ Some checks failed"
    return 1
  else
    echo "⚠ Checks still in progress"
    return 2
  fi
}

# Validate merge eligibility
validate_merge_eligibility() {
  local pr="$1"

  echo "Validating merge eligibility for PR #${pr}..."

  local pr_json
  pr_json=$(gh pr view "${pr}" -R "${REPO}" --json mergeable,mergeStateStatus,reviewDecision)

  local mergeable
  mergeable=$(echo "${pr_json}" | jq -r '.mergeable')

  local merge_state
  merge_state=$(echo "${pr_json}" | jq -r '.mergeStateStatus')

  local review_decision
  review_decision=$(echo "${pr_json}" | jq -r '.reviewDecision // "NONE"')

  echo "  Mergeable: ${mergeable}"
  echo "  Merge State: ${merge_state}"
  echo "  Review Decision: ${review_decision}"

  if [[ "${mergeable}" == "MERGEABLE" ]] && [[ "${merge_state}" == "CLEAN" ]]; then
    echo "✓ PR is ready to merge"
    return 0
  elif [[ "${mergeable}" == "CONFLICTING" ]]; then
    echo "❌ PR has merge conflicts"
    return 1
  else
    echo "⚠ PR may not be ready to merge yet (state: ${merge_state})"
    return 2
  fi
}

# Validate auto-merge status
validate_automerge() {
  local pr="$1"

  echo "Validating auto-merge for PR #${pr}..."

  local automerge_enabled
  automerge_enabled=$(gh pr view "${pr}" -R "${REPO}" --json autoMergeRequest --jq '.autoMergeRequest != null')

  if [[ "${automerge_enabled}" == "true" ]]; then
    echo "✓ Auto-merge is enabled"
    return 0
  else
    echo "⚠ Auto-merge is not enabled"
    return 1
  fi
}

# Validate deployment
validate_deployment() {
  local pr="$1"

  echo "Validating deployment for PR #${pr}..."

  # Get merge commit
  local merge_sha
  merge_sha=$(gh pr view "${pr}" -R "${REPO}" --json mergeCommit --jq '.mergeCommit.oid // empty')

  if [[ -z "${merge_sha}" ]]; then
    echo "❌ PR is not merged yet"
    return 1
  fi

  echo "Merge commit: ${merge_sha}"

  # Check for deployment workflow
  local runs_json
  runs_json=$(gh run list -R "${REPO}" --workflow="production-release.yml" --json databaseId,status,conclusion,headSha --limit 10)

  local deployment_run
  deployment_run=$(echo "${runs_json}" | jq -r ".[] | select(.headSha == \"${merge_sha}\") | .databaseId" | head -1)

  if [[ -z "${deployment_run}" ]]; then
    echo "⚠ No deployment workflow found for this merge"
    return 1
  fi

  local status
  status=$(echo "${runs_json}" | jq -r ".[] | select(.databaseId == ${deployment_run}) | .status")

  local conclusion
  conclusion=$(echo "${runs_json}" | jq -r ".[] | select(.databaseId == ${deployment_run}) | .conclusion // \"pending\"")

  echo "Deployment workflow #${deployment_run}: ${status} (${conclusion})"

  if [[ "${conclusion}" == "success" ]]; then
    echo "✓ Deployment completed successfully"
    return 0
  elif [[ "${conclusion}" == "failure" ]]; then
    echo "❌ Deployment failed"
    return 1
  else
    echo "⚠ Deployment in progress"
    return 2
  fi
}

# Validate complete E2E flow
validate_e2e_complete() {
  local issue="$1"

  echo "Validating complete E2E flow for issue #${issue}..."
  echo ""

  local all_passed=true

  # Phase 1: Admission
  if ! validate_admission "${issue}"; then
    all_passed=false
  fi
  echo ""

  # Phase 2: Queue
  if ! validate_queue "${issue}"; then
    all_passed=false
  fi
  echo ""

  # Phase 3: Worker processing
  if ! validate_running "${issue}"; then
    all_passed=false
  fi
  echo ""

  # Phase 4: PR creation
  local pr
  if pr=$(validate_pr_creation "${issue}"); then
    echo ""

    # Phase 5: Checks
    validate_checks "${pr}"
    echo ""

    # Phase 6: Merge eligibility
    validate_merge_eligibility "${pr}"
    echo ""

    # Phase 7: Auto-merge
    validate_automerge "${pr}"
    echo ""

    # Phase 8: Deployment
    local pr_state
    pr_state=$(gh pr view "${pr}" -R "${REPO}" --json state --jq '.state')

    if [[ "${pr_state}" == "MERGED" ]]; then
      validate_deployment "${pr}"
    else
      echo "⚠ PR not merged yet, skipping deployment validation"
    fi
  else
    all_passed=false
  fi

  echo ""
  if [[ "${all_passed}" == "true" ]]; then
    echo "✓ E2E validation PASSED"
    return 0
  else
    echo "❌ E2E validation FAILED or INCOMPLETE"
    return 1
  fi
}

# Get phase status summary
get_phase_status() {
  local issue="$1"

  local labels
  labels=$(gh issue view "${issue}" -R "${REPO}" --json labels --jq '.labels[].name' | tr '\n' ',')

  if echo "${labels}" | grep -qE "scc-failed|scc-rejected"; then
    echo "FAILED"
  elif echo "${labels}" | grep -q "scc-merged"; then
    echo "COMPLETE"
  elif echo "${labels}" | grep -q "scc-running"; then
    echo "RUNNING"
  elif echo "${labels}" | grep -q "scc-queued"; then
    echo "QUEUED"
  elif echo "${labels}" | grep -q "scc-accepted"; then
    echo "ACCEPTED"
  elif echo "${labels}" | grep -q "ready-to-implement"; then
    echo "PENDING_ADMISSION"
  else
    echo "CREATED"
  fi
}

# Show usage
show_usage() {
  cat <<EOF
Usage: $0 <command> <issue|pr>

Commands:
  admission <issue>     - Validate admission phase
  queue <issue>         - Validate queue phase
  running <issue>       - Validate worker processing
  pr <issue>            - Validate PR creation
  checks <pr>           - Validate CI checks
  merge <pr>            - Validate merge eligibility
  automerge <pr>        - Validate auto-merge status
  deployment <pr>       - Validate deployment
  e2e <issue>           - Validate complete E2E flow
  status <issue>        - Get current phase status

Examples:
  $0 e2e 1234
  $0 checks 567
  $0 status 1234

EOF
}

main() {
  if [[ $# -lt 2 ]]; then
    show_usage
    exit 1
  fi

  local command="$1"
  local identifier="$2"

  case "${command}" in
    admission)
      validate_admission "${identifier}"
      ;;
    queue)
      validate_queue "${identifier}"
      ;;
    running)
      validate_running "${identifier}"
      ;;
    pr)
      validate_pr_creation "${identifier}"
      ;;
    checks)
      validate_checks "${identifier}"
      ;;
    merge)
      validate_merge_eligibility "${identifier}"
      ;;
    automerge)
      validate_automerge "${identifier}"
      ;;
    deployment)
      validate_deployment "${identifier}"
      ;;
    e2e)
      validate_e2e_complete "${identifier}"
      ;;
    status)
      get_phase_status "${identifier}"
      ;;
    *)
      echo "Unknown command: ${command}"
      show_usage
      exit 1
      ;;
  esac
}

main "$@"
