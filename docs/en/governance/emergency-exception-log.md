# Emergency Exception Log

**Purpose**: Audit trail of all emergency bypasses of \`main\` branch protection baseline.

## Log Format

Each exception must be logged with:
- **Date/Time**: When the exception was applied
- **Approver**: Incident Commander or authorized approver
- **Reason**: Brief description of emergency
- **Incident Ticket**: Link to incident or security ticket
- **Setting Modified**: Which protection setting was temporarily disabled
- **Duration**: How long the bypass was active
- **Remediation**: Follow-up PR and validation status

## Exception Entries

---

### [Example - Delete this entry]

**Date/Time**: 2026-01-15 03:42 UTC  
**Approver**: Jane Doe (Incident Commander)  
**Reason**: SEV-1 production outage - critical hotfix required  
**Incident Ticket**: INC-2026-001  
**Setting Modified**: Required status checks (temporarily disabled Test Suite check)  
**Duration**: 47 minutes (03:42 - 04:29 UTC)  
**Remediation**:
- Protection re-enabled: 04:29 UTC
- Follow-up PR with full validation: #1234 (merged 2026-01-15 09:15 UTC)
- Post-mortem: DOCS-2026-002

**Audit Notes**: Hotfix restored service in 47 minutes. Follow-up PR passed all gates. Exception justified and properly handled.

---

### [Add new entries below - newest first]

**Date/Time**: _____________  
**Approver**: _____________  
**Reason**: _____________  
**Incident Ticket**: _____________  
**Setting Modified**: _____________  
**Duration**: _____________  
**Remediation**:
- Protection re-enabled: _____________
- Follow-up PR: _____________
- Post-mortem**: _____________

**Audit Notes**: _____________

---

## Exception Statistics (Current Quarter)

| Quarter | Total Exceptions | Avg Duration | Most Common Reason | Compliance Score |
|---------|------------------|--------------|-------------------|------------------|
| Q2 2026 | 0 | N/A | N/A | 100% |
| Q1 2026 | 1 | 47 min | SEV-1 outage | 99.9% |

## Review Notes

### Q2 2026 Review (Scheduled: 2026-07-01)
- Review all Q2 exceptions
- Identify patterns requiring process improvements
- Update baseline if recurring bypasses indicate missing flexibility

---

**Note**: This log is audited quarterly by Platform Team and Security Team.
