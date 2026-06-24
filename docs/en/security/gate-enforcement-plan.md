# Security Gate Enforcement Plan

## Current Gate Inventory

| Gate | Workflow | Step | Current Mode | Severity Cutoff |
|------|----------|------|-------------|-----------------|
| SBOM + Security Scan | quality-gates.yml | anchore/sbom-action | Permissive | - |
| SAST CodeQL | quality-gates.yml | github/codeql-action | Advisory | Error only |
| Dependency Review | quality-gates.yml | actions/dependency-review-action | Advisory | Moderate+ |
| Trivy FS Scan | quality-gates.yml | aquasecurity/trivy-action | Permissive | - |
| Secret Scanning | security-advisory.yml | trufflesecurity/trufflehog | Advisory | Verified only |
| CodeQL Init/Analyze | security-advisory.yml | github/codeql-action | Advisory | Error only |

## Target Mode per Gate

| Gate | Target Mode | Severity Cutoff | Rationale |
|------|-------------|-----------------|-----------|
| SBOM + Security Scan | Enforcing | High+ | Block known critical/high CVEs in production deps |
| CodeQL (quality-gates.yml) | Enforcing | Error+Warning | Block code quality issues and potential vulns |
| Dependency Review | Enforcing | Moderate+ | Block dependency changes with moderate+ risk |
| Trivy FS Scan | Enforcing | High+ | Block filesystem-level vulnerabilities |
| TruffleHog | Enforcing | Any verified | Block any committed secret |
| CodeQL (security-advisory.yml) | Enforcing | Error+Warning | Full coverage for scheduled security scans |

## Transition Plan

### Phase 1: Immediate (Week 1)

| Gate | Action | Risk |
|------|--------|------|
| TruffleHog | Enable `--fail` flag for verified findings | Low - verified secrets are always blocking |
| CodeQL (quality) | Set `fail-on: warning` | Medium - may catch existing warnings |

### Phase 2: Short-term (Weeks 2-3)

| Gate | Action | Risk |
|------|--------|------|
| Dependency Review | Set `fail-on-severity: moderate` | Medium - FP risk in transitive deps |
| SBOM Scan | Set `severity-threshold: high` | Medium - depends on up-to-date vuln DB |

### Phase 3: Medium-term (Weeks 4-6)

| Gate | Action | Risk |
|------|--------|------|
| Trivy | Set `exit-code: 1` for HIGH+ | Medium - may need baseline tuning |
| CodeQL (security) | Set `fail-on: warning` | Low - fixes from Phase 1 already applied |

## Exception Policy

### Waiver Format

Each exception must include:
- Gate name and finding ID
- Severity and CVSS score
- Business justification (max 200 words)
- Remediation plan and target date
- Approver: must be a maintainer
- Expiration: max 30 days

### Exception Process

1. Create issue with label `security-waiver`
2. Attach waiver documentation
3. Maintainer review and approval
4. Update exception log
5. Auto-reopen after expiration if not remediated

### Auto-Revalidation

Exceptions are automatically flagged for revalidation:
- Every 7 days for Critical waivers
- Every 14 days for High waivers
- At expiration for Medium/Low

## Enforcement Configuration

### quality-gates.yml changes

```yaml
# Dependency Review: enforce on moderate+
- name: Dependency Review
  uses: actions/dependency-review-action@v4
  with:
    fail-on-severity: moderate

# CodeQL: enforce on warnings
- name: CodeQL Analysis
  uses: github/codeql-action/analyze@v4
  with:
    fail-on: warning
```

### security-advisory.yml changes

```yaml
# TruffleHog: fail on any verified finding
- name: Secret Scanning
  uses: trufflesecurity/trufflehog@main
  with:
    fail: true
```

## Related Documents

- [Security Severity Policy](severity-policy.md)
- [Security Policy](../../SECURITY.md)
- Issue #860 (this document)
