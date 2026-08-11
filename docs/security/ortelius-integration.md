# Ortelius Integration — Architecture & Setup

**Parent Issue:** [#1340 - [PARENT] Investigar e integrar Ortelius para gestión post-deployment de vulnerabilidades y SBOM](https://github.com/os-santiago/homedir/issues/1340)

## Status

**DRAFT — Phase 3/4 (POC step implemented + Design).** Provenance:
`docs/security/ortelius-research.md` (Phase 1-2). The SBOM push step is
implemented in `.github/workflows/pr-ci-build-native-sbom.yml` in observation
mode. The remaining validation is **human-gated**: provision SaaS account +
`ORTELIUS_TOKEN`, then execute the POC gates.

## 1. Target Architecture

```
┌─────────────────────────────┐      ┌──────────────────────────────────────┐
│ GitHub Actions              │      │ Ortelius (SaaS app.deployhub.com)    │
│  pr-ci-build-native-sbom    │──SBOM──▶  POST /api/v1/releases             │
│  quarkus-app/target/bom.json│      │    │ (or relscanner-job discovery)   │
└─────────────────────────────┘      └────────┬─────────────────────────────┘
                                              │ scan OSV.dev every 15 min
                                              ▼
                                ┌──────────────────────────────────────┐
                                │ Dashboard de CVEs + endpoints        │
                                │ - releases, endpoints, severity      │
                                │ - SLA compliance / MTTR / backlog    │
                                │ - post-deployment CVEs flag          │
                                └──────────────────────────────────────┘
                                              │
                              POST /api/v1/sync (después de deploy)
                                              ▼
                     Endpoint "homedir-production" = digital twin runtime
```

## 2. Deployment Decision

**Chosen: Option A (SaaS, `app.deployhub.com`).**

Rationale (from research):
- Free tier for small teams, zero infrastructure for Homedir.
- No ArangoDB/cluster to maintain; no self-host upgrade burden.
- Community-scale: self-hosted adds complexity without data-residency need.

Fallback (Option B, self-hosted) only if future compliance demands data
residency: Helm chart + ArangoDB 3.11+, `rbac.yaml` GitOps, TLS + reverse proxy
(rate limiting not built-in yet).

## 3. Pre-requisites

1. Human creates an org on `app.deployhub.com` (owner role).
2. Human connects the **Ortelius GitHub App** for `os-santiago` org
   (`Contents: Read`, `Actions: Read`).
3. Human creates a near-least-privilege credential for CI:
   - Option 1 (`relscanner-job`): no CI secret needed — scanner ingests SBOM +
     scorecard automatically; the GitHub connection is enough.
   - Option 2 (explicit push): create an **Editor** role account and store its
     `auth_token` as GitHub secret `ORTELIUS_TOKEN`.
4. Decide observation-first: **no alerts** in week 1.

## 4. Workflow Changes

### 4.1 Option 1 — relscanner-job (recommended, zero CI change)

No modification to `.github/workflows/pr-ci-build-native-sbom.yml` is required.
The scanner attaches an SBOM from the existing `sbom-${{ github.sha }}` GitHub
Actions artifact (or OCI attestation / Syft) and posts it to
`POST /api/v1/releases`. Homedir's current artifact naming (`sbom-*`) is already
compatible with the scanner's artifact discovery.

### 4.2 Option 2 — explicit push in SBOM workflow

**Implemented in this PR** (`.github/workflows/pr-ci-build-native-sbom.yml`), in
**observation mode** so it cannot block CI: a `Upload SBOM to Ortelius
(observation mode)` step runs only when `secrets.ORTELIUS_TOKEN != ''` and uses
`continue-on-error: true`. The step:

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

Because PRs from forks never have repo secrets, the step is skipped on
contributor PRs and runs only once `ORTELIUS_TOKEN` is configured on the
upstream repo — preserving the existing fork-friendly CI behavior.

### 4.3 Deployment notification (production sync)

Add a step in the release/deploy workflow (`release.yml`) to notify Ortelius of
the production version (enables post-deployment correlation):

```yaml
- name: Notify Ortelius of production deployment
  if: ${{ secrets.ORTELIUS_TOKEN != '' }}
  env:
    ORTELIUS_TOKEN: ${{ secrets.ORTELIUS_TOKEN }}
    VERSION: ${{ steps.release_meta.outputs.new_version }}
  run: |
    curl -fsS -X POST https://app.deployhub.com/api/v1/sync \
      -H "Content-Type: application/json" \
      -H "Cookie: auth_token=${ORTELIUS_TOKEN}" \
      -d "{\"endpoint_name\":\"homedir-production\",\
        \"endpoint\":{\"name\":\"homedir-production\",\"environment\":\"production\",\"org\":\"os-santiago\"},\
        \"releases\":[{\"release\":{\"name\":\"os-santiago/homedir\",\"version\":\"$VERSION\"}}]}"
```

## 5. Secret Management

| Secret | Where | Purpose |
|--------|-------|---------|
| `ORTELIUS_TOKEN` | GitHub repo/org secret | Editor-role `auth_token` (Option 2 only) |
| GitHub App install | Ortelius UI | Scanner discovery (Option 1) |

- Never commit `ORTELIUS_TOKEN`.
- Use repo/org-level visibility and protect with environment rules if possible.
- Rotate on role change or suspected leak.

## 6. Verification Checklist

- [ ] POC: SBOM appears in Ortelius dashboard <5 min post-build.
- [ ] POC: ≥1 existing CVE detected in current dependencies.
- [ ] POC: Maven PURLs match OSV Maven data (no silent PURL mismatch).
- [ ] Dashboard shows releases + endpoints for `os-santiago/homedir`.
- [ ] `POST /api/v1/sync` records deployment to `homedir-production`.
- [ ] Post-deployment CVEs appear with `disclosed_after_deployment=true`.
- [ ] 1-week observation without false-positive threshold alarms.

## 7. Rollback Plan

- Revert workflow changes (Option 2) or disconnect GitHub App (Option 1).
- SBOM artifacts remain on GitHub — no capability is lost.
- Ortelius is additive; existing `security-advisory.yml` is untouched.

## References

- [Ortelius Implementation Guide](https://github.com/ortelius/ortelius/blob/main/docs/implementation.md)
- [Ortelius Architecture Guide](https://github.com/ortelius/ortelius/blob/main/docs/architecture.md)
- [relscanner-job](https://github.com/ortelius/relscanner-job)
- [Homedir SBOM workflow](https://github.com/os-santiago/homedir/blob/main/.github/workflows/pr-ci-build-native-sbom.yml)