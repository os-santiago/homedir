# Worker Integration - Auto-Unblock PRs

## Problema Identificado

**Usuario**: "el flujo debe ser capaz de detectar cuando el merge está bloqueado y usar el flujo de agente para solucionarlo"

**Issue**: PR #1232 estaba bloqueado por conversaciones de CodeRabbit sin resolver, causando deadlock.

## Solución Implementada

### 1. Script de Auto-Unblock

**File**: `platform/scripts/pr-unblock-automation.sh`

**Función**: Detecta y resuelve automáticamente bloqueos comunes:
- ✅ Conversaciones de CodeRabbit sin resolver
- ⚠️ Conversaciones humanas (requiere manual)
- ❌ Merge conflicts (requiere manual)
- ❌ CI failing (requiere manual)

### 2. Integración con Worker

**Dónde integrar**: `platform/scripts/homedir-sdlc-worker.sh`

**Función a modificar**: `reconcile_orphan_open_prs()` (línea ~1573)

**Lógica**:
```bash
# En reconcile_orphan_open_prs(), después de detectar PR limpio pero bloqueado

if [[ "${merge_state}" == "BLOCKED" ]] && [[ "${failing_count}" -eq 0 ]] && [[ "${pending_count}" -eq 0 ]]; then
  log "PR #${pr_number} is BLOCKED despite clean checks - attempting auto-unblock"
  
  if [[ -x "${HOME}/platform/scripts/pr-unblock-automation.sh" ]]; then
    if "${HOME}/platform/scripts/pr-unblock-automation.sh" "${pr_number}"; then
      log "PR #${pr_number} unblocked successfully"
      # Re-check PR state after unblock
      continue  # Skip to next iteration to re-process
    else
      log "PR #${pr_number} unblock failed - may require manual intervention"
      add_label "${pr_number}" "${NEEDS_HUMAN_LABEL}"
    fi
  fi
fi
```

### 3. Casos de Uso

#### Caso 1: CodeRabbit Conversations ✅ AUTO-RESUELVE
```
PR tiene: BLOCKED + checks passing + unresolved CodeRabbit threads
→ Script auto-resolve threads
→ PR pasa a CLEAN
→ Auto-merge procede
```

#### Caso 2: Human Conversations ⚠️ REQUIERE MANUAL
```
PR tiene: BLOCKED + unresolved human threads
→ Script detecta pero NO auto-resolve
→ Aplica label needs-human
→ Requiere intervención manual
```

#### Caso 3: Merge Conflicts ❌ REQUIERE MANUAL
```
PR tiene: DIRTY + merge conflicts
→ Script detecta
→ Aplica label needs-human
→ Requiere resolución manual de conflicts
```

## Cambios Necesarios en Worker

### Archivo a Modificar
`platform/scripts/homedir-sdlc-worker.sh`

### Ubicación
Función `reconcile_orphan_open_prs()` - después de línea 1613 (check de is_draft/failing/pending)

### Código a Agregar
```bash
# After line 1613 - check for BLOCKED state with clean checks
merge_state=$(gh pr view "${pr_number}" --repo "${REPO}" --json mergeStateStatus --jq '.mergeStateStatus')

if [[ "${merge_state}" == "BLOCKED" ]]; then
  log "orphan PR #${pr_number} is BLOCKED despite passing checks - attempting auto-unblock"
  
  if [[ -x "${WORKSPACE_ROOT}/platform/scripts/pr-unblock-automation.sh" ]]; then
    if "${WORKSPACE_ROOT}/platform/scripts/pr-unblock-automation.sh" "${pr_number}" 2>&1 | tee -a "${LOGFILE}"; then
      log "PR #${pr_number} auto-unblocked successfully"
      # Continue to next cycle to re-process
      continue
    else
      log "PR #${pr_number} auto-unblock failed - escalating to needs-human"
      add_label "${pr_number}" "${NEEDS_HUMAN_LABEL}"
      comment_issue "${pr_number}" "Autonomous SDLC detected merge block that cannot be auto-resolved. Manual intervention required. Check PR conversations and merge requirements."
      continue
    fi
  else
    log "WARNING: pr-unblock-automation.sh not found - skipping auto-unblock"
  fi
fi
```

## Deployment Steps

### 1. Add Script to Repository
```bash
git add platform/scripts/pr-unblock-automation.sh
git commit -m "feat(sdlc): add PR auto-unblock automation script"
```

### 2. Modify Worker Script
Add integration code to `reconcile_orphan_open_prs()`

### 3. Test Locally
```bash
# Test with actual PR
./platform/scripts/pr-unblock-automation.sh 1232

# Expected: Resolves CodeRabbit conversations
```

### 4. Deploy to VPS
Via `.github/workflows/deploy-worker.yml` (auto-deploys on push)

## Benefits

### Before (Manual)
```
1. PR blocked by CodeRabbit conversation
2. Human notices (if monitoring)
3. Human manually resolves conversation
4. PR unblocks and auto-merges
Time: Minutes to hours (depends on human availability)
```

### After (Automated)
```
1. PR blocked by CodeRabbit conversation
2. Worker detects block (next 3-min cycle)
3. Worker auto-resolves CodeRabbit conversation
4. PR unblocks and auto-merges
Time: 3-6 minutes (next worker cycle)
```

## Edge Cases Handled

### ✅ Safe to Auto-Resolve
- CodeRabbit review comments
- Other bot comments (dependabot, etc)

### ⚠️ Escalate to Human
- Unresolved human conversations
- Required approvals missing
- Unknown block reasons

### ❌ Cannot Auto-Resolve
- Merge conflicts (DIRTY)
- Failing CI checks (UNSTABLE)
- Branch protection violations

## Testing Checklist

- [ ] Script detects BLOCKED state
- [ ] Script identifies CodeRabbit conversations
- [ ] Script auto-resolves bot conversations
- [ ] Script skips human conversations
- [ ] Script detects merge conflicts
- [ ] Script detects failing CI
- [ ] Worker integration calls script correctly
- [ ] Worker labels needs-human on failure
- [ ] End-to-end test: blocked PR auto-unblocks

## Future Enhancements

### Phase 2: Smarter Detection
- Parse CodeRabbit suggestions
- Auto-apply trivial fixes (formatting, typos)
- Learn from past unblock patterns

### Phase 3: Proactive Prevention
- Pre-validate before PR creation
- Auto-format code to prevent style comments
- Template validation to prevent common issues

## Related Issues
- Issue #1232: Was blocked, now unblocked
- Issue #1231: Was blocked, now unblocked

## Success Metrics

**Target**:
- Reduce manual PR unblocks from 100% to <10%
- Average unblock time: <6 minutes (2 worker cycles)
- False positive rate: <5%

**Current**:
- Manual unblocks: 100%
- Manual detected issue and resolved conversations
- System now ready for automation

---

**Created**: 2026-07-12  
**Status**: Script implemented, worker integration pending  
**Next**: Add to worker and deploy
