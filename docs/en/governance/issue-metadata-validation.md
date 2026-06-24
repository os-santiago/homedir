# Issue Metadata Validation Specification

**Issue:** #845  
**Status:** Implemented  
**Last Updated:** 2026-06-24

## Overview

Automated validation system for GitHub issue metadata ensuring consistent, high-quality issue tracking.

## Validation Rules

### Title
- **Minimum:** 10 characters
- **Maximum:** 200 characters  
- **Required:** Non-empty

###Body
- **Minimum:** 50 characters
- **Required sections:** Objective, Scope, Acceptance Criteria (warnings)

### Labels
- **Type:** One of bug, enhancement, documentation, question
- **Priority:** One of priority:P0, priority:P1, priority:P2, priority:P3

## Enforcement

**Current Mode:** Advisory (comments only, non-blocking)

**Rollout Plan:**
1. Phase 1 (4 weeks): Advisory - collect metrics
2. Phase 2 (2 weeks): Warning checks
3. Phase 3 (Ongoing): Enforcing - block incomplete issues

## Implementation

- **Workflow:** `.github/workflows/issue-metadata-validation.yml`
- **Validation Logic:** `scripts/ci/validate_issue_metadata.py`
- **Dependencies:** `scripts/ci/requirements-validation.txt`

## Quality Metrics

- **Completeness Rate Target:** ≥80%
- **Time to Valid Metadata:** ≤24 hours
- **Incomplete Rate Target:** ≤10%

---
*Closes #845*
