#!/usr/bin/env bash
# Configure branch protection rules for main branch
# Requires: gh CLI authenticated with admin permissions

set -euo pipefail

REPO="${1:-$(gh repo view --json nameWithOwner -q .nameWithOwner)}"
BRANCH="${2:-main}"

echo "Configuring branch protection for ${REPO}:${BRANCH}..."

# Check if gh CLI is authenticated
if ! gh auth status >/dev/null 2>&1; then
  echo "Error: gh CLI not authenticated. Run 'gh auth login' first."
  exit 1
fi

# Configure branch protection rule
gh api \
  --method PUT \
  -H "Accept: application/vnd.github+json" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  "/repos/${REPO}/branches/${BRANCH}/protection" \
  -f required_status_checks[strict]=true \
  -f required_status_checks[contexts][]=Build & Verify \
  -f required_status_checks[contexts][]=SBOM & Security Scan \
  -f required_status_checks[contexts][]=SAST - CodeQL Analysis \
  -f required_status_checks[contexts][]=Secret Scanning \
  -f enforce_admins=true \
  -f required_pull_request_reviews[dismiss_stale_reviews]=true \
  -f required_pull_request_reviews[require_code_owner_reviews]=false \
  -f required_pull_request_reviews[required_approving_review_count]=1 \
  -f required_pull_request_reviews[require_last_push_approval]=false \
  -f required_pull_request_reviews[bypass_pull_request_allowances][users][]='' \
  -f required_pull_request_reviews[bypass_pull_request_allowances][teams][]='' \
  -f restrictions=null \
  -f required_linear_history=true \
  -f allow_force_pushes=false \
  -f allow_deletions=false \
  -f block_creations=false \
  -f required_conversation_resolution=true \
  -f lock_branch=false \
  -f allow_fork_syncing=true

echo ""
echo "Branch protection configured successfully!"
echo ""
echo "Protection settings applied:"
echo "  - Require status checks to pass before merging"
echo "    * Build & Verify"
echo "    * SBOM & Security Scan"
echo "    * SAST - CodeQL Analysis"
echo "    * Secret Scanning"
echo "  - Require branches to be up to date before merging"
echo "  - Require pull request reviews (1 approval)"
echo "  - Dismiss stale reviews when new commits are pushed"
echo "  - Require conversation resolution before merging"
echo "  - Require linear history (no merge commits)"
echo "  - Enforce all restrictions for administrators"
echo "  - Do not allow force pushes"
echo "  - Do not allow deletions"
echo ""
echo "To view current protection status:"
echo "  gh api /repos/${REPO}/branches/${BRANCH}/protection"
