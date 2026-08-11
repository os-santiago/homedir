# Ortelius Integration Research

**Parent Issue:** [#1340 - [PARENT] Investigar e integrar Ortelius para gestión post-deployment de vulnerabilidades y SBOM](https://github.com/os-santiago/homedir/issues/1340)

## Status

**DRAFT — Phase 1 (Research) complete. Phase 2 (Value Analysis) complete.**
This document covers child tasks `[RESEARCH]` (#1, #2) and `[ANALYSIS]` (#3) of
issue #1340. All facts verified against the official Ortelius repository
(`ortelius/ortelius` `main` branch) and public docs on 2026-08-11.

## Executive Summary

Ortelius is an open-source (Apache-2.0) post-deployment vulnerability
management platform maintained by the **Continuous Delivery Foundation**
(Linux Foundation). It ingests SBOMs at build time, correlates them with live
deployment records ("digital twin"), and continuously matches components
against the **OSV.dev** vulnerability database **every 15 minutes**. It answers
the four questions security teams need after a CVE disclosure:

1. **What** is the threat (CVE ID, CVSS, exploitability).
2. **Where** to fix it (repo, package, vulnerable version).
3. **Where** it runs in production right now.
4. **How** to fix it (upgraded version, remediation path).

For Homedir, Ortelius would close the current post-deployment visibility gap:
CVEs are only detected during build today, and a critical disclosure (e.g. in a
Quarkus/Java dependency) goes unnoticed until the next build — potentially days
later. Ortelius would surface it within 15 minutes.

**Preliminary recommendation: GO (conditional on POC).** The hosted SaaS path
(`app.deployhub.com`) is free for small teams, requires zero infrastructure, and
is fully additive to Homedir's existing security posture. A POC is required to
confirm SBOM ingestion quality (Maven PURLs) and dashboard visibility before
promoting to production observability mode.

## 1. Ortelius Architecture

### 1.1 System Overview

| Layer | Technology | Purpose |
|-------|-----------|---------|
| **API** | Go / Fiber v3 | REST (`/api/v1/*`) + GraphQL (`/api/v1/graphql`) |
| **Database** | **ArangoDB 3.11+** (graph + document) | Hub-and-spoke graph (PURL hubs), multi-tenant |
| **Auth** | golang-jwt/jwt v5 | JWT (24h), HttpOnly cookie; role/org fetched from DB per request |
| **CVE data** | OSV.dev API | Vulnerability database, refreshed every 15 min |
| **CVSS** | pandatix/go-cvss (3.1, 4.0) | Pre-computed base scores at ingest |
| **Version parsing** | Masterminds/semver + ecosystem parsers | Maven, npm, PyPI, Go, NuGet, etc. |
| **Async events** | Kafka (optional) | `release-events` topic, `release.sbom.created` |

> **Correction vs. initial assumptions:** the backend uses **ArangoDB**, not
> PostgreSQL. It is **agentless** — no agents/rescanning; a "digital twin" is
> built from release + sync records.

### 1.2 Core Data Model (Hub-and-Spoke)

- **PURL hub nodes** (version-free) sit at the center: CVEs connect to hubs
  (`cve2purl`), SBOMs connect to hubs (`sbom2purl` with exact installed version).
- Materialized `release2cve` edges are pre-computed **at SBOM ingest time** —
  runtime CVE queries return in <500ms without live graph traversal.
- This design avoids the N×M edge explosion of direct CVE→SBOM links
  (~99.89% edge reduction at scale).

### 1.3 CVE Lifecycle & Sync

- `POST /api/v1/sync` records what is **deployed to each endpoint** and creates
  `cve_lifecycle` records (`Detected → Active → Remediated`, plus `Superseded`
  on version upgrades).
- `root_introduced_at` tracks the first version where a CVE was present so
  **MTTR is not reset** by version churn.
- `disclosed_after_deployment` flag identifies **post-deployment CVEs** (the
  most operationally urgent) — disclosure date > deployment date.

### 1.4 Version Matching (Maven)

| Ecosystem | Parser |
|-----------|--------|
| Maven, Go, NuGet | Masterminds/semver (falls back to string comparison) |
| npm | aquasecurity/go-npm-version |
| PyPI | aquasecurity/go-pep440-version |

**Supported ecosystems for CVE matching:** npm, PyPI, **Maven**, Go, NuGet,
RubyGems, cargo, Composer, apk (Alpine/Wolfi), deb (Debian/Ubuntu).
→ **Maven is supported**, which covers Homedir's Quarkus/Java stack.

### 1.5 PURL Standardization

Ortelius normalizes PURL types so OSV ecosystem names match the PURL types
emitted by SBOM tools (e.g. Wolfi→`apk`). Homedir's CycloneDX SBOM from the
Maven CycloneDX plugin already emits standard `pkg:maven/...` PURLs, which
requires no transformation.

## 2. Deployment Models

### Option A: Hosted SaaS (`app.deployhub.com`)

| Criterion | Assessment |
|-----------|------------|
| Setup | **Free for small teams**; sign up, connect GitHub, onboard repos |
| SBOM delivery | `relscanner-job` (auto) OR `POST /api/v1/releases` (CI) |
| Cost | Free to start (DeployHub-hosted); enterprise tier = DeployHub Pro |
| Control | Limited (no data residency control) |
| Maintenance | None (vendor-managed) |
| Best fit | **POC and ongoing community use** |

### Option B: Self-hosted

| Criterion | Assessment |
|-----------|------------|
| Setup | Helm chart / `docker-compose` + ArangoDB 3.11+ |
| Requirements | Go backend + ArangoDB (graph DB); K8s manifest example: 3 replicas, `requests: 500m CPU/1Gi` per pod, `limits: 2 CPU/4Gi` |
| Control | Full (data residency, custom RBAC via GitOps `rbac.yaml`) |
| Maintenance | Upgrades, DB backups, OSV sync, TLS, rate limiting (must add reverse proxy) |
| Best fit | Enterprise/data-residency needs |

### Recommendation

- **POC + ongoing community use**: SaaS (`app.deployhub.com`) — zero infra,
  free tier, validates value immediately.
- **Self-hosted is not justified** for Homedir today: it adds a VM + ArangoDB +
  a background scanner to maintain for marginal benefit, since Homedir has no
  data-residency mandate. Revisit only if compliance requirements change.

## 3. Integration Options with Homedir CI/CD

Current pipeline: `.github/workflows/pr-ci-build-native-sbom.yml` builds with
Maven, generates a CycloneDX SBOM at `quarkus-app/target/bom.json`, and uploads
it as an artifact (retention 30 days). The SBOM is **never forwarded** to any
monitoring system.

### Option 1 (recommended): `relscanner-job` — zero CI changes

Ortelius provides a Kubernetes CronJob (`relscanner-job`) that:

1. Watches GitHub Actions workflow runs for repos connected via the GitHub App.
2. Acquires an SBOM via priority: **OCI attestation → Cosign DSSE → GitHub
   Actions artifact named `sbom`/`cyclonedx` → Syft on-the-fly**.
3. Syncs release records (name, version, git SHA, SBOM, OpenSSF Scorecard) to
   the backend via `POST /api/v1/releases`.

**Relevance to Homedir:** Homedir already uploads an artifact named
`sbom-${{ github.sha }}` containing the CycloneDX JSON. If the scanner is
deployed (or run on the SaaS side), it can discover and ingest Homedir's SBOM
with **no workflow modification**. This matches the issue's goal of
"integración con SBOM tools existentes sin cambios".

### Option 2: Push from GitHub Actions (explicit, deterministic)

**Implemented in this PR** (`.github/workflows/pr-ci-build-native-sbom.yml`,
`Upload SBOM to Ortelius (observation mode)` step) in observation mode: guarded
by `secrets.ORTELIUS_TOKEN != ''` and `continue-on-error: true`, so it never
blocks CI and is skipped on fork PRs.

```yaml
- name: Upload SBOM to Ortelius (observation mode)
  if: ${{ secrets.ORTELIUS_TOKEN != '' }}
  continue-on-error: true
  env:
    ORTELIUS_TOKEN: ${{ secrets.ORTELIUS_TOKEN }}
    GIT_SHA: ${{ github.sha }}
  run: |
    set -euo pipefail
    payload="$(jq -nc \
      --arg name "os-santiago/homedir" \
      --arg version "${GIT_SHA}" \
      --arg gitcommit "${GIT_SHA}" \
      --arg org "os-santiago" \
      --arg projecttype "docker" \
      --argjson sbom "$(jq -c . quarkus-app/target/bom.json)" \
      '{name:$name, version:$version, gitcommit:$gitcommit, org:$org, projecttype:$projecttype, sbom:{content:$sbom}}')"
    curl -fsS -X POST https://app.deployhub.com/api/v1/releases \
      -H "Content-Type: application/json" \
      -H "Cookie: auth_token=${ORTELIUS_TOKEN}" \
      -d "${payload}"
```

> **Important correction:** the issue's POC snippet used
> `POST https://app.deployhub.com/api/sbom` with `-F "sbom=@..."` (multipart).
> The actual endpoint is **`POST /api/v1/releases`** with a **JSON body**
> (`sbom.content` holds the CycloneDX document), authenticated via the
> `auth_token` JWT cookie from `POST /auth/login`. The exact request shape is
> defined in `ortelius/ortelius/docs/implementation.md`.

### Integration Point: Deployment notification (`POST /api/v1/sync`)

After production deployment, record the deployed version to the production
endpoint so Ortelius correlates CVEs with the runtime (enables
post-deployment metrics and the `disclosed_after_deployment` flag):

```yaml
- name: Notify Ortelius of deployment
  run: |
    curl -X POST https://app.deployhub.com/api/v1/sync \
      -H "Content-Type: application/json" \
      -b "auth_token=${{ secrets.ORTELIUS_TOKEN }}" \
      -d "{
        \"endpoint_name\": \"homedir-production\",
        \"endpoint\": { \"name\": \"homedir-production\", \"environment\": \"production\", \"org\": \"os-santiago\" },
        \"releases\": [ { \"release\": { \"name\": \"os-santiago/homedir\", \"version\": \"${{ steps.release_meta.outputs.new_version }}\" } } ]
      }"
```

### Integration Point: Dashboard / Queries

- **Ortelius UI** (`app.deployhub.com`): releases, endpoints, CVEs per
  severity, SLA compliance, MTTR, backlog trend, top risks.
- **GraphQL** (`POST /api/v1/graphql`): programmatic access — e.g.
  `affectedReleases(severity: CRITICAL)`, `endpointDetails`,
  `dashboardMTTR(days: 180)`.

## 4. Value Analysis (Phase 2)

### 4.1 Benefits vs Complexity Matrix

| Dimension | Benefit | Complexity / Cost |
|-----------|---------|-------------------|
| Detection | CVE in production within **15 min** of disclosure (vs next build) | None extra if SaaS |
| Prioritization | Runtime impact (what is running), not theoretical | Requires sync of deployment state |
| Compliance | EO 14028, NIST 800-53/800-218, Zero Trust, cATO alignment | None extra |
| Visibility | End-to-end: commit → build → deploy → runtime CVEs | Dashboard learning curve |
| SBOM reuse | CycloneDX already generated; no tooling change | SBOM must contain valid PURLs |
| Infrastructure | SaaS: none. Self-host: VM + ArangoDB + scanner | Significant if self-hosted |
| Maintenance | SaaS: none. Self-host: upgrades, DB, OSV sync | Significant if self-hosted |
| CI/CD changes | Option 1: none. Option 2: 1 step | Minimal |

### 4.2 Current Gaps That Ortelius Resolves

| Gap (current Homedir state) | Ortelius |
|-----------------------------|----------|
| ❌ No post-deployment CVE visibility | Continuous 15-min OSV.dev matching against deployed components |
| ❌ No correlation with runtime | Digital twin: release ↔ endpoint mapping |
| ❌ Reactive detection (only at build) | Notification within 15 min of disclosure |
| ❌ No SLA/MTTR tracking | Built-in NIST-based SLA policy + MTTR metrics |
| ✅ SBOM generated (CycloneDX) | Native CycloneDX/SPDX ingestion |
| ✅ Security advisories workflow | Complements (does not replace) `security-advisory.yml` |

### 4.3 Compliance Impact

- **EO 14028** — software supply chain security: SBOM + continuous vuln mgmt.
- **NIST 800-53 / 800-218 (SSDF)** — provenance, vulnerability monitoring.
- **NIST 800-171** — CUI protection posture.
- **Zero Trust** — continuous validation of deployed inventory.
- For a tech/DevSecOps community, this demonstrates a proactive security
  posture — a differentiator vs "build-time scan only".

### 4.4 Trade-off Key

**¿El valor de visibilidad post-deployment justifica la complejidad operacional?**

With the SaaS path, the operational complexity is effectively **zero** for
Homedir (no infra, no maintenance). The remaining cost is (a) a GitHub
App/credential setup and (b) SBOM quality validation. **Verdict: YES** — the
value justifies a POC, and the SaaS option makes the ongoing operational cost
negligible.

## 5. Cost Evaluation

| Item | SaaS (app.deployhub.com) | Self-hosted |
|------|--------------------------|-------------|
| License | Free tier for small teams (DeployHub-hosted); DeployHub Pro = enterprise paid tier | Apache-2.0 (free) |
| Infrastructure | $0 (hosted) | ~1 VM/pod + ArangoDB (shared with existing VPS or +$5-20/mo) |
| Setup time | ~1 hour (sign up + GitHub connect + onboard) | 1-2 days (Helm/Compose + ArangoDB + RBAC repo + TLS) |
| Maintenance | $0 | Ongoing: upgrades, DB backups, OSV sync, monitoring |
| Support | Community | Community |

**Estimated total cost of ownership:** SaaS ≈ **$0** for Homedir's community
scale. Self-hosted ≈ small infra cost + ongoing operator time (not recommended
at this stage).

## 6. Preliminary GO/NO-GO

**GO (conditional on POC).** Rationale:

1. **Additive & safe** — does not replace existing security tooling; SBOM
   artifact capability is preserved; rollback is a workflow revert.
2. **Low operational cost** — free SaaS tier removes infra/maintenance burden.
3. **High value** — closes the post-deployment visibility gap (15-min CVE
   detection) with a digital-twin correlation Homedir currently lacks.
4. **Open-source alignment** — CDF/Linux Foundation project matches Homedir's
   community/open-source philosophy.

**POC gates (Phase 3) before enabling alerting in production:**
- [ ] Sign up on `app.deployhub.com`, connect GitHub, onboard `os-santiago/homedir`.
- [ ] Validate SBOM ingestion: Homedir release appears in dashboard <5 min.
- [ ] Confirm ≥1 existing CVE is detected in current dependencies.
- [ ] Verify Maven PURLs from the CycloneDX SBOM match OSV Maven data.
- [ ] Decide Option 1 (relscanner-job) vs Option 2 (explicit CI push).
- [ ] Deploy in **observation mode** (no alerts) for 1 week; then enable alerts.

## 7. Next Steps

| Phase | Child issue | Action |
|-------|-------------|--------|
| 3 | `[POC]` | Execute POC gates above (needs human to sign up for SaaS + add `ORTELIUS_TOKEN` secret). CI step is already implemented in observation mode in this PR |
| 4 | `[DESIGN]` | Produce final integration architecture based on POC outcome |
| 5 | `[IMPL]` | Enable alerts after 1-week observation; optionally add `POST /api/v1/sync` on deploy |
| 6 | `[DOCS]` | Update `ortelius-operations.md` based on live data + README badge |

## References

- [Ortelius GitHub](https://github.com/ortelius/ortelius)
- [Ortelius — Architecture Guide](https://github.com/ortelius/ortelius/blob/main/docs/architecture.md)
- [Ortelius — Implementation Guide (API)](https://github.com/ortelius/ortelius/blob/main/docs/implementation.md)
- [relscanner-job (SBOM scanner)](https://github.com/ortelius/relscanner-job)
- [Ortelius — Supply Chain Security](https://ortelius.io/catalog/)
- [Ortelius — Vulnerability Tracking](https://ortelius.io/blog/2023/03/31/leveraging-ortelius-for-vulnerability-tracking/)
- [DeployHub — Ortelius free post-deployment VM](https://www.deployhub.com/open-source-vulnerability-management/)
- [Homedir SBOM workflow](https://github.com/os-santiago/homedir/blob/main/.github/workflows/pr-ci-build-native-sbom.yml)

---

*Status: DRAFT — Phase 1 (Research) + Phase 2 (Value Analysis) complete.
Phase 3 (POC) requires a human to provision the SaaS account and secrets.*
