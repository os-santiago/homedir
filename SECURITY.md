# Security Policy

EventFlow takes the security of our users and contributors seriously. If you believe you’ve found a vulnerability, please follow the process below to help us fix it quickly and responsibly.

---

## ✅ Supported Versions (Major-only)

We support **only the currently active major release line**. When a new major is released, all previous majors become **unsupported** (EOL) immediately.

**Current active major (as of 2025-08-09): `2.x`**

| Version | Supported                 |
|--------:|---------------------------|
| 2.x     | ✅ Active (features + security) |
| < 2.x   | ❌ Unsupported             |

> This major-only policy keeps the project fast and secure in line with our trunk-based development approach.

---

## 🔐 Reporting a Vulnerability

**Please do not open a public Issue or Discussion for security problems.**

Use one of these private channels:

1. **GitHub – Private vulnerability report (preferred)**  
   Go to the repository **Security** tab → **Report a vulnerability** and follow the form.

2. **Email**  
   Send details to **sergio.canales.e@gmail.com** with the subject:  
   `EVENTFLOW SECURITY: <short title>`

Please include (as applicable):
- Affected versions (e.g., `2.1.2`)
- Environment (local/Docker/Kubernetes/OpenShift)
- Impact summary (what can an attacker do?)
- Reproduction steps or proof-of-concept
- Relevant logs/config (redact secrets)
- Any known workarounds or mitigations

---

## ⏱️ Our Response & Timelines

We aim for the following SLAs:

- **Acknowledgement:** within **48 hours**
- **Triage & initial assessment:** within **5 business days**
- **Status updates:** at least **weekly** until resolution

**Guideline targets** (aligned to our P0–P3 severity scale):

| Severity | Target to Fix | Disclosure Window* |
|---------|----------------|--------------------|
| P0 (Critical) | ASAP, typically ≤ **7 days** | Coordinated, typically ≤ **30 days** |
| P1 (High)     | Typically ≤ **14–21 days**   | Coordinated, typically ≤ **60 days** |
| P2 (Medium)   | Next scheduled release       | Coordinated, typically ≤ **90 days** |
| P3 (Low)      | As time permits              | Batched notes / next release         |

\* We practice **coordinated disclosure** with the reporter.

---

## 🔏 Disclosure & CVE

- We will publish a **GitHub Security Advisory** with details, credits (if desired), and fixed versions.
- When appropriate, we will request a **CVE ID** and reference it in the advisory and release notes.

---

## 🛡️ Safe Harbor for Good-Faith Research

We do not pursue or support legal action for **good-faith security research** that:
- Avoids privacy violations, service degradation, or data destruction/exfiltration
- Respects rate limits and only uses your own accounts/data
- Stops testing and reports immediately upon finding a vulnerability
- Keeps details confidential until a fix is available and a coordinated disclosure is agreed

Out-of-scope activities include social engineering, physical attacks, spam, and DDoS.

---

_Last updated: 2025-08-09_
