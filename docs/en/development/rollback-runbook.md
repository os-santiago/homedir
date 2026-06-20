# Rollback Runbook

## When to Rollback

- Production healthcheck fails after deploy
- Error rate spikes > 5% on critical routes
- Business logic regression detected in manual validation
- Security vulnerability discovered post-deploy

## Rollback Methods

### Method 1: Git Revert (preferred for simple PRs)

```bash
# Identify the merge commit to revert
git log --oneline --merges -10 origin/main

# Create revert branch from main
git checkout -b revert/rollback-$(date +%Y%m%d) origin/main

# Revert the merge commit
git revert -m 1 <merge-commit-sha>

# Push and let CI deploy
git push origin revert/rollback-$(date +%Y%m%d)
# Open PR, let Production Release workflow deploy the revert
```

### Method 2: Re-deploy Previous Tag (for complex reverts)

```bash
# List recent tags
git tag --sort=-version:refname | head -10

# Trigger deploy of previous tag manually
# Use GitHub Actions workflow_dispatch with the previous tag
# Or locally:
gh workflow run "Production Release" --ref v<previous-version>
```

### Method 3: VPS Manual Intervention (last resort)

```bash
# SSH to VPS and force pull previous release
ssh -p "$DEPLOY_PORT" "$DEPLOY_USER@$DEPLOY_HOST" "
  DEPLOY_TRIGGER=manual-rollback \
  DEPLOY_TARGET_TAG=v<previous-version> \
  /usr/local/bin/homedir-update.sh
"
```

## Post-Rollback Checklist

- [ ] Healthcheck passes (`/q/health` returns 200)
- [ ] Critical routes return HTTP 200 (`/`, `/comunidad`, `/eventos`, `/proyectos`)
- [ ] Rollback verified with manual smoke test
- [ ] Root cause issue created with `wos-review` label
- [ ] Fix PR opened with explicit prevention note

Modelo: DeepSeek V4 Flash Free