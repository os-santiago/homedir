#!/usr/bin/env bash
# Update GitHub repository ruleset to match documented requirements
# Issue: #988
# Documentation: docs/governance/STATUS_CHECK_MATRIX.md

set -euo pipefail

RULESET_ID=9071701
REPO="os-santiago/homedir"

echo "🔧 Updating GitHub branch protection ruleset..."
echo "Repository: $REPO"
echo "Ruleset ID: $RULESET_ID"
echo ""

# Define the required status checks from STATUS_CHECK_MATRIX.md
# Universal Required Checks (All PRs)
REQUIRED_CHECKS='[
  {
    "context": "PR Quality — Suite / style",
    "integration_id": null
  },
  {
    "context": "PR Quality — Suite / static",
    "integration_id": null
  },
  {
    "context": "PR Quality — Suite / arch",
    "integration_id": null
  },
  {
    "context": "PR Quality — Suite / tests_cov",
    "integration_id": null
  },
  {
    "context": "PR Quality — Suite / deps",
    "integration_id": null
  },
  {
    "context": "PR CI (Build, Native, SBOM/Scan) / sbom",
    "integration_id": null
  }
]'

# Conventional Commits pattern
# Matches: feat:, fix:, docs:, chore:, test:, refactor:, style:, perf:, ci:, build:
COMMIT_PATTERN='^(feat|fix|docs|chore|test|refactor|style|perf|ci|build)(\(.+\))?: .{1,100}$'

echo "📋 Preparing ruleset update payload..."

# Build the complete ruleset update payload
cat > /tmp/ruleset-update.json <<EOF
{
  "name": "Main Branch Protection",
  "target": "branch",
  "enforcement": "active",
  "conditions": {
    "ref_name": {
      "include": ["~DEFAULT_BRANCH"],
      "exclude": []
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
      "type": "required_status_checks",
      "parameters": {
        "required_status_checks": $(echo "$REQUIRED_CHECKS"),
        "strict_required_status_checks_policy": true
      }
    },
    {
      "type": "pull_request",
      "parameters": {
        "required_approving_review_count": 0,
        "dismiss_stale_reviews_on_push": false,
        "require_code_owner_review": false,
        "require_last_push_approval": false,
        "required_review_thread_resolution": true,
        "allowed_merge_methods": ["merge", "squash", "rebase"]
      }
    },
    {
      "type": "commit_message_pattern",
      "parameters": {
        "name": "Conventional Commits",
        "negate": false,
        "operator": "regex",
        "pattern": "$COMMIT_PATTERN"
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

echo "✅ Payload prepared"
echo ""
echo "🔍 Current ruleset state:"
gh api repos/$REPO/rulesets/$RULESET_ID --jq '{
  name,
  enforcement,
  required_checks: [.rules[] | select(.type == "required_status_checks") | .parameters.required_status_checks[]?.context],
  commit_pattern: [.rules[] | select(.type == "commit_message_pattern") | .parameters.pattern],
  conversation_resolution: [.rules[] | select(.type == "pull_request") | .parameters.required_review_thread_resolution],
  bypass_mode: [.bypass_actors[].bypass_mode]
}'

echo ""
echo "🚀 Applying ruleset update..."

# Update the ruleset
gh api -X PUT repos/$REPO/rulesets/$RULESET_ID \
  --input /tmp/ruleset-update.json \
  > /tmp/ruleset-result.json

echo "✅ Ruleset updated successfully"
echo ""
echo "🔍 Updated ruleset state:"
cat /tmp/ruleset-result.json | jq '{
  name,
  enforcement,
  required_checks: [.rules[] | select(.type == "required_status_checks") | .parameters.required_status_checks[]?.context],
  commit_pattern: [.rules[] | select(.type == "commit_message_pattern") | .parameters.pattern],
  conversation_resolution: [.rules[] | select(.type == "pull_request") | .parameters.required_review_thread_resolution],
  bypass_mode: [.bypass_actors[].bypass_mode]
}'

echo ""
echo "✅ Branch protection ruleset updated to match documented requirements"
echo "📄 See: docs/governance/STATUS_CHECK_MATRIX.md"

# Cleanup
rm -f /tmp/ruleset-update.json /tmp/ruleset-result.json
