# Unified Security Severity Policy

## Purpose

Define a single, consistent severity classification for all security-related findings across Homedir, regardless of source (dependency scan, SAST, DAST, penetration test, manual report).

## Severity Levels

### Critical (CVSS 9.0-10.0)

**Definition**: Actively exploitable vulnerability with remote code execution, authentication bypass, or data exfiltration potential.

**Response**: Fix within 24 hours. Emergency release. Block all deployments until resolved.

**Examples**:
- Remote Code Execution (RCE) in production dependency
- SQL injection in authenticated endpoint
- Authentication bypass in API gateway
- Hardcoded credential in committed code

### High (CVSS 7.0-8.9)

**Definition**: Exploitable vulnerability with significant impact on confidentiality, integrity, or availability.

**Response**: Fix within 72 hours. Next patch release. Block production deployments.

**Examples**:
- Cross-Site Scripting (XSS) in user-facing components
- Insecure direct object reference (IDOR)
- Server-Side Request Forgery (SSRF)
- Dependency with known active exploit

### Medium (CVSS 4.0-6.9)

**Definition**: Vulnerability with limited exploitability or impact; requires specific conditions.

**Response**: Fix within 14 days. Next minor release. Does not block deployments.

**Examples**:
- Information disclosure via error messages
- Missing security headers
- Outdated dependency without active exploit
- Weak but not broken cryptography

### Low (CVSS 0.1-3.9)

**Definition**: Minor security findings, informational, or best-practice violations.

**Response**: Fix within 90 days. Next major release. Backlog item.

**Examples**:
- Missing rate limiting on low-impact endpoint
- Verbose server banner
- Optional security header missing
- Informational scan finding

## Classification by Source

| Source | Tool | Default Severity | Gate |
|--------|------|-----------------|------|
| Dependency scan | pip-audit, Dependabot | High+ blocks CI | quality-gates.yml |
| SAST | Bandit, CodeQL | High+ blocks CI | quality-gates.yml |
| Secret scan | Gitleaks, TruffleHog | Critical blocks commit | pre-commit hook |
| DAST | OWASP ZAP | High+ blocks release | security-advisory.yml |
| Manual report | Security advisory | Per triage | Security tab |

## Escalation Path

1. **Critical/High**: Notify maintainers immediately via security channel
2. **Medium**: File issue with severity label, triage within 7 days
3. **Low**: File issue with severity label, triage within 30 days

## Related Documents

- [Security Policy](../../SECURITY.md)
- [Security Gates](security-gates.md)
- Issue #861 (this document)
