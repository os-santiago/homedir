#!/usr/bin/env bash
# PR Unblock Automation - Detects and resolves PR merge blocks
#
# Usage: pr-unblock-automation.sh <pr_number>
#
# Detects common merge blocks and attempts to auto-resolve them:
# - Unresolved review conversations (CodeRabbit, etc)
# - Stale approvals that need refresh
# - Missing required labels

set -euo pipefail

REPO="${HOMEDIR_SDLC_REPO:-os-santiago/homedir}"
PR_NUMBER="${1:-}"

log() {
  echo "[$(date -u '+%Y-%m-%dT%H:%M:%SZ')] [pr-unblock] $*" >&2
}

if [[ -z "${PR_NUMBER}" ]]; then
  echo "Usage: $0 <pr_number>" >&2
  exit 1
fi

log "checking PR #${PR_NUMBER} for merge blocks"

# Get PR state
PR_STATE=$(gh pr view "${PR_NUMBER}" --repo "${REPO}" --json mergeStateStatus,mergeable,autoMergeRequest --jq '{merge_state:.mergeStateStatus,mergeable,auto_merge:(.autoMergeRequest!=null)}')

MERGE_STATE=$(echo "${PR_STATE}" | jq -r '.merge_state')
MERGEABLE=$(echo "${PR_STATE}" | jq -r '.mergeable')
AUTO_MERGE=$(echo "${PR_STATE}" | jq -r '.auto_merge')

log "PR state: merge_state=${MERGE_STATE}, mergeable=${MERGEABLE}, auto_merge=${AUTO_MERGE}"

# If CLEAN, nothing to do
if [[ "${MERGE_STATE}" == "CLEAN" ]]; then
  log "PR #${PR_NUMBER} is CLEAN - no blocks detected"
  exit 0
fi

# If UNKNOWN, wait and retry
if [[ "${MERGE_STATE}" == "UNKNOWN" ]] || [[ "${MERGEABLE}" == "UNKNOWN" ]]; then
  log "PR #${PR_NUMBER} state is UNKNOWN - GitHub is processing, waiting 10s..."
  sleep 10
  exec "$0" "$@"  # Retry
fi

# Check for unresolved review conversations
log "checking for unresolved review conversations"

UNRESOLVED_THREADS=$(gh api graphql -f query="
query {
  repository(owner: \"${REPO%/*}\", name: \"${REPO#*/}\") {
    pullRequest(number: ${PR_NUMBER}) {
      reviewThreads(first: 50) {
        nodes {
          isResolved
          id
          comments(first: 1) {
            nodes {
              author {
                login
              }
            }
          }
        }
      }
    }
  }
}" --jq '.data.repository.pullRequest.reviewThreads.nodes[] | select(.isResolved==false) | {id,author:.comments.nodes[0].author.login}')

UNRESOLVED_COUNT=$(echo "${UNRESOLVED_THREADS}" | jq -s 'length')

if [[ "${UNRESOLVED_COUNT}" -gt 0 ]]; then
  log "found ${UNRESOLVED_COUNT} unresolved review conversation(s)"

  # Auto-resolve CodeRabbit conversations (safe to resolve)
  echo "${UNRESOLVED_THREADS}" | jq -r 'select(.author=="coderabbitai" or .author=="coderabbitai[bot]") | .id' | while read -r thread_id; do
    if [[ -n "${thread_id}" ]]; then
      log "auto-resolving CodeRabbit conversation: ${thread_id}"

      if gh api graphql -f query="
mutation {
  resolveReviewThread(input: {threadId: \"${thread_id}\") {
    thread {
      isResolved
    }
  }
}" --jq '.data.resolveReviewThread.thread.isResolved' >/dev/null 2>&1; then
        log "resolved conversation: ${thread_id}"
      else
        log "WARNING: failed to resolve conversation: ${thread_id}"
      fi
    fi
  done

  # Check for human conversations (don't auto-resolve these)
  HUMAN_THREADS=$(echo "${UNRESOLVED_THREADS}" | jq -s '[.[] | select(.author!="coderabbitai" and .author!="coderabbitai[bot]")] | length')

  if [[ "${HUMAN_THREADS}" -gt 0 ]]; then
    log "WARNING: ${HUMAN_THREADS} unresolved HUMAN conversation(s) - requires manual review"
    echo "${UNRESOLVED_THREADS}" | jq -s '.[] | select(.author!="coderabbitai" and .author!="coderabbitai[bot]")' >&2
    exit 1
  fi

  log "all bot conversations resolved, waiting for GitHub to update state..."
  sleep 5
  exec "$0" "$@"  # Retry to check if now unblocked
fi

# Check for other common blocks
case "${MERGE_STATE}" in
  BLOCKED)
    log "PR #${PR_NUMBER} is BLOCKED - checking specific reasons"

    # Check if missing required reviews
    REVIEW_DECISION=$(gh pr view "${PR_NUMBER}" --repo "${REPO}" --json reviewDecision --jq '.reviewDecision')
    log "review decision: ${REVIEW_DECISION}"

    if [[ "${REVIEW_DECISION}" == "" ]] || [[ "${REVIEW_DECISION}" == "null" ]]; then
      # Check if required reviews count > 0
      REQUIRED_REVIEWS=$(gh api repos/"${REPO}"/branches/main/protection --jq '.required_pull_request_reviews.required_approving_review_count // 0')

      if [[ "${REQUIRED_REVIEWS}" -gt 0 ]]; then
        log "ERROR: PR requires ${REQUIRED_REVIEWS} approval(s) - cannot auto-resolve"
        exit 1
      fi
    fi

    # If no specific reason found, log and exit
    log "WARNING: PR is BLOCKED but no auto-resolvable reason found"
    log "Manual investigation required"
    exit 1
    ;;

  DIRTY)
    log "ERROR: PR #${PR_NUMBER} has merge conflicts - cannot auto-resolve"
    log "Manual conflict resolution required"
    exit 1
    ;;

  UNSTABLE)
    log "WARNING: PR #${PR_NUMBER} has failing CI checks - cannot auto-resolve"
    exit 1
    ;;

  *)
    log "WARNING: Unknown merge state: ${MERGE_STATE}"
    exit 1
    ;;
esac

log "PR #${PR_NUMBER} unblock complete"
