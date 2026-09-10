# PR Ready - K3s Worker with SCC Support

**Estado**: Archivos listos para PR  
**Fecha**: 2026-08-24 00:40 UTC  
**Branch**: `feat/k3s-worker-scc-support`

---

## Comandos para Crear PR

```bash
cd D:\git\homedir

# 1. Crear branch
git checkout -b feat/k3s-worker-scc-support

# 2. Stage archivos
git add .github/workflows/build-worker-container.yml
git add container/Containerfile.worker
git add container/worker-entrypoint.sh
git add container/sc-agent-config.json

# 3. Commit
git commit -m "feat(k3s): add CI/CD workflow for worker container with SCC support

## Summary
Implements automated container build pipeline for AI-SDLC worker with SCC (sc-agent-cli) integration to restore full 99% autonomy in K3s deployment.

## Changes

### New Files
- **.github/workflows/build-worker-container.yml**: GitHub Actions workflow for automated container builds
  - Triggers on push to main (container/, platform/scripts/ paths)
  - Triggers on workflow_dispatch (manual)
  - Builds container with Docker Buildx
  - Publishes to quay.io (primary) + ghcr.io (fallback)
  - Verifies SCC installation in final image

- **container/sc-agent-config.json**: SCC configuration template
  - NVIDIA API profile configuration
  - Template with \${NVIDIA_API_KEY} placeholder
  - Substituted at runtime by entrypoint

### Modified Files
- **container/Containerfile.worker**: Install SCC from sc-agent-cli repo
  - Install Node.js 20.x (required by sc-agent-cli)
  - Copy sc-agent-cli pre-compiled dist/ from build context
  - Install runtime dependencies only (--omit=dev --ignore-scripts)
  - Manual installation to /usr/local/lib/node_modules/sc-agent-cli/
  - Symlinks: bin/sc.js -> /usr/local/bin/sc -> /usr/local/bin/scc

- **container/worker-entrypoint.sh**: Configure SCC at runtime
  - Create ~/.sc-agent/config.json from template
  - Substitute \${NVIDIA_API_KEY} with actual secret value
  - Verify SCC installation and log version
  - Log SCC profile configuration

## Technical Details

### SCC (sc-agent-cli)
- **Repository**: https://github.com/os-santiago/sc-agent-cli (local: D:\\git\\sc-agent-cli)
- **Type**: Node.js/TypeScript CLI
- **Function**: Provider-agnostic AI agent compatible with OpenAI-compatible APIs
- **Integration**: NVIDIA API (meta/llama-3.3-70b-instruct model)
- **Usage**: Generates code implementations for issue processing

### Build Context
GitHub Actions workflow uses build-context feature to include sc-agent-cli:
\`\`\`yaml
build-contexts: |
  sc-agent-cli=./sc-agent-cli
\`\`\`

Containerfile references it:
\`\`\`dockerfile
COPY --from=sc-agent-cli package.json /tmp/sc-agent-cli/
COPY --from=sc-agent-cli bin /tmp/sc-agent-cli/bin/
COPY --from=sc-agent-cli dist /tmp/sc-agent-cli/dist/
\`\`\`

### Registry Strategy
- **Primary**: quay.io/opensourcesantiago/homedir-ai-sdlc-worker:latest
- **Fallback**: ghcr.io/os-santiago/homedir-ai-sdlc-worker:latest
- **Tags**: latest + timestamp-sha (YYYYMMDD-HHMMSS-githash)

### K3s Integration
K3s deployment auto-pulls updated image:
- ImagePullPolicy: Always (or IfNotPresent with imagePullSecrets)
- Auto-reconciliation via deployment controller
- Manual force: \`kubectl delete pods -n homedir-ai-sdlc -l app=ai-sdlc-worker\`

## Impact

### Before (Current State)
- ❌ Worker in K3s: reconcile-only mode (~20% functionality)
- ❌ NO processes new issues
- ❌ NO generates code with IA
- ❌ NO CI remediation
- ❌ Autonomy: 0% in issue processing

### After (With This PR)
- ✅ Worker in K3s: full functionality
- ✅ Processes issues automatically
- ✅ Generates code with NVIDIA AI API
- ✅ Creates PRs automatically
- ✅ CI checks remediation
- ✅ Auto-merge functional
- ✅ **Autonomy: 99%** (restored)
- ✅ **E2E time: 16-20 min** (matches Podman baseline)

## Verification Plan

### 1. GitHub Actions Build (5-10 min)
After PR merge, workflow executes automatically:
- ✅ Checkout homedir + sc-agent-cli repos
- ✅ Build image with SCC
- ✅ Push to quay.io + ghcr.io
- ✅ Verify SCC installed: \`which scc && scc --version\`

### 2. K3s Deployment (2-3 min)
K3s automatically pulls updated image:
\`\`\`bash
# Monitor deployment
wsl ssh -i ~/.ssh/id_ed25519 root@72.60.141.165
k3s kubectl get pods -n homedir-ai-sdlc -w

# Check logs for SCC initialization
k3s kubectl logs -n homedir-ai-sdlc -l app=ai-sdlc-worker --tail=100 -f
\`\`\`

**Expected logs**:
\`\`\`
[entrypoint] INFO: SCC found: sc-agent-cli v0.4.2
[entrypoint] INFO: SCC configured with profile: nvidia
[homedir-sdlc-worker] SCC available at /usr/local/bin/scc
\`\`\`

### 3. SCC Verification in Pod (1 min)
\`\`\`bash
# Get pod name
POD=\$(k3s kubectl get pods -n homedir-ai-sdlc -l app=ai-sdlc-worker -o jsonpath='{.items[0].metadata.name}')

# Exec into pod
k3s kubectl exec -n homedir-ai-sdlc \${POD} -it -- bash

# Test SCC
which scc                                    # /usr/local/bin/scc
scc --version                                # sc-agent-cli v0.4.2
scc chat -m nvidia -yq \"Say OK\"             # OK
\`\`\`

### 4. E2E Test with Real Issue (16-20 min)
\`\`\`bash
# Find oldest ready-to-implement issue
gh issue list -R os-santiago/homedir \\
  --label ready-to-implement \\
  --limit 1 \\
  --json number,title,createdAt \\
  --state open

# Monitor worker processing
k3s kubectl logs -n homedir-ai-sdlc -l app=ai-sdlc-worker --tail=100 -f
\`\`\`

**Expected timeline**:
- T+0: Worker detects issue
- T+3: Admission analysis (SCC)
- T+6: SCC generates implementation
- T+16: PR created
- T+20: CI checks complete
- T+21: Auto-merge executes
- **Total: 16-20 minutes** ✅

### 5. 24-48h Monitoring
Metrics to validate:
- ✅ Heartbeat age < 5min (95% uptime)
- ✅ Issues processed >= 1 per day
- ✅ PRs created automatically
- ✅ CI remediation working
- ✅ Auto-merge rate >= 90%
- ✅ E2E time: 16-20 min consistently

## Dependencies

### GitHub Secrets/Vars Required
- \`secrets.GH_TOKEN\`: Access to sc-agent-cli private repo
- \`secrets.QUAY_PASSWORD\`: Quay.io registry push
- \`vars.QUAY_USERNAME\`: Quay.io registry username
- \`vars.REGISTRY\`: quay.io (default)
- \`vars.QUAY_ORG\`: opensourcesantiago (default: repo owner)

### K3s Secrets Required
- \`NVIDIA_API_KEY\`: NVIDIA AI API key (already configured in K3s secret)
- \`GH_TOKEN\`: GitHub access (already configured in K3s secret)

## Rollback Plan

If issues arise post-deployment:

### Rollback Container Image
\`\`\`bash
# Find previous working tag
gh run list --workflow=build-worker-container.yml --limit 10

# Manually set previous image
k3s kubectl set image deployment/ai-sdlc-worker \\
  worker=quay.io/opensourcesantiago/homedir-ai-sdlc-worker:<previous-tag> \\
  -n homedir-ai-sdlc
\`\`\`

### Fallback to Podman Deployment
If K3s worker fails completely:
\`\`\`bash
# VPS still has working Podman deployment
ssh homedir-sdlc@vps
systemctl --user status homedir-sdlc-worker.timer

# Re-enable if disabled
systemctl --user enable --now homedir-sdlc-worker.timer
\`\`\`

## Testing Checklist

Before merge:
- [ ] Workflow file syntax valid (GitHub validates on PR)
- [ ] All required secrets/vars configured in repo settings
- [ ] sc-agent-cli repo accessible via GH_TOKEN

After merge:
- [ ] GitHub Actions build succeeds
- [ ] Images pushed to quay.io + ghcr.io
- [ ] K3s pods restart with new image
- [ ] SCC found in pod logs
- [ ] SCC can execute test prompt
- [ ] Worker processes test issue
- [ ] E2E test completes in 16-20 min
- [ ] No errors in worker logs for 1 hour

## Related Issues

Fixes: N/A (restoration of functionality post-K3s migration)

Related to:
- K3s migration (#XXXX)
- SCC discovery and integration

## References

- **SCC Documentation**: D:\\git\\homedir-infra\\K3S-SCC-INSTALLATION-COMPLETE.md
- **Build Status**: D:\\git\\homedir-infra\\K3S-SCC-BUILD-STATUS.md
- **sc-agent-cli Repo**: https://github.com/os-santiago/sc-agent-cli
- **NVIDIA API**: https://integrate.api.nvidia.com/v1

---

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"

# 4. Push branch
git push origin feat/k3s-worker-scc-support

# 5. Create PR
gh pr create \
  --title "feat(k3s): add CI/CD workflow for worker container with SCC support" \
  --body-file PR-READY.md \
  --base main \
  --head feat/k3s-worker-scc-support \
  --label "enhancement,k3s,ci-cd,ai-sdlc"
```

---

## Archivos en PR

### Nuevos
1. `.github/workflows/build-worker-container.yml` - Workflow CI/CD
2. `container/sc-agent-config.json` - Template config SCC

### Modificados
1. `container/Containerfile.worker` - Instalación SCC
2. `container/worker-entrypoint.sh` - Setup runtime SCC

### Temporales (NO incluir)
- `COMMIT-READY.md` - Eliminar
- `PR-READY.md` - Este archivo (usar para PR body, luego eliminar)
- `deploy-k3s-worker-with-scc.sh` - Ya no necesario (CI/CD automático)

---

## Post-Merge

1. **Automático**: GitHub Actions build + push a quay.io
2. **Automático**: K3s pull imagen actualizada
3. **Manual**: Verificar SCC en logs
4. **Manual**: E2E test con issue antiguo
5. **Monitoreo**: 24-48h para certificar 99% autonomía

---

## Estado Final Esperado

✅ Worker K3s con SCC funcional  
✅ Autonomía 99% restaurada  
✅ E2E time 16-20 min  
✅ Flujo igual a pre-migración K3s  
✅ **Sistema completamente funcional**
