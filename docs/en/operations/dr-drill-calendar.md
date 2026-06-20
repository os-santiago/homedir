# DR Drill Calendar

## Cadence

| Frequency | Drill Type | Scope | Domains |
|-----------|-----------|-------|---------|
| **Weekly** | Automated health + backup integrity | Backup exists, decrypts, restorable | Community, Economy |
| **Monthly** | Full restore drill | Restore from encrypted backup, verify content | Community |
| **Quarterly** | Full DR scenario | Restore all domains, measure RTO, failover test | All domains |
| **Per-release** | CFP-specific drill | Part of cfp-go-live-resilience.yml workflow | Events/CFP |

## Weekly Drill (automated, < 5 min)

1. Verify latest backup artifact exists in backup directory
2. Decrypt with age key and verify checksum
3. Restore to `/tmp/homedir-dr-drill-weekly/`
4. Assert minimum file count (at least community data present)
5. Clean up temporary files

## Monthly Drill (semi-automated, < 30 min)

1. Run full encryption-decryption-restore cycle
2. Verify content integrity of restored community data
3. Compare key record counts against production
4. Log results to `docs/en/operations/dr-drill-results/`

## Quarterly Drill (manual orchestration, < 2h)

1. Declare simulated disaster (e.g., "VPS data volume lost")
2. Measure RTO from declaration to service restored
3. Validate RPO by checking last backup timestamp vs data freshness
4. Run cfp-go-live-resilience.yml Gate 3 (DR readiness) as validation
5. Document incident report with timeline and improvement items

## Evidence Requirements

- **Weekly**: Log output saved to `dr-drill-weekly-YYYY-MM-DD.log`
- **Monthly**: Check-in comment on tracking issue with file count, record count, RPO met (Y/N)
- **Quarterly**: Full incident report in issue format with timeline, RTO, RPO, improvements

Modelo: DeepSeek V4 Flash Free