# RPO/RTO Goals by Data Domain

## Definitions

- **RPO** (Recovery Point Objective): Maximum age of data that may be lost. Lower = more frequent backups.
- **RTO** (Recovery Time Objective): Maximum time to restore service after a declared incident.

## Domain Matrix

| Domain | Data | Current Protection | RPO Target | RTO Target | Verification |
|--------|------|--------------------|------------|------------|--------------|
| **Community** | Member profiles, board posts, votes | Daily encrypted backup (homedir-dr-backup.sh) | 24h | 2h | DR drill + restore test |
| **Economy** | Transaction history, balances | Daily backup (included in data dump) | 24h | 4h | Verify tx log replay |
| **Trending** | Computed scores, activity aggregates | Recomputable from source data | 0 (recompute) | 1h | Re-trigger trend pipeline |
| **Events/CFP** | Proposals, reviews, schedules | Daily backup | 24h | 2h | CFP-specific restore test |
| **Configuration** | OIDC, env vars, nginx | Versioned in repo + VPS clone | 0 (git) | 30min | Redeploy from repo |
| **Discord Bot** | Economy state, cached guild data | LRU cache + Redis persistence | 1h | 30min | Bot restart + cache warm |

## Notes

- RPO=0 domains are rebuildable from source of truth (git, external API) without backup restore.
- RTO assumes automated deploy pipeline is operational and VPS is healthy.
- DR drill results should be logged and tracked per domain.

Modelo: DeepSeek V4 Flash Free