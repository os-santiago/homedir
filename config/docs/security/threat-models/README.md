# STRIDE Threat Model Documentation

**Version:** 1.0  
**Last Updated:** 2026-06-23  
**Owner:** Security Team  
**Issue Reference:** #855

## Table of Contents

- [Overview](#overview)
- [STRIDE Methodology](#stride-methodology)
- [Threat Model Domains](#threat-model-domains)
- [Severity and Risk Matrix](#severity-and-risk-matrix)
- [Threat Model Structure](#threat-model-structure)
- [Usage Guidelines](#usage-guidelines)
- [Maintenance and Review](#maintenance-and-review)
- [Compliance Mapping](#compliance-mapping)
- [Tool Integration](#tool-integration)
- [References](#references)

---

## Overview

This directory contains comprehensive STRIDE-based threat models for the platform's critical functional domains. Threat modeling is a structured approach to identifying, quantifying, and addressing security threats during the design and development lifecycle.

### Purpose

- **Proactive Security:** Identify threats before they become vulnerabilities
- **Risk Prioritization:** Focus security efforts on highest-impact areas
- **Compliance:** Meet regulatory and industry security requirements
- **Security Awareness:** Educate development teams on domain-specific risks
- **Incident Prevention:** Reduce likelihood and impact of security incidents

### Key Benefits

1. **Systematic Coverage:** STRIDE ensures all threat categories are considered
2. **Actionable Mitigations:** Each threat includes specific, implementable controls
3. **Risk Quantification:** CVSS scores enable objective risk comparison
4. **Compliance Alignment:** Threats mapped to OWASP ASVS, PCI DSS, GDPR, SOC 2
5. **Living Documentation:** Regular reviews keep models current with application changes

---

## STRIDE Methodology

STRIDE is a threat modeling framework developed by Microsoft that categorizes security threats into six types. Each letter represents a different threat category that violates a specific security property.

### STRIDE Categories

| Category | Security Property Violated | Description | Examples |
|----------|---------------------------|-------------|----------|
| **S**poofing | Authentication | Impersonating a user, process, or system | Fake login, session hijacking, email spoofing |
| **T**ampering | Integrity | Unauthorized modification of data or code | SQL injection, XSS, price manipulation, config tampering |
| **R**epudiation | Non-repudiation | Denying an action without proof | Missing audit logs, log tampering, unsigned transactions |
| **I**nformation Disclosure | Confidentiality | Exposing information to unauthorized parties | Data leaks, IDOR, exposed PII, credential theft |
| **D**enial of Service | Availability | Degrading or blocking service availability | DDoS, resource exhaustion, account lockout abuse |
| **E**levation of Privilege | Authorization | Gaining capabilities without authorization | Privilege escalation, role manipulation, authorization bypass |

### Why STRIDE?

- **Comprehensive:** Covers all major threat categories systematically
- **Well-Established:** Industry standard with extensive documentation and tools
- **Structured:** Provides consistent framework across different domains
- **Educational:** Easy to understand and teach to development teams
- **Tool Support:** Integrated into Microsoft Threat Modeling Tool and other platforms

### STRIDE Application Process

1. **Identify Assets:** What data, systems, or functions need protection?
2. **Map Trust Boundaries:** Where does data/control flow across trust levels?
3. **Enumerate Threats:** Apply each STRIDE category to each boundary crossing
4. **Assess Risk:** Evaluate likelihood and impact for each threat
5. **Define Mitigations:** Specify security controls to address threats
6. **Validate:** Ensure mitigations adequately reduce risk to acceptable levels

---

## Threat Model Domains

This repository contains threat models for five critical functional domains:

### 1. Call for Papers (CFP) - Proposal Submission and Review

**File:** `cfp-threat-model.yaml`  
**Risk Score:** 6.9/10  
**Key Concerns:**
- Proposal confidentiality (intellectual property protection)
- Blind review integrity
- Fraudulent speaker submissions
- Unauthorized access to review scores

**Highest Priority Threats:**
- CFP-T004: Unauthorized access to proposals/scores (CRITICAL)
- CFP-T006: Unauthorized review/selection access (HIGH)

### 2. Community Interaction and User-Generated Content

**File:** `community-threat-model.yaml`  
**Risk Score:** 7.8/10  
**Key Concerns:**
- Cross-site scripting (XSS) in user content
- Content moderation integrity
- User impersonation
- Spam and abuse at scale

**Highest Priority Threats:**
- COMM-T002: XSS and HTML injection (CRITICAL) - Issue #856 (CSP headers)
- COMM-T004: Unauthorized draft access (HIGH)
- COMM-T006: Unauthorized moderation access (HIGH)

### 3. Authentication and Authorization

**File:** `auth-threat-model.yaml`  
**Risk Score:** 9.2/10  
**Key Concerns:**
- Credential stuffing and brute force attacks
- Session hijacking
- Authorization bypass (Issue #854: endpoint authorization matrix)
- Privilege escalation

**Highest Priority Threats:**
- AUTH-T001: Credential stuffing (CRITICAL)
- AUTH-T002: Session hijacking (CRITICAL)
- AUTH-T003: Authorization matrix bypass - Issue #854 (CRITICAL)
- AUTH-T007: Privilege escalation (CRITICAL)

### 4. Administrative Functions

**File:** `admin-threat-model.yaml`  
**Risk Score:** 9.6/10  
**Key Concerns:**
- Admin account compromise
- Configuration tampering
- Mass data exfiltration
- Insufficient admin audit logging

**Highest Priority Threats:**
- ADMIN-T001: Admin account compromise (CRITICAL)
- ADMIN-T002: Configuration tampering (CRITICAL)
- ADMIN-T004: Mass data exfiltration (CRITICAL)

### 5. Financial Transactions and Payment Economy

**File:** `economy-threat-model.yaml`  
**Risk Score:** 9.8/10  
**Key Concerns:**
- Payment card testing (carding)
- Transaction amount manipulation
- PCI DSS compliance violations
- Wallet race conditions and double-spending

**Highest Priority Threats:**
- ECON-T001: Card testing and carding (CRITICAL)
- ECON-T002: Transaction manipulation (CRITICAL)
- ECON-T004: Payment card data leakage (CRITICAL)

---

## Severity and Risk Matrix

### Severity Rating System

Threat severity is determined by combining **Likelihood** and **Impact** ratings:

| Likelihood | Impact | Severity | CVSS Range | Priority |
|------------|--------|----------|------------|----------|
| CRITICAL | CRITICAL | CRITICAL | 9.0 - 10.0 | P0 |
| HIGH | CRITICAL | CRITICAL | 8.0 - 8.9 | P0 |
| CRITICAL | HIGH | CRITICAL | 8.0 - 8.9 | P0 |
| MEDIUM | CRITICAL | HIGH | 7.0 - 7.9 | P1 |
| HIGH | HIGH | HIGH | 7.0 - 7.9 | P1 |
| CRITICAL | MEDIUM | HIGH | 7.0 - 7.9 | P1 |
| LOW | CRITICAL | HIGH | 6.0 - 6.9 | P1 |
| MEDIUM | MEDIUM | MEDIUM | 4.0 - 5.9 | P2 |
| HIGH | LOW | MEDIUM | 4.0 - 5.9 | P2 |
| LOW | LOW | LOW | 0.1 - 3.9 | P3 |

### Likelihood Assessment Criteria

| Likelihood | Description | Criteria |
|------------|-------------|----------|
| **CRITICAL** | Attack is trivial and actively exploited | - Public exploits available<br>- Automated attack tools exist<br>- No authentication required<br>- Common vulnerability (OWASP Top 10) |
| **HIGH** | Attack is easy and likely to occur | - Low skill required<br>- Common attack pattern<br>- Weak controls in place<br>- Similar attacks observed in industry |
| **MEDIUM** | Attack requires moderate effort | - Moderate skill required<br>- Some security controls present<br>- Requires specific conditions<br>- Occasional industry examples |
| **LOW** | Attack is difficult or rare | - High skill required<br>- Strong controls in place<br>- Multiple conditions needed<br>- Rarely observed in industry |

### Impact Assessment Criteria

| Impact | Description | Business Impact | Data Impact |
|--------|-------------|-----------------|-------------|
| **CRITICAL** | Catastrophic damage to business | - Complete system compromise<br>- Massive financial loss (>$1M)<br>- Regulatory shutdown<br>- Permanent reputation damage | - All user data exposed<br>- Payment card data breach<br>- Complete data loss<br>- Intellectual property theft |
| **HIGH** | Significant damage | - Major functionality loss<br>- Significant financial loss ($100K-$1M)<br>- Major compliance violations<br>- Serious reputation damage | - Large-scale PII exposure<br>- Financial data breach<br>- Major data corruption<br>- Trade secret exposure |
| **MEDIUM** | Moderate damage | - Partial functionality loss<br>- Moderate financial loss ($10K-$100K)<br>- Minor compliance issues<br>- Moderate reputation impact | - Limited PII exposure<br>- User data leak (non-financial)<br>- Minor data corruption<br>- Internal data exposure |
| **LOW** | Minor damage | - Minimal functionality impact<br>- Low financial loss (<$10K)<br>- No compliance violations<br>- Minor reputation impact | - Minimal data exposure<br>- Non-sensitive data only<br>- Easily recoverable<br>- Public data only |

### CVSS v3.1 Scoring

All threats include Common Vulnerability Scoring System (CVSS) v3.1 scores for objective risk quantification:

- **CVSS 9.0-10.0:** Critical severity - immediate action required
- **CVSS 7.0-8.9:** High severity - prioritized remediation
- **CVSS 4.0-6.9:** Medium severity - scheduled remediation
- **CVSS 0.1-3.9:** Low severity - tracked for future consideration

CVSS calculator: https://www.first.org/cvss/calculator/3.1

### Common Weakness Enumeration (CWE)

Each threat includes a CWE ID referencing the specific weakness type:

- **CWE-79:** Cross-site Scripting (XSS)
- **CWE-89:** SQL Injection
- **CWE-307:** Improper Restriction of Excessive Authentication Attempts
- **CWE-362:** Concurrent Execution using Shared Resource with Improper Synchronization (Race Condition)
- **CWE-639:** Authorization Bypass Through User-Controlled Key (IDOR)
- **CWE-778:** Insufficient Logging
- **CWE-862:** Missing Authorization

Full CWE list: https://cwe.mitre.org/

---

## Threat Model Structure

Each threat model YAML file follows a consistent structure:

### File Structure

```yaml
# Header
metadata:
  domain: <domain_identifier>
  scope: "Description of domain coverage"
  classification: CRITICAL|HIGH|MEDIUM|LOW
  compliance_frameworks: [List of applicable frameworks]
  dependencies: [References to related documents]

domain:
  name: "Domain Name"
  description: "Detailed domain description"
  components: [List of domain components]

protected_assets:
  - asset: "Asset name"
    confidentiality: CRITICAL|HIGH|MEDIUM|LOW
    integrity: CRITICAL|HIGH|MEDIUM|LOW
    availability: CRITICAL|HIGH|MEDIUM|LOW
    data_classification: HIGHLY_CONFIDENTIAL|PII|FINANCIAL|etc.

trust_boundaries:
  - boundary: "Source → Destination"
    crossing_points: [API endpoints or interfaces]
    authentication_required: true|false
    authorization_required: true|false
    # Additional security requirements

threats:
  - id: DOMAIN-T001
    stride_category: Spoofing|Tampering|Repudiation|Information Disclosure|Denial of Service|Elevation of Privilege
    title: "Threat title"
    description: "Detailed threat description"
    vector: "Attack vector description"
    attack_scenario: "Step-by-step attack scenario"
    likelihood: CRITICAL|HIGH|MEDIUM|LOW
    impact: CRITICAL|HIGH|MEDIUM|LOW
    severity: CRITICAL|HIGH|MEDIUM|LOW
    cvss_score: 0.0-10.0
    cwe_id: CWE-XXXX
    affected_assets: [List of affected assets]
    affected_components: [List of affected components]
    primary_mitigation:
      status: EXISTING|PARTIAL|RECOMMENDED|PLANNED
      control: "Detailed mitigation description"
    secondary_mitigation:
      status: EXISTING|PARTIAL|RECOMMENDED|PLANNED
      control: "Additional mitigations"
    additional_controls: [List of supplementary controls]

prioritization:
  critical: [List of critical threat IDs]
  high: [List of high threat IDs]
  medium: [List of medium threat IDs]
  low: [List of low threat IDs]

compliance_mapping:
  OWASP_ASVS_4.0: [Mapped requirements]
  PCI_DSS_3.2.1: [Mapped requirements]
  GDPR: [Mapped articles]
  # Other frameworks

metrics:
  total_threats: X
  by_stride: {spoofing: X, tampering: X, ...}
  by_severity: {critical: X, high: X, ...}
  risk_score: X.X/10
  remediation_effort: CRITICAL|VERY_HIGH|HIGH|MEDIUM|LOW
  estimated_hours: XXX

mitigation_summary:
  existing: X
  partial: X
  recommended: X
  planned: X

review_schedule:
  initial_review: YYYY-MM-DD
  next_review: YYYY-MM-DD
  review_frequency: MONTHLY|QUARTERLY|BIANNUALLY
  review_triggers: [List of events triggering review]

notes: |
  Additional notes, priorities, and recommendations
```

### Asset CIA Rating

Assets are rated on Confidentiality, Integrity, and Availability (CIA triad):

- **CRITICAL:** Essential to business operations, catastrophic impact if compromised
- **HIGH:** Important to operations, significant impact if compromised
- **MEDIUM:** Useful to operations, moderate impact if compromised
- **LOW:** Minor importance, minimal impact if compromised

### Mitigation Status

- **EXISTING:** Control is currently implemented and active
- **PARTIAL:** Control is partially implemented or has gaps
- **RECOMMENDED:** Control is recommended but not yet implemented
- **PLANNED:** Control is scheduled for implementation with timeline

---

## Usage Guidelines

### For Security Engineers

1. **Initial Assessment:**
   - Review all threat models for assigned domains
   - Prioritize CRITICAL and HIGH severity threats
   - Identify threats with RECOMMENDED mitigation status

2. **Mitigation Planning:**
   - Create security tickets for each CRITICAL threat
   - Define implementation timeline based on severity
   - Coordinate with development teams on technical approach

3. **Validation:**
   - Verify mitigations through penetration testing
   - Update mitigation status from RECOMMENDED to EXISTING
   - Document residual risk if mitigation is partial

4. **Monitoring:**
   - Implement detection controls for identified threats
   - Set up alerts for suspicious patterns described in attack scenarios
   - Review security logs for evidence of threat exploitation

### For Developers

1. **Feature Development:**
   - Review relevant threat model before implementing new features
   - Incorporate security controls during development (shift-left)
   - Follow mitigation guidance for similar functionality

2. **Code Review:**
   - Check for vulnerabilities described in threat scenarios
   - Verify authorization checks per endpoint authorization matrix (#854)
   - Ensure input validation and output encoding per COMM-T002

3. **Testing:**
   - Write security test cases based on attack scenarios
   - Test for race conditions (ECON-T005), IDOR (CFP-T004), etc.
   - Include negative test cases (unauthorized access should fail)

4. **Documentation:**
   - Update threat model when adding new endpoints or functionality
   - Document security assumptions and dependencies
   - Escalate new threats to security team

### For Product Managers

1. **Feature Planning:**
   - Consider security implications during feature design
   - Allocate time/budget for security controls
   - Prioritize security features (MFA, audit logging) based on threat models

2. **Risk Acceptance:**
   - Review residual risk for unmitigated threats
   - Make informed decisions on risk acceptance vs. mitigation
   - Document risk acceptance decisions with business justification

3. **Compliance:**
   - Ensure features meet compliance requirements (PCI DSS, GDPR)
   - Plan for security audits based on compliance mappings
   - Budget for compliance certifications (SOC 2, ISO 27001)

### For Auditors

1. **Compliance Audits:**
   - Use compliance_mapping section to identify relevant threats
   - Verify mitigations are implemented per documented status
   - Review audit logs for evidence of security controls

2. **Risk Assessment:**
   - Review risk scores and prioritization
   - Validate likelihood and impact ratings
   - Assess residual risk for threats with PARTIAL or RECOMMENDED status

3. **Evidence Collection:**
   - Review audit trail for authentication/authorization events
   - Verify incident response for detected threats
   - Confirm regular threat model reviews per schedule

---

## Maintenance and Review

### Review Schedule

Threat models are living documents that must be kept current:

| Domain | Frequency | Next Review |
|--------|-----------|-------------|
| CFP | Quarterly | 2026-09-23 |
| Community | Bimonthly | 2026-08-23 |
| Authentication | Monthly | 2026-07-23 |
| Admin | Monthly | 2026-07-23 |
| Economy | Monthly | 2026-07-23 |

### Review Triggers (Immediate Review Required)

- **New Features:** Any new functionality in the domain
- **Security Incidents:** Vulnerability discovered or exploit attempted
- **Compliance Changes:** Regulatory requirements updated
- **Architecture Changes:** Major system redesigns
- **Technology Changes:** New frameworks, libraries, or services
- **Threat Intelligence:** New attack patterns or vulnerabilities published

### Review Process

1. **Preparation (1 week before):**
   - Gather recent security incidents and vulnerabilities
   - Review changes to domain since last review
   - Collect metrics: attack attempts, vulnerabilities found, mitigations completed

2. **Review Meeting (2 hours):**
   - Attendees: Security team, domain tech lead, product manager
   - Review each existing threat for relevance and accuracy
   - Identify new threats based on changes
   - Update likelihood/impact based on real-world data
   - Adjust mitigation priorities

3. **Documentation (1 week after):**
   - Update threat model YAML file
   - Increment version number
   - Update "Last Updated" date
   - Create tickets for new or updated mitigations
   - Publish updated threat model to repository

4. **Communication (ongoing):**
   - Share updates with development teams
   - Update security training materials
   - Brief executive team on critical changes
   - Notify compliance/audit teams of updates

### Version Control

- All threat models are version controlled in Git
- Changes tracked via pull requests
- Security team approval required for updates
- Archive old versions for historical reference

### Metrics and KPIs

Track threat model effectiveness:

- **Mitigation Progress:** % of threats with EXISTING status
- **Incident Correlation:** Incidents matching predicted threats
- **Coverage:** % of code/endpoints covered by threat models
- **Review Timeliness:** % of reviews completed on schedule
- **Vulnerability Detection:** Time to detect vs. time to exploit

---

## Compliance Mapping

Threat models are mapped to industry compliance frameworks:

### OWASP Application Security Verification Standard (ASVS) 4.0

**Coverage:**
- V2: Authentication
- V3: Session Management
- V4: Access Control
- V5: Validation, Sanitization, and Encoding
- V7: Error Handling and Logging
- V8: Data Protection
- V11: Business Logic

**Reference:** https://owasp.org/www-project-application-security-verification-standard/

### Payment Card Industry Data Security Standard (PCI DSS) 3.2.1

**Relevant Requirements:**
- 3.2: Do Not Store Sensitive Authentication Data (CVV)
- 3.4: Render PAN Unreadable
- 6.5: Address Common Coding Vulnerabilities
- 8.1-8.3: User Identification and Authentication
- 10.2-10.3: Audit Trails

**Scope:** Economy domain (payment processing)

**Reference:** https://www.pcisecuritystandards.org/

### General Data Protection Regulation (GDPR)

**Relevant Articles:**
- Article 5: Principles (lawfulness, fairness, transparency)
- Article 25: Data Protection by Design and Default
- Article 32: Security of Processing
- Article 33: Breach Notification

**Scope:** All domains (user data processing)

**Reference:** https://gdpr.eu/

### SOC 2 Type II

**Trust Service Criteria:**
- CC6.1: Logical Access Controls
- CC6.2: Prior to Issuing Credentials
- CC6.3: Removes Access When Appropriate
- CC7.2: System Monitoring
- CC7.3: Evaluates Security Events

**Scope:** Admin, Auth, Economy domains

**Reference:** https://www.aicpa.org/soc4so

### ISO 27001

**Controls:**
- A.9: Access Control
- A.12: Operations Security
- A.14: System Acquisition, Development, and Maintenance
- A.18: Compliance

**Scope:** All domains

### Other Frameworks

- **NIST SP 800-53:** Security controls catalog
- **CIS Controls:** Critical security controls
- **NIST Cybersecurity Framework:** Risk management framework
- **PSD2:** European payment services directive (Economy domain)

---

## Tool Integration

### Threat Modeling Tools

**Microsoft Threat Modeling Tool:**
- Import threat models from YAML
- Visualize trust boundaries and data flows
- Generate threat reports

**OWASP Threat Dragon:**
- Web-based threat modeling
- Export to JSON/YAML
- Integration with CI/CD pipelines

**IriusRisk:**
- Automated threat modeling
- Risk scoring and prioritization
- Compliance mapping

### Security Testing Tools

**Static Analysis (SAST):**
- SonarQube: Code quality and security scanning
- Checkmarx: Enterprise SAST platform
- Semgrep: Lightweight pattern-based scanning

**Dynamic Analysis (DAST):**
- OWASP ZAP: Web application security scanner
- Burp Suite: Web vulnerability scanner
- Acunetix: Automated vulnerability scanning

**Dependency Scanning:**
- Snyk: Dependency vulnerability scanning
- Dependabot: Automated dependency updates
- OWASP Dependency-Check: Open source scanning

### Monitoring and Detection

**SIEM Integration:**
- Splunk: Log aggregation and analysis
- ELK Stack: Elasticsearch, Logstash, Kibana
- Datadog: Cloud monitoring and security

**Threat Intelligence:**
- MITRE ATT&CK: Adversary tactics and techniques
- OWASP Top 10: Most critical web application risks
- CWE Top 25: Most dangerous software weaknesses

### CI/CD Integration

**Automated Security Testing:**
- Pre-commit hooks: Secret scanning, PAN detection
- PR checks: SAST, dependency scanning
- Deployment gates: DAST, compliance checks

**Example GitLab CI/CD:**

```yaml
security_scan:
  stage: test
  script:
    - sast-scan --threats-from threat-models/
    - dependency-check --critical-only
    - authorization-test --matrix endpoint-authorization-matrix.yaml
  only:
    - merge_requests
```

---

## References

### STRIDE Methodology

- [Microsoft STRIDE Threat Modeling](https://learn.microsoft.com/en-us/azure/security/develop/threat-modeling-tool-threats)
- [OWASP Threat Modeling](https://owasp.org/www-community/Threat_Modeling)
- [Threat Modeling Manifesto](https://www.threatmodelingmanifesto.org/)

### Security Frameworks

- [OWASP ASVS 4.0](https://owasp.org/www-project-application-security-verification-standard/)
- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [CWE Top 25](https://cwe.mitre.org/top25/)
- [MITRE ATT&CK](https://attack.mitre.org/)

### Compliance Standards

- [PCI DSS](https://www.pcisecuritystandards.org/)
- [GDPR](https://gdpr.eu/)
- [SOC 2](https://www.aicpa.org/soc4so)
- [ISO 27001](https://www.iso.org/isoiec-27001-information-security.html)

### Books and Resources

- *Threat Modeling: Designing for Security* by Adam Shostack
- *The Art of Software Security Assessment* by Mark Dowd et al.
- *OWASP Testing Guide* (https://owasp.org/www-project-web-security-testing-guide/)

### Internal Documentation

- **Issue #854:** Endpoint Authorization Matrix
- **Issue #855:** STRIDE Threat Models (this initiative)
- **Issue #856:** CSP Header Implementation

---

## Support and Contact

### Security Team

- **Email:** security@company.com
- **Slack:** #security-team
- **Incident Reporting:** security-incidents@company.com
- **Bug Bounty:** https://company.com/security/bug-bounty

### Threat Model Maintainers

- **Auth/Admin Domains:** Senior Security Engineer
- **CFP/Community Domains:** Application Security Engineer
- **Economy Domain:** Payment Security Specialist

### Questions and Feedback

For questions about threat models, security guidance, or to report potential threats:

1. Create issue in security repository
2. Tag @security-team
3. Include domain name and threat ID (if applicable)
4. For urgent security concerns, contact security team directly

---

**Document Status:** Published  
**Classification:** Internal  
**Distribution:** Engineering, Security, Product, Compliance teams

**Revision History:**

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-06-23 | Security Team | Initial release: 5 domain threat models created |

---

*This README is part of Issue #855: STRIDE Threat Model Implementation*
