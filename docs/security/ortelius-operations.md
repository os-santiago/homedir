# Ortelius Operations Runbook

**Parent Issue:** [#1340 - [PARENT] Investigar e integrar Ortelius para gestión post-deployment de vulnerabilidades y SBOM](https://github.com/os-santiago/homedir/issues/1340)

## Status

**DRAFT — Phase 6 (Operations).** Becomes actionable only after the integration
(Phases 3-5) is deployed and a GO decision is recorded. Target: SaaS
(`app.deployhub.com`).

## 1. Component Inventory

| Component | Location | Maintainer |
|-----------|----------|------------|
| Ortelius SaaS dashboard | `app.deployhub.com` | DeployHub (hosted) |
| Ortelius GitHub App | Installed on `os-santiago` org | Homedir admin |
| (Option 2) `ORTELIUS_TOKEN` secret | GitHub repo secrets | Homedir admin |
| Homedir SBOM workflow | `.github/workflows/pr-ci-build-native-sbom.yml` | Homedir |

## 2. Health Checks

| Check | How | Frequency |
|-------|-----|-----------|
| Dashboard reachable | Open `app.deployhub.com`, org `os-santiago` | Weekly |
| Releases appear | Releases list shows recent `os-santiago/homedir` version | Weekly |
| Sync timestamps fresh | `last_sync` on endpoint `homedir-production` | Weekly |
| OSV data freshness | CVE list updates (OSV refresh is every 15 min) | Spot-check |
| CI upload step passing | GitHub Actions run status | On PR/release |
| `ORTELIUS_TOKEN` still valid | A manual `POST /api/v1/releases` returns 201 | Monthly |

## 3. Routine Operations

### 3.1 Weekly review

1. Open Ortelius dashboard.
2. Check **Post-Deploy CVEs** count and the severity breakdown.
3. Check **% Open > SLA**; identify overdue Critical/High.
4. Record MTTR / backlog trend (delta). Note: dashboard uses 180-day rolling window.

### 3.2 When a new CVE is disclosed

1. Confirm whether it maps to a deployed endpoint (`reveals_cve` info in
   dashboard).
2. Determine if it is `disclosed_after_deployment` (post-deployment).
3. Triage: if Critical/High and reaching SLA limit, plan dependency upgrade and
   new release (normal Homedir release flow).
4. Upgrade fixed dependency → re-deploy → `POST /api/v1/sync` with new version
   → verify CVE marked remediated on the endpoint.

### 3.3 Monthly audit

1. Export/check organization status via GraphQL dashboard queries.
2. Verify no unauthorized org members/roles (*viewer* minimal principle).
3. Rotate `ORTELIUS_TOKEN` if an Editor-role account was used.
4. Confirm GitHub App permissions unchanged (`Contents: Read`, `Actions: Read`).

## 4. Troubleshooting

| Symptom | Likely cause | Resolution |
|---------|--------------|------------|
| "Zero CVEs" despite releases | SBOM not attached (relscanner not running / artifact not found) | Connect GitHub App, run scanner, wait 1 scan cycle (≤15 min); or push explicitly from CI |
| Release "not found" in dashboard | Wrong `org`/`name` (case/org slash) | Use `os-santiago/homedir` and explicit `org:"os-santiago"` |
| Silent PURL mismatch (no CVEs on known vuln) | SBOM PURLs lack version/Maven format | Regenerate with CycloneDX Maven plugin; confirm `pkg:maven/...` purls |
| `POST /api/v1/releases` 401 | Invalid/expired `auth_token` | Re-login Editor account, update secret |
| Re-upload returns no-op | Same `(name, version, contentsha)` already processed | Use a new git SHA/version; dedup is by design |
| Sync does not create lifecycle records | Releases absent from sync payload | Include the exact releases deployed in `POST /api/v1/sync` |
| Dashboard numbers seem stale | Rolling 180-day window | Interpret as windowed metric, not total |

## 5. On-Call Procedures

### 5.1 Dashboard unreachable

- SaaS: out-of-scope for Homedir (vendor-managed). Monitor via
  `GET /api/v1/` health endpoint; report to DeployHub/community channel.
- Degraded reporting only — Homedir app itself is unaffected.

### 5.2 CI upload step failing persistently

1. Check secret presence: `secret.ORTELIUS_TOKEN != ''` is the step guard.
2. Validate token with a manual curl (`POST /auth/login` → `POST /api/v1/releases`).
3. If the SaaS org was deleted/renamed, re-create org and re-connect GitHub App.
4. Keep SBOM artifact capability regardless — Ortelius failure must not block
   the build (step should be `continue-on-error: true` in observation mode).

## 6. Escalation

| Issue | Escalate to |
|-------|-------------|
| Critical CVE in production without known fix | Homedir maintainers + community security channel |
| Ortelius SaaS outage / data loss | DeployHub support / @ortelius community |
| Security incident (token leak) | Rotate secret immediately; report per SECURITY.md |

## 7. Useful Queries

### GraphQL — top risks

```graphql
query {
  dashboardTopRisks(limit: 10, type: "releases", org: "os-santiago") {
    name version critical_count high_count total_vulns
  }
}
```

### GraphQL — endpoint detail

```graphql
query {
  endpointDetails(name: "homedir-production") {
    last_sync
    total_vulnerabilities { critical high medium low }
    releases { release_name release_version vulnerability_count }
  }
}
```

## Decisión de alertas (post-observation)

1. Observation week 1: collect baseline (counts, false-positive rate).
2. Enable threshold alerts only after validating signal quality.
3. Alerts target: Critical/High CVEs on `homedir-production` beyond SLA.

---

*Status: DRAFT — Phase 6 (Operations). To be updated after POC/GO decision.*