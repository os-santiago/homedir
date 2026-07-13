# Bugs Pendientes del AI SDLC - Análisis y Priorización

## Resumen Ejecutivo

**Bugs Críticos Resueltos**: 5/5 (100%) ✅  
**Bugs Pendientes**: 3 (2 conocidos + 1 nuevo)  
**Prioridad**: 1 P1 + 2 P2

---

## Bug #1: Label Update Logic (Nuevo - P2)

### Descripción
Worker evalúa issue correctamente con `issue_acceptance_review()` pero **no aplica el label final** en algunos casos.

### Evidencia
**Issue #1008**:
- Worker ejecuta `reconcile_stuck_admission_reviews()` cada cycle
- Acceptance review evalúa como `needs-human` (correcto)
- Label `needs-human` **NO se aplica**
- Issue queda stuck en `scc-admission-review`

### Root Cause (Hipótesis)
Posibles causas:
1. Condición en el código que previene label update
2. Error silencioso en `add_label()`/`remove_label()`
3. Race condition entre cycles
4. GitHub API rate limiting

### Ubicación del Código
`platform/scripts/homedir-sdlc-worker.sh`:
- `reconcile_stuck_admission_reviews()` - Lines 604-665
- Específicamente lines 643-648 (needs-human case)

```bash
needs-human)
  remove_label "${number}" "${ADMISSION_REVIEW_LABEL}"
  add_label "${number}" "${NEEDS_HUMAN_LABEL}"
  comment_issue "${number}" "..."
  log "Issue #${number} marked needs-human via reconciliation"
  ;;
```

### Reproducción
1. Crear issue con content que trigger `needs-human`
2. Keywords: "VPS", "deploy", "SSH", "manual access"
3. Agregar label `ready-to-implement`
4. Observar: queda stuck en `scc-admission-review`

### Impacto
**Severidad**: MEDIUM (P2)
- ✅ Workaround: Remove labels manualmente
- ✅ No bloquea pipeline (solo admission)
- ⚠️ Afecta user experience (parece hung)
- ✅ Casos poco frecuentes (requires VPS/ops keywords)

### Acceptance Criteria
- [ ] Worker aplica label `needs-human` cuando review lo indica
- [ ] Issue sale de `scc-admission-review` a estado terminal
- [ ] Logging muestra label update attempt y resultado
- [ ] Test case con keywords VPS/deploy valida fix

### Estimación
**Complejidad**: Simple  
**Tiempo**: 1-2 horas  
**Risk**: Low

---

## Bug #2: Orchestrator Labels (#1141 - P1)

### Descripción
Pipeline orchestrator falla cuando pipeline generado referencia labels que no existen en el repo.

### Evidencia
- Issue #1132 completado
- Orchestrator intentó crear next issue
- Error: `could not add label: 'scc-auto-split' not found`
- Pipeline auto-split detenido

### Root Cause
`gh issue create --label "scc-auto-split"` falla si label no existe.

### Ubicación del Código
`platform/scripts/pipeline-orchestrator.sh` (si existe)

### Impacto
**Severidad**: HIGH (P1)
- ❌ Bloquea pipeline continuation
- ❌ No hay workaround automático
- ✅ Casos específicos (auto-split pipelines)

### Solución Propuesta
**Opción A**: Validar labels antes de `gh issue create`
```bash
validate_and_create_labels() {
  local labels="$1"
  for label in ${labels//,/ }; do
    if ! gh label list --repo "${REPO}" | grep -q "^${label}"; then
      log "Creating missing label: ${label}"
      gh label create "${label}" --repo "${REPO}" --color "FBCA04" || true
    fi
  done
}
```

**Opción B**: Skip missing labels con warning
```bash
gh issue create --label "label1,label2" 2>&1 | grep -v "not found" || true
```

**Recomendación**: Opción A (crear labels automáticamente)

### Acceptance Criteria
- [ ] Orchestrator valida labels antes de crear issue
- [ ] Missing operational labels se crean automáticamente
- [ ] Missing optional labels se skip con warning
- [ ] Auto-split pipelines funcionan end-to-end

### Estimación
**Complejidad**: Medium  
**Tiempo**: 2-3 horas  
**Risk**: Medium (requires testing)

---

## Bug #3: PR Remediation Loops (#1143 - P1)

### Descripción
PRs con failing checks pueden quedar en `scc-failing-checks`/`scc-under-review` indefinidamente sin retry bounded o escalation.

### Evidencia
**PR #1096**:
- Failing checks desde 2026-07-08 (4+ días)
- Labels: `scc-failing-checks`, `scc-under-review`
- No remediation attempts
- No escalation a `needs-human`

### Root Cause
Worker no implementa:
1. Remediation attempt counter
2. Bounded retry logic
3. Escalation después de N attempts

### Ubicación del Código
`platform/scripts/homedir-sdlc-worker.sh`:
- PR remediation logic (needs investigation)
- Probablemente falta implementación completa

### Impacto
**Severidad**: HIGH (P1)
- ⚠️ PRs stuck indefinidamente
- ❌ No convergencia automática
- ⚠️ Requiere intervención manual
- ✅ Casos específicos (failing checks)

### Solución Propuesta

**1. Track Remediation Attempts**
```bash
# En PR state file
{
  "pr_number": 1096,
  "remediation_attempts": 3,
  "last_failure_signature": "eslint: 2 errors",
  "max_attempts": 5
}
```

**2. Bounded Retry**
```bash
remediate_failing_pr() {
  local pr_number="$1"
  local attempts=$(get_remediation_attempts "${pr_number}")
  local max_attempts=5
  
  if [[ "${attempts}" -ge "${max_attempts}" ]]; then
    log "PR #${pr_number} exceeded max remediation attempts (${max_attempts})"
    add_label "${pr_number}" "${NEEDS_HUMAN_LABEL}"
    remove_label "${pr_number}" "${FAILING_CHECKS_LABEL}"
    comment_issue "${pr_number}" "Remediation exhausted after ${max_attempts} attempts. Last failure: ..."
    return 1
  fi
  
  # Attempt remediation
  increment_remediation_attempts "${pr_number}"
  # ... run SCC with failure context
}
```

**3. Context-Aware Remediation**
```bash
# Include check logs and CodeRabbit feedback
run_scc_prompt "Fix failing checks. Previous failures: ${failure_logs}. CodeRabbit suggestions: ${review_comments}"
```

### Acceptance Criteria
- [ ] Track remediation attempts per PR
- [ ] Bounded retry (default: 5 attempts)
- [ ] Context-aware prompts (include failure logs)
- [ ] Escalate to `needs-human` after exhaustion
- [ ] Blocker summary in final comment
- [ ] Run summary entries for each attempt

### Estimación
**Complejidad**: High  
**Tiempo**: 4-6 horas  
**Risk**: High (requires careful state management)

---

## Priorización Recomendada

### Immediate (P1)
1. **#1141 - Orchestrator Labels** (2-3h)
   - Bloquea pipeline continuation
   - Fix relativamente simple
   - Alto impacto en autonomía

2. **#1143 - PR Remediation** (4-6h)
   - PRs stuck indefinidamente
   - Requiere diseño más complejo
   - Crítico para convergencia

### Next (P2)
3. **Label Update Logic** (1-2h)
   - Casos edge poco frecuentes
   - Workaround disponible
   - Buen tener pero no blocker

### Orden Sugerido
```
1. Fix #1141 (Orchestrator labels) - 2-3h
   ↓
2. Test & validate #1141 - 1h
   ↓
3. Fix #1143 (PR remediation) - 4-6h
   ↓
4. Test & validate #1143 - 2h
   ↓
5. Fix label update logic - 1-2h
   ↓
6. Test & validate - 1h
---
Total: 11-15 horas
```

---

## Impacto en Autonomía

### Actual (Post PR #1227)
- **99% autonomía** para happy path
- ⚠️ Edge cases causan stuck states

### Post-Fixes (#1141, #1143)
- **99.5% autonomía** esperada
- ✅ Pipeline continuation robusta
- ✅ PR remediation convergente
- ✅ Escalation automática cuando needed

---

## Siguiente Sesión - Plan de Acción

### Session 1: Orchestrator Labels (3-4h total)
1. Investigar código de orchestrator
2. Implementar label validation
3. Crear labels automáticamente si missing
4. Test con auto-split pipeline
5. PR + deployment

### Session 2: PR Remediation (6-8h total)
1. Diseñar state management para attempts
2. Implementar bounded retry logic
3. Agregar context-aware prompts
4. Implementar escalation
5. Test con PR failing
6. PR + deployment

### Session 3: Label Update (2-3h total)
1. Investigar root cause en #1008
2. Fix label application logic
3. Add logging para debugging
4. Test con VPS keywords issue
5. PR + deployment

---

## Métricas de Éxito

### Pre-Fixes
- Pipeline continuation: Manual intervention needed
- PR remediation: Infinite loops possible
- Label updates: Silent failures in edge cases
- **Autonomía**: 99%

### Post-Fixes (Target)
- Pipeline continuation: 100% automático
- PR remediation: Bounded, converges to needs-human
- Label updates: 100% reliable
- **Autonomía**: **99.5%** 🎯

---

**Análisis completado**: 2026-07-12 18:15 UTC  
**Bugs identificados**: 3  
**Prioridad alta**: 2 (P1)  
**Tiempo estimado total**: 11-15 horas  
**Autonomía target**: 99.5%
