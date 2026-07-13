# Plan de Validación E2E - Post Merge Batch

## Fecha
2026-07-12

## PRs Validados
- **PR #1153**: fix(sdlc): reconcile orphan PRs for auto-merge (#1142)
- **PR #1227**: fix(sdlc): prevent subshell bug in admission reconciliation loop

## Deployment Status
- ✅ Deployment #1: PR #1153 deployed at 16:35:20 UTC
- ⏳ Deployment #2: PR #1227 pending merge

---

## Test 1: Orphan PRs Auto-merge (Fix #1153)

### Objetivo
Validar que PRs sin state file pero con `scc-approved` + checks passing se auto-mergean

### Escenario de Prueba

**Setup**:
1. Crear branch manualmente: `test/orphan-pr-validation`
2. Hacer cambio trivial (ej: README typo fix)
3. Commit y push
4. Crear PR manualmente via `gh pr create`
5. Esperar CI checks passing
6. Agregar label `scc-approved` manualmente
7. **NO crear state file** (simular orphan PR)

**Resultado Esperado**:
- ✅ Worker detecta PR en próximo cycle (~3 min)
- ✅ Función `reconcile_orphan_open_prs()` ejecuta
- ✅ Auto-merge enabled
- ✅ PR merged automáticamente

**Criterios de Éxito**:
- [ ] Worker logs muestran: "reconcile_orphan_open_prs: processing PR #XXXX"
- [ ] Auto-merge enabled: "enabled normal auto-merge for orphan PR"
- [ ] PR merged sin intervención manual
- [ ] Tiempo total: <5 minutos desde label `scc-approved`

---

## Test 2: Admission Stuck Issues (Fix #1227)

### Objetivo
Validar que múltiples issues stuck en admission se reconcilian todos (no solo algunos)

### Escenario de Prueba

**Setup**:
1. Crear 3 issues simultáneamente (dentro de 5 segundos)
2. Todos con label `ready-to-implement`
3. Todos con contenido simple que pasa acceptance criteria

**Issues de prueba**:
```bash
# Issue A
Title: [test-admission-a] Fix typo in CONTRIBUTING
Body: Simple typo fix in line 42

# Issue B  
Title: [test-admission-b] Add badge to README
Body: Add build status badge

# Issue C
Title: [test-admission-c] Update docs link
Body: Fix broken link in documentation
```

**Resultado Esperado**:
- ✅ Todos 3 issues auto-accepted en <10 segundos
- ✅ No stuck issues después de 2 worker cycles (6 minutos)
- ✅ Logs muestran: "processing issues: A,B,C"
- ✅ Logs muestran result para cada issue individual

**Criterios de Éxito**:
- [ ] Worker logs: "reconcile_stuck_admission_reviews: processing issues: X,Y,Z"
- [ ] Worker logs: "evaluating issue #X", "evaluating issue #Y", "evaluating issue #Z"
- [ ] Worker logs: "issue #X review result: accepted" (para cada uno)
- [ ] Todos 3 issues tienen label `scc-accepted`
- [ ] **0 issues stuck** después de 10 minutos
- [ ] Success rate: **100%** (3/3 processed)

**Antes del fix**: ~33% failure rate (1/3 stuck)  
**Después del fix**: 0% failure rate (0/3 stuck)

---

## Test 3: End-to-End Full Pipeline (Integración)

### Objetivo
Validar que ambos fixes funcionan juntos en pipeline completo

### Escenario de Prueba

**Setup**:
1. Crear issue simple con `ready-to-implement`
2. Dejar que worker procese completamente
3. **NO intervenir manualmente** en ningún paso

**Pipeline esperado**:
```
Issue created (manual)
    ↓
Admission review (AUTO - nuevo logging)
    ↓
Auto-accepted (AUTO - fix #1227)
    ↓
Queued (AUTO)
    ↓
Running (AUTO)
    ↓
PR created (AUTO)
    ↓
CI checks (AUTO)
    ↓
PR approved (AUTO)
    ↓
Auto-merge (AUTO - fix #1153 si orphan)
    ↓
PR merged (AUTO)
    ↓
Issue closed (AUTO)
```

**Criterios de Éxito**:
- [ ] Pipeline completo sin intervención manual
- [ ] Tiempo total: <20 minutos
- [ ] Logs muestran ejecución de ambos fixes:
  - [ ] `reconcile_stuck_admission_reviews` con logging
  - [ ] `reconcile_orphan_open_prs` si aplica
- [ ] Issue cerrado automáticamente

---

## Test 4: Stress Test - Batch de Issues

### Objetivo
Validar robustez con múltiples issues concurrentes

### Escenario de Prueba

**Setup**:
1. Crear 5 issues simultáneamente
2. Mezcla de complejidades (simple, medium)
3. Todos con `ready-to-implement`

**Resultado Esperado**:
- ✅ Todos 5 issues procesados
- ✅ No stuck issues
- ✅ Auto-acceptance: 100% (5/5)
- ✅ PRs created: 100% (5/5)
- ✅ Auto-merge: 100% (5/5)

**Criterios de Éxito**:
- [ ] Worker procesa todos sin errors
- [ ] No stuck admission después de 30 minutos
- [ ] Todos los PRs creados y merged
- [ ] Throughput: ~4 issues/hora (promedio)

---

## Métricas de Validación

### Pre-Fix (Baseline)
- **Admission stuck rate**: ~33% (1/3 issues)
- **Orphan PR reconciliation**: 0% (no se detectaban)

### Post-Fix (Target)
- **Admission stuck rate**: 0% (0/N issues)
- **Orphan PR reconciliation**: 100% (N/N PRs)

### SLA Targets
- Admission → Accepted: <10 segundos
- Issue → PR created: <10 minutos
- PR created → Merged: <10 minutos
- **Total E2E**: <20 minutos

---

## Logs a Monitorear

### Worker Logs (VPS)
```bash
# Admission reconciliation
grep "reconcile_stuck_admission_reviews" /var/log/homedir-sdlc-worker.log

# Orphan PR reconciliation  
grep "reconcile_orphan_open_prs" /var/log/homedir-sdlc-worker.log

# Errores
grep "ERROR\|FAIL" /var/log/homedir-sdlc-worker.log
```

### GitHub Actions
- Deploy workflow success
- CI checks passing
- Auto-merge triggers

---

## Rollback Plan

**Si Test 1 falla** (Orphan PRs):
- Revert PR #1153
- Redeploy worker
- Issues orphan se mergean manualmente

**Si Test 2 falla** (Admission stuck):
- Revert PR #1227  
- Redeploy worker
- Issues stuck se acceptan manualmente con label

**Si Test 3/4 fallan** (Pipeline completo):
- Revert ambos PRs
- Redeploy worker
- Rollback a último worker estable (deployment 2026-07-11 20:54:31)

---

## Checklist de Validación

### Pre-requisitos
- [x] PR #1153 merged y deployed
- [ ] PR #1227 merged
- [ ] PR #1227 deployed
- [ ] Worker restarted con nuevo código

### Ejecución de Tests
- [ ] Test 1: Orphan PRs (fix #1153)
- [ ] Test 2: Admission stuck (fix #1227)
- [ ] Test 3: E2E full pipeline
- [ ] Test 4: Stress test (5 issues)

### Validación de Logs
- [ ] Worker logs confirman ejecución de fixes
- [ ] No errors en logs
- [ ] Metrics dentro de SLA

### Sign-off
- [ ] Todos los tests passed
- [ ] Métricas post-fix validadas
- [ ] Sistema estable por 1 hora
- [ ] Documentación actualizada

---

**Preparado por**: Claude Sonnet 4.5  
**Fecha**: 2026-07-12 16:40 UTC  
**Status**: ⏳ Waiting for PR #1227 merge
