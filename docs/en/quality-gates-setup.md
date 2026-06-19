# Quality Gates and Branch Protection Setup

This document describes the CI/CD quality gates, deployment tests, and branch protection rules configured for the homedir repository.

## Overview

The repository implements comprehensive quality gates to ensure code quality, security, and compliance before production deployment.

## Quality Gates

### 1. SBOM & Vulnerability Scanning

**Workflow:** `.github/workflows/quality-gates.yml`

- **SBOM Generation**: Uses Anchore SBOM Action to generate Software Bill of Materials in SPDX JSON format
- **Vulnerability Scanning**: Scans Docker images for known vulnerabilities using Anchore Scan Action
- **Severity Threshold**: Medium and above
- **SARIF Upload**: Results uploaded to GitHub Security tab for tracking
- **Artifacts**: SBOM and scan reports stored for 30 days

**Security Gating Mode:**
- Set via repository variable `SECURITY_GATING`
- `permissive` (default): Scan failures don't block the workflow
- `enforcing`: Scan failures cause the job to fail

### 2. SAST (Static Application Security Testing)

**Tool:** GitHub CodeQL

- **Languages**: Java/Kotlin
- **Query Suites**: `security-extended`, `security-and-quality`
- **Analysis**: Identifies security vulnerabilities, bugs, and code quality issues
- **Results**: Uploaded to GitHub Security tab

### 3. Secret Scanning

**Tool:** TruffleHog

- **Scope**: Full repository history
- **Mode**: Only verified secrets trigger alerts
- **Integration**: Runs on every PR and push to main

### 4. Dependency Review

**Tool:** GitHub Dependency Review Action

- **Trigger**: Pull requests only
- **Severity Threshold**: Moderate and above
- **License Checks**: Denies GPL-2.0 and GPL-3.0 licenses
- **Results**: Comments on PRs with dependency changes

## Deployment Tests

### Pre-Production Verification

**Workflow:** `.github/workflows/release.yml`

The production release workflow includes comprehensive testing:

1. **Build & Test Gate** (Line 55-60):
   - Runs `mvn clean verify` before any deployment
   - Includes all unit and integration tests
   - Blocks release if tests fail

2. **Production Build** (Line 114-116):
   - Removed `-DskipTests` flag
   - Tests now run during production build
   - Ensures test coverage in release artifacts

3. **Health Check Verification** (Line 295-315):
   - Post-deployment health check at `/q/health`
   - 18 attempts for normal deployment
   - 36 attempts if SSH deploy fails
   - 10-second intervals between checks

### Continuous Validation

**Workflow:** `.github/workflows/pr-check.yml`

- Runs on every PR to main
- Executes `pr_preflight.sh` script
- Validates build, tests, and smoke tests
- 25-minute timeout for comprehensive checks

## Branch Protection Rules

### Configuration Script

**Location:** `scripts/ci/configure_branch_protection.sh`

Run this script to configure branch protection for the `main` branch:

```bash
./scripts/ci/configure_branch_protection.sh
```

### Protection Settings

The script configures the following rules for the `main` branch:

1. **Required Status Checks:**
   - Build & Verify
   - SBOM & Security Scan
   - SAST - CodeQL Analysis
   - Secret Scanning
   - Branches must be up to date before merging

2. **Pull Request Reviews:**
   - Require 1 approval before merge
   - Dismiss stale reviews on new commits
   - No code owner review required (configurable)

3. **Code Quality:**
   - Require conversation resolution before merging
   - Require linear history (no merge commits)

4. **Enforcement:**
   - Enforce all restrictions for administrators
   - No force pushes allowed
   - No branch deletions allowed

### Manual Configuration

If you prefer to configure branch protection manually via GitHub UI:

1. Go to **Settings** > **Branches**
2. Add rule for `main` branch
3. Configure the following:
   - ✅ Require a pull request before merging
   - ✅ Require approvals: 1
   - ✅ Dismiss stale pull request approvals when new commits are pushed
   - ✅ Require status checks to pass before merging
   - ✅ Require branches to be up to date before merging
   - ✅ Status checks required:
     - Build & Verify
     - SBOM & Security Scan
     - SAST - CodeQL Analysis
     - Secret Scanning
   - ✅ Require conversation resolution before merging
   - ✅ Require linear history
   - ✅ Include administrators
   - ✅ Do not allow bypassing the above settings

## Documentation Updates Process

### Automated PR Creation

**Workflow:** `.github/workflows/update-docs-on-release.yml`

Previously, this workflow pushed directly to `main`. It now:

1. Creates a feature branch: `docs/release-{TAG}-sync`
2. Updates `CHANGELOG.md` and `RELEASE_NOTES.md`
3. Creates a PR for review
4. Requires approval before merging (via branch protection)

This ensures:
- No direct commits to `main`
- Documentation changes are reviewed
- CI/CD quality gates run on documentation updates
- Audit trail for all changes

## Security Reports

### Viewing Reports

1. **GitHub Security Tab**: Navigate to **Security** > **Code scanning alerts**
2. **Workflow Artifacts**: Download from workflow run summary
3. **PR Comments**: Dependency Review posts findings on PRs

### Report Retention

- **Artifacts**: 30 days
- **Security Alerts**: Indefinite (until resolved)
- **SARIF Results**: Indefinite (in Security tab)

## Enabling Enforcing Mode

To enable strict security gating:

1. Go to **Settings** > **Secrets and variables** > **Actions** > **Variables**
2. Add or update repository variable:
   - Name: `SECURITY_GATING`
   - Value: `enforcing`

In enforcing mode:
- Vulnerability scan failures block PRs
- Medium+ severity vulnerabilities must be resolved
- Builds fail if security issues detected

## Troubleshooting

### Quality Gate Failures

1. **SBOM/Scan failures**: Check Security tab for vulnerability details
2. **CodeQL failures**: Review code scanning alerts in Security tab
3. **Secret scan failures**: Check TruffleHog output in workflow logs
4. **Dependency failures**: Review PR comments from Dependency Review

### Branch Protection

To view current protection status:

```bash
gh api /repos/OWNER/REPO/branches/main/protection
```

To update protection rules:

```bash
./scripts/ci/configure_branch_protection.sh
```

## References

- [Supply Chain Security](./features/supply-chain-security.md)
- [Security Hardening Baseline](./development/security-hardening-baseline.md)
- [CI/CD Pipeline](./ci-cd.md)
