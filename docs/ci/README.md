# CI/CD Documentation

Documentation for Continuous Integration and Continuous Deployment processes.

## Available Documentation

- [AI Code Review](ai-code-review.md) - Automated code quality analysis using Claude AI
- [Action Versioning Policy](action-versioning-policy.md) - GitHub Actions version management

## Related Documentation

- [Main CI/CD Pipeline](../en/ci-cd.md) - Overview of the entire pipeline
- [Quality Gates Workflow](../../.github/workflows/quality-gates.yml) - Code quality enforcement
- [PR Check Workflow](../../.github/workflows/pr-check.yml) - Pull request validation

## Quick Links

### Workflows
- AI Code Review: `.github/workflows/ai-code-review.yml`
- Quality Gates: `.github/workflows/quality-gates.yml`
- PR Checks: `.github/workflows/pr-check.yml`
- Security Advisory: `.github/workflows/security-advisory.yml`

### Scripts
- AI Code Review: `scripts/ci/ai_code_review.py`
- Pipeline Health: `scripts/ci/pipeline_health_report.py`
- Issue Metadata Validation: `scripts/ci/validate_issue_metadata.py`

### Tests
- AI Code Review Tests: `tests/ci/test_ai_code_review.py`
