# VPS Migration to Podman - Deployment Guide

**Target VPS**: 72.60.141.165  
**Date**: 2026-07-18  
**Goal**: Zero-downtime migration from systemd-native to Podman pods

## 🔐 Prerequisites

SSH access required:
```bash
ssh -i ~/.ssh/id_ed25519 root@72.60.141.165
```

If passphrase is required, use `ssh-add` first:
```bash
eval "$(ssh-agent -s)"
ssh-add ~/.ssh/id_ed25519
# Enter passphrase when prompted
```

## 📋 Pre-Migration Checklist

### 1. Verify Current State
```bash
# Check current service status
systemctl status homedir-sdlc-worker.service

# Check state directory size
du -sh /var/lib/homedir-sdlc/

# Count tracked items
find /var/lib/homedir-sdlc/issues -type f | wc -l
find /var/lib/homedir-sdlc/prs -type f | wc -l
find /var/lib/homedir-sdlc/autonomous-decisions -type f | wc -l

# Check recent heartbeat
cat /var/lib/homedir-sdlc/heartbeat.json | jq

# Verify podman installed
podman --version
```

### 2. Backup Current Configuration
```bash
# Backup environment
cp ~/.config/homedir-sdlc/worker.env ~/homedir-sdlc-backup-$(date +%Y%m%d).env

# Extract GH_TOKEN for reuse
grep "GH_TOKEN=" ~/.config/homedir-sdlc/worker.env
```

## 🚀 Migration Steps (Zero Downtime)

### Step 1: Clone/Update Repository
```bash
cd /root
git clone https://github.com/os-santiago/homedir.git || (cd homedir && git fetch && git checkout main && git pull)
cd homedir
```

### Step 2: Configure Environment
```bash
# Copy production template
cp container/config/production.env container/config/production.local.env

# Edit configuration
nano container/config/production.local.env
```

**Required settings** (paste from backup):
```bash
GH_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxx  # From backup
HOMEDIR_SDLC_REPO=os-santiago/homedir
SCC_PROFILE=nvidia  # If GPU available, else remove
HOMEDIR_SDLC_CONCURRENCY=1  # Production: 1 issue per run
ENABLE_AUTOMERGE=false  # Production: require manual merge
```

### Step 3: Build Image Locally (First Time)
```bash
# Build OCI image
podman build \
  -f container/Containerfile.sdlc-worker \
  -t localhost/homedir-sdlc:latest \
  .

# Verify image
podman images | grep homedir-sdlc
```

### Step 4: Run Migration Script (Dry Run First)
```bash
# Dry run to preview changes
./container/migrate-to-podman.sh --dry-run
```

**Review dry-run output**:
- ✓ Current service will be stopped
- ✓ State backed up to ~/homedir-sdlc-backup-YYYYMMDD-HHMMSS
- ✓ Volumes created
- ✓ State imported
- ✓ Pod created
- ✓ SystemD units installed

### Step 5: Execute Migration
```bash
# Execute actual migration
./container/migrate-to-podman.sh

# Output:
# - Stops old service (brief downtime: ~30 seconds)
# - Backs up state
# - Creates pod
# - Starts new containers
# - Installs systemd units
```

### Step 6: Verify Migration Success
```bash
# Check pod running
podman pod ps
# Should show: homedir-sdlc-pod, 2 containers, Up

# Check containers
podman ps --pod
# Should show: homedir-app (port 8080), homedir-sdlc-worker

# Follow logs
podman logs -f homedir-sdlc-worker

# Check health
podman healthcheck run homedir-sdlc-worker
# Should show: healthy

# Check heartbeat
podman exec homedir-sdlc-worker cat /var/lib/homedir-sdlc/heartbeat.json | jq
```

### Step 7: Enable Auto-Update
```bash
# Check timer installed
systemctl list-timers | grep homedir-sdlc-pod-autoupdate

# Enable auto-update (every 6 hours)
systemctl enable homedir-sdlc-pod-autoupdate.timer
systemctl start homedir-sdlc-pod-autoupdate.timer

# Verify timer
systemctl status homedir-sdlc-pod-autoupdate.timer
```

### Step 8: Test Dashboard Access
```bash
# Test from VPS
curl -I http://localhost:8080/sdlc/dashboard

# Should return: HTTP/1.1 200 OK
```

From local machine:
```bash
# If VPS has public IP exposed
curl -I http://72.60.141.165:8080/sdlc/dashboard
```

## 🔍 Post-Migration Verification

### Verify State Preserved
```bash
# Compare counts with pre-migration
podman exec homedir-sdlc-worker bash -c '
  echo "Issues: $(find /var/lib/homedir-sdlc/issues -type f | wc -l)"
  echo "PRs: $(find /var/lib/homedir-sdlc/prs -type f | wc -l)"
  echo "Decisions: $(find /var/lib/homedir-sdlc/autonomous-decisions -type f | wc -l)"
'
```

### Verify Worker Processing
```bash
# Check for recent activity
podman logs --tail 100 homedir-sdlc-worker | grep -i "reconcile\|policy\|decision"

# Wait for next scheduled run (timer-based)
# Or trigger manual run
podman exec homedir-sdlc-worker /app/scripts/homedir-sdlc-worker.sh reconcile
```

### Verify GitHub Integration
```bash
# Check GH authentication
podman exec homedir-sdlc-worker gh auth status

# Should show: Logged in to github.com as os-santiago (...)
```

### Verify Policy System
```bash
# Check policies loaded
podman exec homedir-sdlc-worker ls -la /app/config/

# Should show: scc-autonomous-decision-policies.json
```

## 📊 Monitoring Commands

### Pod Management
```bash
# Pod status
podman pod ps

# Container stats
podman pod stats homedir-sdlc-pod

# Stop pod (for maintenance)
podman pod stop homedir-sdlc-pod

# Start pod
podman pod start homedir-sdlc-pod

# Restart pod
podman pod restart homedir-sdlc-pod
```

### Logs
```bash
# All pod logs
podman pod logs -f homedir-sdlc-pod

# Worker only
podman logs -f homedir-sdlc-worker

# Last 100 lines
podman logs --tail 100 homedir-sdlc-worker

# Since timestamp
podman logs --since "1 hour ago" homedir-sdlc-worker
```

### Health Checks
```bash
# Manual health check
podman healthcheck run homedir-sdlc-worker

# Health history
podman inspect homedir-sdlc-worker | jq '.[0].State.Health'
```

### Volume Management
```bash
# List volumes
podman volume ls

# Inspect volume
podman volume inspect homedir-sdlc-state

# Backup volume
podman volume export homedir-sdlc-state -o /root/backups/state-$(date +%Y%m%d).tar

# Restore volume (if needed)
podman volume import homedir-sdlc-state /root/backups/state-YYYYMMDD.tar
```

## 🔄 Auto-Update Flow

Every 6 hours (00:00, 06:00, 12:00, 18:00):
1. Timer triggers `homedir-sdlc-pod-autoupdate.service`
2. Service pulls latest images from ghcr.io
3. Stops current pod
4. Recreates pod with new images
5. Starts new pod

### Manual Update
```bash
# Trigger update immediately
systemctl start homedir-sdlc-pod-autoupdate.service

# View update logs
journalctl -u homedir-sdlc-pod-autoupdate.service -f
```

## 🚨 Rollback Procedure (If Issues)

### Quick Rollback to Systemd-Native
```bash
# Stop pod
podman pod stop homedir-sdlc-pod

# Restore state from backup
BACKUP_DIR=$(ls -dt ~/homedir-sdlc-backup-* | head -1)
sudo cp -r "$BACKUP_DIR/homedir-sdlc/"* /var/lib/homedir-sdlc/

# Restart old service
systemctl enable homedir-sdlc-worker.service
systemctl start homedir-sdlc-worker.service

# Verify
systemctl status homedir-sdlc-worker.service
```

### Export Data from Pod
```bash
# If pod is accessible but needs rollback
podman exec homedir-sdlc-worker bash -c '
  tar czf /tmp/state-export.tar.gz -C /var/lib/homedir-sdlc .
'

podman cp homedir-sdlc-worker:/tmp/state-export.tar.gz ./state-recovery.tar.gz

# Extract to systemd location
sudo tar xzf state-recovery.tar.gz -C /var/lib/homedir-sdlc/
```

## 📈 Success Metrics

After migration, verify:
- ✅ Pod running: `podman pod ps`
- ✅ Both containers healthy
- ✅ State preserved (issue/PR/decision counts match)
- ✅ Worker processing issues (check logs)
- ✅ Dashboard accessible (http://localhost:8080)
- ✅ Auto-update timer active
- ✅ GitHub authentication working
- ✅ Policy system loaded

## 🎯 Expected Downtime

**Total service interruption**: ~30-60 seconds
- Time to stop old service: ~5s
- Time to start pod: ~20-40s
- Time to initialize worker: ~5-15s

**Background sync**: Worker will catch up on missed events automatically via GitHub API polling.

## 📞 Troubleshooting

### Pod won't start
```bash
# Check pod events
podman pod inspect homedir-sdlc-pod | jq

# Check container logs
podman logs homedir-sdlc-worker
podman logs homedir-app

# Remove and recreate
podman pod rm -f homedir-sdlc-pod
./container/pod-create.sh production
```

### Worker not processing
```bash
# Check environment
podman exec homedir-sdlc-worker env | grep -E "GH_TOKEN|REPO"

# Check GitHub auth
podman exec homedir-sdlc-worker gh auth status

# Manual reconcile
podman exec homedir-sdlc-worker /app/scripts/homedir-sdlc-worker.sh reconcile
```

### Dashboard not accessible
```bash
# Check app container
podman logs homedir-app

# Test connectivity from worker
podman exec homedir-sdlc-worker curl -I http://localhost:8080/sdlc/dashboard

# Check port mapping
podman port homedir-app
```

## 📚 Next Steps After Migration

1. Monitor for 24 hours
2. Verify auto-update working (wait for next 6h window)
3. Test manual trigger: `systemctl start homedir-sdlc-pod-autoupdate.service`
4. Document any VPS-specific customizations
5. Set up external monitoring (optional)

---

**Migration Script Location**: `./container/migrate-to-podman.sh`  
**Documentation**: `./container/README.md`  
**PR**: https://github.com/os-santiago/homedir/pull/1240
