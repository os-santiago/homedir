# Bug Report: Issue #1221 Stuck en Admission Review

## Resumen
Issue #1221 lleva **25+ minutos** stuck en `scc-admission-review` a pesar de que la función `reconcile_stuck_admission_reviews()` está desplegada y funcionando para otros issues.

---

## Evidencia

### Issue #1221 - Estado Actual
- **Creado**: 2026-07-12 14:52:51 UTC
- **Labels actuales**: `ready-to-implement`, `scc-admission-review`, `documentation`
- **Último comentario**: 2026-07-12 15:11:59 UTC
- **Tiempo stuck**: 25+ minutos

### Otros Issues en el Mismo Batch
| Issue | Creado | Auto-accepted | Tiempo |
|-------|--------|---------------|--------|
| #1219 | 14:52:51 | ✅ 14:52:56 | **<5 segundos** |
| #1220 | 14:52:51 | ✅ 14:52:56 | **<5 segundos** |
| #1221 | 14:52:51 | ❌ STUCK | **25+ minutos** |

**Observación**: Los 3 issues se crearon en el mismo segundo, pero solo #1221 no se auto-acceptó.

---

## Análisis

### 1. La función `reconcile_stuck_admission_reviews()` ESTÁ activa
**Evidencia**:
- Issues #1219 y #1220 fueron auto-aceptados
- Deployment del worker: 2026-07-11 20:54:31 UTC (exitoso)
- Commit con fix #1140 está en main: `13a374f6`

### 2. El issue cumple los criterios de la query
```bash
gh issue list --repo os-santiago/homedir \
  --state open \
  --label "ready-to-implement" \
  --label "scc-admission-review"
```
**Resultado**: Issue #1221 **SÍ aparece** en la query

### 3. El issue debería ser auto-aceptado
**Test manual de `issue_acceptance_review()`**:
```python
title = "[e2e-test-7] Add table of contents to README"
body = "## Problem Statement\nREADME is long but lacks a table of contents..."
# Resultado: {"status": "accepted", "reasons": []}
```
**Conclusión**: El issue **SÍ pasa** la review de acceptance

---

## Hipótesis

### Hipótesis 1: Error en el while loop (MÁS PROBABLE)
La función usa un `while IFS= read -r` loop que puede fallar silenciosamente:

```bash
jq -c '.[]' <<<"${issues_json}" | while IFS= read -r issue_json; do
    # ...
done
```

**Problema potencial**: Si hay un error en el procesamiento de un issue anterior en el loop, podría hacer que el loop termine sin procesar #1221.

**Evidencia**: Los issues se procesan en orden de la query. Si #1221 es el tercer issue en la lista y hay un error en el procesamiento de #1220 o #1219, #1221 nunca se procesaría.

### Hipótesis 2: Timing issue
Posible condición de carrera:
1. Worker detecta #1221 en `scc-admission-review`
2. Comienza a procesarlo
3. Algo interrumpe el procesamiento
4. El label nunca se actualiza

### Hipótesis 3: Variable scope en el while loop
En bash, los `while` loops en pipes crean un subshell. Variables modificadas dentro del loop no persisten fuera. Esto podría causar que los comandos `add_label` no se ejecuten correctamente.

---

## Reproducción

1. Crear 3 issues simultáneamente con `ready-to-implement`
2. Observar que 2 se auto-aceptan y 1 queda stuck
3. El issue stuck sigue recibiendo comentarios del worker cada 3 minutos

**Frecuencia**: 1/3 issues (33%) - posiblemente relacionado con el orden en el loop

---

## Solución Propuesta

### Corto Plazo: Workaround Manual
Agregar label `scc-accepted` manualmente al issue #1221 para desbloquearlo.

### Medio Plazo: Fix en el Código

**Opción A**: Reemplazar el while loop con un array iteration:
```bash
reconcile_stuck_admission_reviews() {
  local issues_json
  issues_json="$(gh issue list ...)"
  
  # Usar mapfile en lugar de while read
  local issue_numbers
  mapfile -t issue_numbers < <(jq -r '.[].number' <<<"${issues_json}")
  
  for number in "${issue_numbers[@]}"; do
    # Re-fetch issue details para cada uno
    local issue_data
    issue_data="$(gh issue view "${number}" --json title,body,labels)"
    # Process...
  done
}
```

**Opción B**: Agregar logging extensivo para diagnosticar:
```bash
reconcile_stuck_admission_reviews() {
  log "reconcile_stuck_admission_reviews: START"
  log "Issues found: $(jq -r '.[].number' <<<"${issues_json}" | tr '\n' ',')"
  
  jq -c '.[]' <<<"${issues_json}" | while IFS= read -r issue_json; do
    number="$(jq -r '.number' <<<"${issue_json}")"
    log "Processing issue #${number} in reconciliation"
    # ...
  done
  
  log "reconcile_stuck_admission_reviews: END"
}
```

**Opción C**: Usar process substitution en lugar de pipe:
```bash
while IFS= read -r issue_json; do
  # ...
done < <(jq -c '.[]' <<<"${issues_json}")
```
Esto evita el subshell creado por el pipe.

---

## Impacto

**Severidad**: MEDIUM
- **Workaround disponible**: Sí (label manual)
- **Frecuencia**: ~33% de issues (basado en muestra de 3)
- **Afecta autonomía**: Sí, pero no bloquea completamente el sistema

**Afectados**:
- Issues que llegan al worker en el tercer+ posición en la query
- Solo afecta la función `reconcile_stuck_admission_reviews()`
- NO afecta la auto-acceptance inicial (que funciona para #1219 y #1220)

---

## Próximos Pasos

1. ✅ Documentar el bug (este documento)
2. ⏳ Aplicar workaround manual a #1221
3. ⏳ Crear PR con fix (Opción C recomendada)
4. ⏳ Agregar tests para verificar que todos los issues en la query se procesan
5. ⏳ Agregar logging para diagnosticar futuros casos

---

**Reportado**: 2026-07-12 15:15 UTC  
**Reporter**: Claude Sonnet 4.5  
**Affected versions**: Worker deployed at 2026-07-11 20:54:31 UTC  
**Related PR**: #1151 (fix #1140 - admission stuck reviews)
