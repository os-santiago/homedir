#!/usr/bin/env bash
# Update GitHub repository ruleset to match documented requirements
# Issue: #988
# Documentation: docs/governance/STATUS_CHECK_MATRIX.md

set -euo pipefail

RULESET_ID=9071701
REPO="os-santiago/homedir"

echo "🔧 Branch Protection Ruleset Update Script"
echo "Repository: $REPO"
echo "Ruleset ID: $RULESET_ID"
echo ""
echo "This script updates the GitHub repository ruleset to enforce:"
echo "  ✓ 6 universal required status checks"
echo "  ✓ Conventional Commits pattern"
echo "  ✓ Required PR conversation resolution"
echo "  ✓ Bypass mode: pull_request only"
echo ""
echo "⚠️  Run this script only after reviewing BRANCH_PROTECTION_IMPLEMENTATION.md"
echo ""
echo "Documentation: docs/governance/STATUS_CHECK_MATRIX.md"
echo "Implementation Guide: docs/governance/BRANCH_PROTECTION_IMPLEMENTATION.md"
echo ""

# Fetch current ruleset
echo "📥 Fetching current ruleset..."
CURRENT=$(gh api repos/$REPO/rulesets/$RULESET_ID)

# Create updated ruleset payload with required status checks
cat > /tmp/ruleset-update.json <<'EOF'
{
  "name": "Main Branch Protection",
  "enforcement": "active",
  "conditions": {
    "ref_name": {
      "exclude": [],
      "include": ["~DEFAULT_BRANCH"]
    }
  },
  "rules": [
    {
      "type": "deletion"
    },
    {
      "type": "non_fast_forward"
    },
    {
      "type": "pull_request",
      "parameters": {
        "required_approving_review_count": 0,
        "dismiss_stale_reviews_on_push": false,
        "required_reviewers": [],
        "require_code_owner_review": false,
        "require_last_push_approval": false,
        "required_review_thread_resolution": true,
        "allowed_merge_methods": ["merge", "squash", "rebase"]
      }
    },
    {
      "type": "required_status_checks",
      "parameters": {
        "strict_required_status_checks_policy": false,
        "required_status_checks": [
          {
            "context": "PR Quality — Suite / style",
            "integration_id": 15368
          },
          {
            "context": "PR Quality — Suite / static",
            "integration_id": 15368
          },
          {
            "context": "PR Quality — Suite / arch",
            "integration_id": 15368
          },
          {
            "context": "PR Quality — Suite / tests_cov",
            "integration_id": 15368
          },
          {
            "context": "PR Quality — Suite / deps",
            "integration_id": 15368
          },
          {
            "context": "PR CI (Build, Native, SBOM/Scan) / sbom",
            "integration_id": 15368
          }
        ]
      }
    },
    {
      "type": "commit_message_pattern",
      "parameters": {
        "operator": "starts_with",
        "pattern": "^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)(\\([a-z0-9-]+\\))?: .+",
        "name": "Conventional Commits",
        "negate": false
      }
    },
    {
      "type": "copilot_code_review",
      "parameters": {
        "review_on_push": false,
        "review_draft_pull_requests": false
      }
    }
  ],
  "bypass_actors": [
    {
      "actor_id": 5,
      "actor_type": "RepositoryRole",
      "bypass_mode": "pull_request"
    }
  ]
}
EOF

echo ""
echo "📤 Updating ruleset $RULESET_ID..."
gh api -X PUT repos/$REPO/rulesets/$RULESET_ID \
  --input /tmp/ruleset-update.json

echo ""
echo "✅ Ruleset updated successfully!"
echo ""
echo "Verification steps:"
echo "1. Visit https://github.com/$REPO/rules/$RULESET_ID"
echo "2. Confirm 6 required status checks are enforced"
echo "3. Confirm 'Require conversation resolution before merging' is enabled"
echo "4. Confirm bypass mode is 'For pull requests only'"
echo "5. Confirm commit message pattern matches Conventional Commits"
echo ""
echo "📋 Next steps:"
echo "  - Monitor next PR to verify checks are enforced"
echo "  - Update docs/governance/BRANCH_PROTECTION_IMPLEMENTATION.md with execution timestamp"

# Cleanup
rm -f /tmp/ruleset-update.json
