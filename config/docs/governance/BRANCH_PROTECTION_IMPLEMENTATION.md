# Branch Protection Ruleset Implementation Guide

## Overview

This document provides the implementation plan to align the actual GitHub repository ruleset with the documented configuration in `ruleset-main.json`.

**Related Issue**: #988  
**Parent Issue**: #838 (Quality Audit)  
**Status Check Matrix**: [STATUS_CHECK_MATRIX.md](./STATUS_CHECK_MATRIX.md)

## Current State (Before Implementation)

### Actual GitHub Ruleset (ID: 9071701 "Minimal Rules")

Retrieved via: `gh api repos/:owner/:repo/rulesets/9071701`

```json
{
  "id": 9071701,
  "name": "Minimal Rules",
  "enforcement": "active",
  "rules": [
    {"type": "deletion"},
    {"type": "non_fast_forward"},
    {
      "type": "pull_request",
      "parameters": {
        "required_approving_review_count": 0,
        "required_review_thread_resolution": false
      }
    }
  ],
  "bypass_actors": [
    {
      "actor_id": 5,
      "actor_type": "RepositoryRole",
      "bypass_mode": "always"
    }
  ]
}
```

### Critical Gaps Identified

| Protection | Documented (ruleset-main.json) | Actual (GitHub) | Status |
|------------|-------------------------------|-----------------|--------|
| **Required Status Checks** | 6 checks enforced | None | ❌ Missing |
| **Commit Message Pattern** | Conventional Commits regex | None | ❌ Missing |
| **Conversation Resolution** | Should be enabled | `false` | ❌ Disabled |
| **Bypass Mode** | `pull_request` only | `always` | ❌ Too permissive |

## Implementation Approach

**Note**: This is a documentation-only change. The actual GitHub ruleset update must be performed manually by a repository administrator using the GitHub web UI or the provided script.

### Recommended Steps

1. Review this document with repository maintainers
2. Use the script at `scripts/governance/update-branch-protection.sh` OR update via GitHub web UI
3. Validate changes using the checklist below
4. Close issue #988

## Validation Checklist

After implementation, verify:

- [ ] Run: `gh api repos/:owner/:repo/rulesets/9071701 | jq '.rules'`
- [ ] Confirm ruleset updates are documented
- [ ] Team notified of branch protection changes
- [ ] Issue #988 closed

## References

- [STATUS_CHECK_MATRIX.md](./STATUS_CHECK_MATRIX.md) - Required checks by change type
- [Conventional Commits](https://www.conventionalcommits.org/) - Commit message standard
- [GitHub Rulesets Documentation](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets)
- Issue #838 - Quality audit parent
- Issue #988 - This implementation

---

**Prepared By**: AI Agent (WOS)  
**Date**: 2026-06-24
