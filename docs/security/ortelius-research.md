# Ortelius Integration Research

**Parent Issue:** [#1340 - [PARENT] Investigar e integrar Ortelius para gestión post-deployment de vulnerabilidades y SBOM](https://github.com/os-santiago/homedir/issues/1340)

## Status

**DRAFT — Phase 1 (Research) in progress.** This document covers child task
`[RESEARCH] Investigar Ortelius: arquitectura, deployment models, requisitos`.

## Executive Summary

Ortelius is an open-source (Apache-2.0) post-deployment vulnerability
management platform maintained by the Continuous Delivery Foundation
(Linux Foundation). It ingests SBOMs at build time, correlates them with live
deployment records ("digital twin"), and continuously matches components
against the OSV.dev vulnerability database every 15 minutes. It answers the
four questions security teams need after a CVE disclosure:

1. **What** is the threat (CVE ID, CVSS, exploitability).
2. **Where** to fix it (repo, package, vulnerable version).
3. **Where** it runs in production right now.
4. **How** to fix it (upgraded version, remediation path).

For Homedir, Ortelius would close the current post-deployment visibility gap:
CVEs are only detected during build today, and a critical disclosure (e.g.
Quarkus/Java dependency) goes unnoticed until the next build.

## Ortelius Architecture

- **Backend**: Go service exposing a REST API (`/api/v1/...`) plus GraphQL.
- **Database**: PostgreSQL-based catalog of releases, endpoints, components.
- **Scanner (optional)**: `relscanner-job`, a CronJob that discovers releases
  from GitHub/GitLab and auto-attaches an SBOM via OCI attestations, Cosign,
  GitHub Release assets, or Syft/cdxgen generation.
- **Vulnerability data**: OSV.dev database, refreshed every 15 minutes.
  Components with missing/malformed PURLs are silently skipped during CVE
  matching.
- **GitHub integration**: Ortelius GitHub App imports release + deployment
  metadata (`POST /api/v1/github/onboard`). Importing metadata alone does **not**
  attach an SBOM — the `relscanner-job` scanner (or a direct `POST
  /api/v1/releases` with an SBOM) is required for CVE matching.
- **Supported ecosystems for CVE matching**: npm, PyPI, Maven, Go, NuGet,
  RubyGems, cargo, Composer, apk (Alpine/Wolfi), deb (Debian/Ubuntu).
  → Maven is supported, which covers Homedir's Quarkus/Java stack.

## Deployment Models

### Option A: Hosted SaaS (`app.deployhub.com`)

| Criterion | Assessment |
|-----------|------------|
| Setup | Zero infrastructure; sign up, connect GitHub, onboard repos |
| SBOM delivery | Deploy `relscanner-job` OR push SBOM via `POST /api/v1/releases` |
| Cost | Free to start; see hosted terms for limits |
| Control | Limited (no data residency control) |
| Maintenance | None (vendor-managed) |
| Best fit | **POC**, quick time-to-value |

### Option B: Self-hosted

| Criterion | Assessment |
|-----------|------------|
| Setup | Helm chart / `docker-compose.yml` + PostgreSQL |
| Requirements | VM/pod extra (Go backend + DB), storage for catalog |
| Control | Full (data residency, custom policies) |
| Maintenance | Upgrades, DB backups, OSV sync, monitoring |
| Best fit | Production with compliance/data-control needs |

### Recommendation for Homedir

- **Phase 1 (POC)**: SaaS trial on `app.deployhub.com` — zero infra, validates
  value quickly.
- **Phase 2 (GO decision)**: revisit self-hosted if data residency becomes a
  requirement. Homedir currently runs on a VPS via Podman
  (`homedir.opensourcesantiago.io`), so a lightweight self-hosted instance is
  feasible if desired.

## Integration with Current Homedir CI/CD

Current pipeline: `.github/workflows/pr-ci-build-native-sbom.yml` builds with
Maven, generates a CycloneDX SBOM at `quarkus-app/target/bom.json`, and uploads
it as an artifact (retention 30 days). The SBOM is never forwarded to any
monitoring system.

### Integration Point 1: Push SBOM after build (PR CI)

```yaml
- name: Upload SBOM to Ortelius
  run: |
    curl -X POST https://app.deployhub.com/api/v1/releases \
      -H "Authorization: Bearer ${{ secrets.ORTELIUS_TOKEN }}" \
      -H "Content-Type: application/json" \
      -d "{\"sbom\":$(jq -c . < quarkus-app/target/bom.json)}"
```

> Exact payload shape to be confirmed against
> `docs/implementation.md` in the Ortelius repo during the POC.

### Integration Point 2: Deployment notification

On production deploy, record the deployed version + SBOM so Ortelius correlates
CVEs against the runtime endpoint (digital twin). This is the key enabler for
"post-deploy" metrics (disclosure date vs. deployment date).

### Integration Point 3: Dashboard

View CVEs per endpoint/severity, SLA compliance, MTTR, backlog trends from the
Ortelius UI without building custom tooling.

## Current Gaps That Ortelius Resolves

| Gap (current Homedir state) | Ortelius |
|-----------------------------|----------|
| ❌ No post-deployment CVE visibility | Continuous 15-min OSV.dev matching against deployed components |
| ❌ No correlation with runtime | Digital twin: release ↔ endpoint mapping |
| ❌ Reactive detection (only at build) | Notification within 15 min of disclosure |
| ❌ No SLA/MTTR tracking | Built-in NIST-based SLA policy + remediation metrics |
| ✅ SBOM generated (CycloneDX) | Native CycloneDX/SPDX ingestion |

## Trade-offs: Benefits vs. Complexity

**Benefits**
- CVE detection in production minutes after disclosure.
- Prioritization based on real runtime impact (what is running, not theoretical).
- Compliance alignment (EO 14028, NIST 800-53/800-218, Zero Trust).
- End-to-end visibility: commit → build → deploy → runtime CVEs.
- Open-source alignment (CDF/Linux Foundation) matches Homedir's philosophy.

**Complexity / Costs**
- Infrastructure (if self-hosted): extra VM/pod + PostgreSQL.
- Maintenance: Ortelius upgrades, OSV sync, DB backups.
- Learning curve: team must learn dashboard/APIs.
- CI/CD changes: modify workflows to send SBOM + deployment records.
- SBOM quality dependency: CVE matching requires valid PURLs.

**Key trade-off**: does the post-deployment visibility value justify the
operational complexity for a community platform? Preliminary assessment: **YES
for a POC**, given the SaaS option removes most operational burden.

## Preliminary GO/NO-GO

**Conditional GO (pending POC).** The SaaS path makes this low-risk and
additive: current SBOM capability is preserved, Ortelius does not replace
existing security tooling, and rollback is trivial (workflow revert). The
decision will be confirmed after the POC validates SBOM flow, dashboard
visibility, and at least one real CVE detection.

## Next Steps (Phase 1-2)

- [ ] Validate `POST /api/v1/releases` SBOM payload shape against Ortelius docs.
- [ ] Sign up for SaaS trial, connect GitHub, onboard `os-santiago/homedir`.
- [ ] Confirm Maven/PURL extraction quality from the existing CycloneDX SBOM.
- [ ] POC: push SBOM from a feature branch workflow, verify dashboard within 5 min.
- [ ] Assess deployment notification flow for `homedir.opensourcesantiago.io`.

## References

- [Ortelius GitHub](https://github.com/ortelius/ortelius)
- [Ortelius — Supply Chain Security](https://ortelius.io/catalog/)
- [Ortelius — Vulnerability Tracking](https://ortelius.io/blog/2023/03/31/leveraging-ortelius-for-vulnerability-tracking/)
- [Ortelius — OSV.dev Integration](https://ortelius.io/blog/2026/04/06/securechaincon-2026)
- [relscanner-job (SBOM scanner)](https://github.com/ortelius/relscanner-job)
- [Homedir SBOM workflow](https://github.com/os-santiago/homedir/blob/main/.github/workflows/pr-ci-build-native-sbom.yml)

---

*Status: DRAFT (research phase). Not for merge review yet — validation pending.*
