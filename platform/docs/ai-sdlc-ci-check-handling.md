# AI SDLC - Manejo de CI Checks

**Última actualización**: 2026-07-09

## Resumen

Sí, el AI SDLC workflow **identifica y responde automáticamente** cuando los checks de CI fallan en un PR autónomo.

## Flujo de Detección y Remediación

### 1. Monitoreo Continuo de PRs

El worker revisa el estado de todos los PRs autónomos cada 3 minutos mediante:

```bash
gh pr view "${pr_number}" \
  --json statusCheckRollup,reviewDecision,latestReviews \
  2>/dev/null
```

### 2. Clasificación de Estado de Checks

La función `pr_checks_state()` categoriza los checks en:

**a) Failing** (Fallando):
- `conclusion` = "failure", "error", "timed_out", "cancelled"
- Se crea un array con detalles de cada check fallido

**b) Pending** (En progreso):
- `status` = "queued", "in_progress", "pending"
- O checks sin conclusión aún

**c) Successful** (Exitosos):
- `conclusion` = "success"

### 3. Labels Automáticos

Según el estado, el worker aplica labels:

| Estado | Label | Descripción |
|--------|-------|-------------|
| Checks fallando | `scc-failing-checks` | CI tiene errores |
| Checks pending | `scc-waiting-checks` | CI ejecutándose |
| Reviews pendientes | `scc-under-review` | Feedback de humanos |
| Coverage gap | `scc-coverage-gap` | PR body incompleto |
| Todo OK | `scc-approved` | Listo para merge |

### 4. Remediación Automática

Cuando `failing_count > 0`, el worker ejecuta **remediación automática**:

```bash
# Líneas 1387-1392 del worker script
if [[ "${failing_count}" -gt 0 ]]; then
  trigger="failing checks on PR #${pr_number}"
  set_flow_labels "${issue}" "${PR_LABEL}" "${FAILING_CHECKS_LABEL}" "${UNDER_REVIEW_LABEL}"
  update_issue_state "${issue}" '.last_pr_state = "failing-checks"'
  run_scc_on_existing_pr "${issue}" "${title}" "${branch}" "${pr_number}" "${pr_url}" \
    "${checks_json}" "${reviews_json}" "${trigger}"
fi
```

### 5. Prompt de Remediación

SCC recibe un prompt especializado con:

```
Continue the autonomous SDLC remediation for ${REPO} issue #${issue}.

Issue title:
${title}

PR:
${pr_url}

Branch:
${branch}

Reason for this remediation cycle:
${trigger}

Failing or pending check context:
{
  "failing": [
    {
      "name": "ci/test-suite",
      "conclusion": "failure",
      "url": "https://github.com/.../checks/..."
    }
  ],
  "pending": [...],
  "successful": [...]
}

Rules:
- Stay on branch ${branch}; never push directly to main.
- Fix only the failing checks or actionable review feedback shown above.
- Keep the change minimal and within the issue/PR scope.
- Run the smallest meaningful validation you can.
- Do not bypass branch protection, required checks, reviews, or rulesets.
```

### 6. Límites de Intentos

**MAX_REMEDIATION_ATTEMPTS**: 3 (por defecto)

Después de 3 intentos fallidos:
- Label `needs-human` agregado
- Issue requiere intervención humana
- Worker deja de intentar remediación automática

```bash
# Líneas 1159-1163
attempts="$(jq -r '.remediation_attempts // 0' "${ISSUE_STATE_DIR}/issue-${issue}.json")"
if [[ "${attempts}" -ge "${MAX_REMEDIATION_ATTEMPTS}" ]]; then
  mark_needs_human "${issue}" "Autonomous remediation reached ${MAX_REMEDIATION_ATTEMPTS} attempts"
  return 0
fi
```

### 7. Escenarios de Remediación

El worker puede remediar automáticamente:

#### ✅ Sí puede remediar:
- **Test fallidos**: Corrige código para que tests pasen
- **Linter errors**: Aplica fixes de formato/estilo
- **Type errors**: Corrige tipos TypeScript/Java
- **Build errors**: Arregla imports, dependencias
- **Coverage insuficiente**: Agrega tests faltantes
- **PR body incompleto**: Actualiza descripción del PR

#### ❌ No puede remediar (requiere humano):
- Fallos de infraestructura (runner down, registry offline)
- Permisos insuficientes
- Branch protection violations
- Secrets faltantes o expirados
- Checks externos no configurados

### 8. Validación Post-Remediación

Después de hacer cambios, el worker:

1. **Valida localmente** (si `VALIDATION_COMMAND` está configurado):
   ```bash
   cd "${WORKDIR}" && bash -lc "${VALIDATION_COMMAND}"
   ```

2. **Commit automático**:
   ```bash
   git commit -m "fix(sdlc): remediate issue #${issue} PR checks" -m "PR #${pr_number}"
   ```

3. **Push al branch**:
   ```bash
   git push origin "${branch}"
   ```

4. **Espera nuevo CI run**:
   - GitHub Actions dispara nuevamente
   - Worker espera 3+ minutos antes de revisar

5. **Re-evaluación**:
   - Si checks pasan → `scc-approved`
   - Si checks fallan → Intento 2/3
   - Si 3 intentos fallidos → `needs-human`

## Ejemplo Real - Timeline

**Issue #1091** (del worker log):

```
13:05:12 [homedir-sdlc-worker] claiming issue #1091
13:05:17 [homedir-sdlc-worker] running SCC for issue #1091
13:27:54 [homedir-sdlc-worker] committing SCC changes for issue #1091
13:35:06 [homedir-sdlc-worker] running SCC remediation for issue #1091 PR #1092
           trigger: technical issue coverage gap on PR #1092
[ERROR]    API Error 404: 404 page not found  ← API externa falló
13:35:48 [homedir-sdlc-worker] SCC remediation exited non-zero (0)
```

**En este caso**:
1. SCC creó PR #1092 inicialmente
2. Worker detectó "coverage gap" (PR body incompleto)
3. Intentó remediar automáticamente
4. API externa falló (no culpa del código)
5. Worker marcó como failed para revisión humana

## Configuración

### Variables de Entorno

```bash
# Máximo de intentos de remediación
MAX_REMEDIATION_ATTEMPTS=3  # Default: 3

# Comando de validación local (opcional)
VALIDATION_COMMAND="mvn test -Dtest=*Test"

# Timeout para SCC remediation (desde ADEV)
SCC_TIMEOUT_SECONDS=300  # 5 min para simple
SCC_TIMEOUT_SECONDS=600  # 10 min para medium
SCC_TIMEOUT_SECONDS=900  # 15 min para complex
```

### Labels Utilizados

```bash
WAITING_CHECKS_LABEL="scc-waiting-checks"
FAILING_CHECKS_LABEL="scc-failing-checks"
UNDER_REVIEW_LABEL="scc-under-review"
COVERAGE_GAP_LABEL="scc-coverage-gap"
APPROVED_LABEL="scc-approved"
NEEDS_HUMAN_LABEL="needs-human"
```

## Monitoreo

### Ver estado actual de un PR

```bash
gh pr view <PR_NUMBER> -R os-santiago/homedir \
  --json statusCheckRollup \
  --jq '.statusCheckRollup[] | select(.conclusion != "success")'
```

### Ver intentos de remediación de un issue

```bash
cat .local/state/homedir-sdlc/issues/issue-<NUMBER>.json | jq '{
  remediation_attempts,
  last_pr_state,
  last_remediation_noop_at,
  approved_at
}'
```

### Ver logs de remediación

```bash
tail -f /home/homedir-sdlc/.local/state/homedir-sdlc/logs/worker.log | \
  grep -E "(remediation|failing|checks)"
```

## Debugging

### Check manualmente el estado de checks

```bash
pr_json=$(gh pr view 1100 -R os-santiago/homedir \
  --json statusCheckRollup,reviewDecision,latestReviews)

# Ver checks fallando
echo "$pr_json" | jq '.statusCheckRollup[] | select(.conclusion == "failure")'

# Ver checks pending
echo "$pr_json" | jq '.statusCheckRollup[] | select(.status == "in_progress")'

# Ver checks exitosos
echo "$pr_json" | jq '.statusCheckRollup[] | select(.conclusion == "success")'
```

### Forzar re-evaluación de PR

```bash
# Como usuario homedir-sdlc en el VPS
/home/homedir-sdlc/.local/bin/homedir-sdlc-worker.sh
```

## Mejores Prácticas

### Para Issues Autónomos

1. **Acceptance Criteria claros**: Facilita que SCC sepa qué debe pasar en CI
2. **Tests específicos**: Mencionar qué tests deben pasar
3. **Scope limitado**: Issues atómicos son más fáciles de remediar

### Para CI Configuration

1. **Fast feedback**: Checks rápidos primero (lint → type → unit → integration)
2. **Clear error messages**: Outputs verbosos ayudan a SCC a diagnosticar
3. **Idempotent tests**: Tests que no dependen de orden o estado externo
4. **Reasonable timeouts**: Dar tiempo suficiente pero no excesivo

### Para Monitoring

1. **Alertas en failures**: Notificar cuando `remediation_attempts >= 3`
2. **Dashboard de métricas**: Tasa de remediación exitosa
3. **Review periódico**: Issues con `needs-human` requieren atención

## Referencias

- **Código**: `platform/scripts/homedir-sdlc-worker.sh`
  - Líneas 961-992: `pr_checks_state()` - Detección de estado
  - Líneas 1387-1393: Trigger de remediación cuando checks fallan
  - Líneas 1148-1238: `run_scc_on_existing_pr()` - Ejecución de remediación
  - Líneas 1102-1145: `build_remediation_prompt()` - Prompt especializado

- **Issues de ejemplo**:
  - #1091: Remediación intentada por coverage gap
  - #1098: Fallido por API externa degradada
  - #1099: Exitoso en primera ejecución

- **Labels workflow**: `.github/workflows/ai-sdlc.yml`
