# Ready to Commit - K3s Worker with SCC

**Estado**: Archivos listos para commit y push  
**Fecha**: 2026-08-24 00:30 UTC  

---

## Archivos Modificados/Creados

### Nuevos Archivos
1. ✅ `.github/workflows/build-worker-container.yml` - Workflow CI/CD para build automático
2. ✅ `container/sc-agent-config.json` - Template configuración SCC con NVIDIA API
3. ✅ `COMMIT-READY.md` - Este archivo (eliminar después de commit)

### Archivos Modificados
1. ✅ `container/Containerfile.worker` - Instalación de SCC desde sc-agent-cli
2. ✅ `container/worker-entrypoint.sh` - Setup de SCC con NVIDIA_API_KEY

---

## Comando de Commit

```bash
cd D:\git\homedir

# Stage archivos
git add .github/workflows/build-worker-container.yml
git add container/Containerfile.worker
git add container/worker-entrypoint.sh
git add container/sc-agent-config.json

# Commit
git commit -m "feat(k3s): add CI/CD workflow for worker container with SCC support

- Add GitHub Actions workflow for automated container builds
- Install sc-agent-cli (SCC) from local repository D:\git\sc-agent-cli
- Configure NVIDIA API integration for AI-powered code generation
- Publish to quay.io (primary) + ghcr.io (fallback)
- K3s deployment will auto-pull updated image
- Enables full 99% autonomy in K3s worker

SCC (sc-agent-cli):
- Provider-agnostic AI agent CLI (Node.js/TypeScript)
- Uses NVIDIA API with meta/llama-3.3-70b-instruct model
- Generates code implementations for issue processing
- Binary installed as /usr/local/bin/scc

Workflow triggers:
- push to main (paths: container/, platform/scripts/, .github/workflows/)
- workflow_dispatch (manual trigger)

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"

# Push
git push origin main
```

---

## Post-Push: Flujo Automático

### 1. GitHub Actions Ejecuta (5-10 min)
- ✅ Checkout `homedir` + `sc-agent-cli` repos
- ✅ Build imagen con Docker Buildx
- ✅ Push a `quay.io/opensourcesantiago/homedir-ai-sdlc-worker:latest`
- ✅ Push a `ghcr.io/os-santiago/homedir-ai-sdlc-worker:latest`
- ✅ Verifica SCC instalado en imagen

### 2. K3s Descarga Imagen Actualizada
**Opción A - Automático (timer)**:
- K3s reconciliation loop detecta nueva imagen
- Descarga desde quay.io
- Reinicia pods con nueva imagen

**Opción B - Manual (inmediato)**:
```bash
# Via WSL
wsl ssh -i ~/.ssh/id_ed25519 root@72.60.141.165

# Delete pods para forzar re-create con nueva imagen
k3s kubectl delete pods -n homedir-ai-sdlc -l app=ai-sdlc-worker

# Monitor logs
k3s kubectl logs -n homedir-ai-sdlc -l app=ai-sdlc-worker --tail=100 -f
```

### 3. Verificar SCC en K3s (2-3 min)
**Logs esperados**:
```
[entrypoint] INFO: SCC found: sc-agent-cli v0.4.2
[entrypoint] INFO: SCC configured with profile: nvidia
[homedir-sdlc-worker] SCC available at /usr/local/bin/scc
```

**Test manual en pod**:
```bash
# Get pod name
POD=$(k3s kubectl get pods -n homedir-ai-sdlc -l app=ai-sdlc-worker -o jsonpath='{.items[0].metadata.name}')

# Exec into pod
k3s kubectl exec -n homedir-ai-sdlc ${POD} -it -- bash

# Test SCC
which scc
scc --version
scc chat -m nvidia -yq "Say OK"
```

### 4. E2E Test (16-20 min)
```bash
# Find oldest ready-to-implement issue
gh issue list -R os-santiago/homedir \
  --label ready-to-implement \
  --limit 1 \
  --json number,title,createdAt \
  --state open

# Monitor worker processing
k3s kubectl logs -n homedir-ai-sdlc -l app=ai-sdlc-worker --tail=100 -f
```

**Timeline esperado**:
- T+0: Worker detecta issue
- T+3: Admission analysis con SCC
- T+6: SCC genera implementación
- T+16: PR creado
- T+20: CI checks completan
- T+21: Auto-merge ejecuta
- **Total: 16-20 minutos** ✅

### 5. Certificación 99% Autonomía (24-48h)
**Métricas a monitorear**:
- ✅ Heartbeat age < 5min (95% del tiempo)
- ✅ Issues procesados automáticamente
- ✅ PRs creados sin intervención
- ✅ CI checks remediation funciona
- ✅ Auto-merge ejecuta correctamente
- ✅ E2E time: 16-20 min consistente

---

## Detalles Técnicos

### SCC Installation en Container
```dockerfile
# Containerfile.worker
COPY --from=sc-agent-cli package.json package-lock.json /tmp/sc-agent-cli/
COPY --from=sc-agent-cli bin /tmp/sc-agent-cli/bin/
COPY --from=sc-agent-cli dist /tmp/sc-agent-cli/dist/

RUN cd /tmp/sc-agent-cli && \
    npm install --omit=dev --ignore-scripts && \
    mkdir -p /usr/local/lib/node_modules/sc-agent-cli && \
    cp -r package.json bin dist node_modules /usr/local/lib/node_modules/sc-agent-cli/ && \
    chmod +x /usr/local/lib/node_modules/sc-agent-cli/bin/sc.js && \
    ln -s /usr/local/lib/node_modules/sc-agent-cli/bin/sc.js /usr/local/bin/sc && \
    ln -s /usr/local/bin/sc /usr/local/bin/scc
```

### SCC Configuration Runtime
```bash
# worker-entrypoint.sh
mkdir -p ~/.sc-agent
sed "s/\${NVIDIA_API_KEY}/${NVIDIA_API_KEY}/g" \
  /app/config/sc-agent-config.json > ~/.sc-agent/config.json
```

### Workflow Triggers
```yaml
on:
  push:
    branches: [main]
    paths:
      - 'container/Containerfile.worker'
      - 'container/worker-entrypoint.sh'
      - 'container/sc-agent-config.json'
      - 'platform/scripts/homedir-sdlc-worker.sh'
      - '.github/workflows/build-worker-container.yml'
  workflow_dispatch:
```

---

## Troubleshooting

### Si GitHub Actions Build Falla
**Logs**: https://github.com/os-santiago/homedir/actions/workflows/build-worker-container.yml

**Verificar**:
- ✅ `secrets.GH_TOKEN` tiene acceso a repo `sc-agent-cli`
- ✅ `vars.QUAY_USERNAME` y `secrets.QUAY_PASSWORD` configurados
- ✅ Build context incluye ambos repos

### Si K3s No Descarga Imagen
```bash
# Verificar ImagePullPolicy en deployment
k3s kubectl get deployment -n homedir-ai-sdlc ai-sdlc-worker -o yaml | grep imagePullPolicy

# Forzar pull manual
k3s kubectl set image deployment/ai-sdlc-worker \
  worker=quay.io/opensourcesantiago/homedir-ai-sdlc-worker:latest \
  -n homedir-ai-sdlc
```

### Si SCC No Funciona en Pod
```bash
# Check env var
k3s kubectl exec -n homedir-ai-sdlc ${POD} -- env | grep NVIDIA_API_KEY

# Check config file
k3s kubectl exec -n homedir-ai-sdlc ${POD} -- cat ~/.sc-agent/config.json

# Test NVIDIA API connectivity
k3s kubectl exec -n homedir-ai-sdlc ${POD} -- curl -I https://integrate.api.nvidia.com/v1
```

---

## Referencias

- **Workflow**: `.github/workflows/build-worker-container.yml`
- **Containerfile**: `container/Containerfile.worker`
- **Entrypoint**: `container/worker-entrypoint.sh`
- **Config**: `container/sc-agent-config.json`
- **Documentación SCC**: `D:\git\homedir-infra\K3S-SCC-INSTALLATION-COMPLETE.md`
- **Status Build**: `D:\git\homedir-infra\K3S-SCC-BUILD-STATUS.md`

---

## Estado Final Esperado

**Después de push + workflow + deployment**:
- ✅ Worker en K3s con SCC instalado
- ✅ NVIDIA API configurado
- ✅ Procesa issues automáticamente
- ✅ Genera código con IA
- ✅ Crea PRs automáticamente
- ✅ Remediation de CI automática
- ✅ Auto-merge funcional
- ✅ **Autonomía: 99%** (restaurada)
- ✅ **E2E time: 16-20 min** (baseline)

**Sistema completamente funcional igual que antes de la migración K3s** ✅
