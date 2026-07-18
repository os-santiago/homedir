# HomeDir AI SDLC - Podman Container Deployment

Complete containerized deployment of the HomeDir AI SDLC system using Podman pods.

## 🏗️ Architecture

```
┌─────────────────────────────────────────────┐
│         Podman Pod: homedir-sdlc-pod        │
├─────────────────────────────────────────────┤
│  Shared network namespace (localhost)      │
│                                             │
│  ┌────────────────────────────────────┐   │
│  │  homedir-app                       │   │
│  │  Port: 8080 (exposed)              │   │
│  │  Reads: autonomous decisions       │   │
│  └────────────────────────────────────┘   │
│                                             │
│  ┌────────────────────────────────────┐   │
│  │  homedir-sdlc-worker               │   │
│  │  Runs: AI SDLC autonomous flow     │   │
│  │  Writes: decisions, PRs, state     │   │
│  └────────────────────────────────────┘   │
└─────────────────────────────────────────────┘
```

## 📦 Components

- **Containerfile.sdlc-worker**: OCI image definition
- **pod-create.sh**: Script to create and manage the pod
- **systemd/**: SystemD service units for auto-start
- **config/**: Environment-specific configurations

## 🚀 Quick Start

### 1. Build Image Locally

```bash
# Build SDLC worker image
podman build \
  -f container/Containerfile.sdlc-worker \
  -t homedir-sdlc:latest \
  .

# Verify image
podman images | grep homedir-sdlc
```

### 2. Configure Environment

```bash
# Copy and edit production config
cp container/config/production.env container/config/production.local.env

# Set your GitHub token
nano container/config/production.local.env
# GH_TOKEN=ghp_your_token_here
```

### 3. Create Pod

```bash
# Create production pod
chmod +x container/pod-create.sh
./container/pod-create.sh production

# Verify pod is running
podman pod ps
podman ps --pod
```

### 4. Monitor

```bash
# Follow all pod logs
podman pod logs -f homedir-sdlc-pod

# Follow only worker logs
podman logs -f homedir-sdlc-worker

# Check worker health
podman healthcheck run homedir-sdlc-worker

# Shell into worker
podman exec -it homedir-sdlc-worker bash
```

## 🔧 SystemD Integration

### User-Level (Recommended for development)

```bash
# Install service
mkdir -p ~/.config/systemd/user/
cp container/systemd/*.service ~/.config/systemd/user/
cp container/systemd/*.timer ~/.config/systemd/user/

# Enable and start
systemctl --user daemon-reload
systemctl --user enable homedir-sdlc-pod.service
systemctl --user start homedir-sdlc-pod.service

# Enable auto-update
systemctl --user enable homedir-sdlc-pod-autoupdate.timer
systemctl --user start homedir-sdlc-pod-autoupdate.timer

# Check status
systemctl --user status homedir-sdlc-pod.service
```

### System-Level (Recommended for production)

```bash
# Install service (requires root)
sudo cp container/systemd/*.service /etc/systemd/system/
sudo cp container/systemd/*.timer /etc/systemd/system/

# Enable and start
sudo systemctl daemon-reload
sudo systemctl enable homedir-sdlc-pod.service
sudo systemctl start homedir-sdlc-pod.service

# Enable auto-update (pulls latest images every 6 hours)
sudo systemctl enable homedir-sdlc-pod-autoupdate.timer
sudo systemctl start homedir-sdlc-pod-autoupdate.timer

# Check status
sudo systemctl status homedir-sdlc-pod.service
sudo journalctl -u homedir-sdlc-pod.service -f
```

## 🔄 CI/CD Integration

### Automatic Image Builds

Every push to `main` that touches `platform/**` triggers:

1. Build OCI image with Podman
2. Push to `ghcr.io/os-santiago/homedir-sdlc:latest`
3. Tag with commit SHA

### VPS Auto-Update

On the VPS, the systemd timer checks for new images every 6 hours:

```bash
# Force immediate update
sudo systemctl start homedir-sdlc-pod-autoupdate.service

# View update logs
sudo journalctl -u homedir-sdlc-pod-autoupdate.service
```

## 📊 Volumes

Persistent data is stored in named Podman volumes:

| Volume | Purpose | Mounted In |
|--------|---------|------------|
| `homedir-sdlc-state` | SDLC state (issues, PRs, decisions) | Both containers |
| `homedir-sdlc-worktrees` | Git worktrees | Worker only |
| `homedir-sdlc-logs` | Worker logs | Worker only |

### Backup Volumes

```bash
# Backup state volume
podman volume export homedir-sdlc-state -o homedir-sdlc-state-backup.tar

# Restore state volume
podman volume import homedir-sdlc-state homedir-sdlc-state-backup.tar
```

## 🛠️ Troubleshooting

### Pod won't start

```bash
# Check pod events
podman pod inspect homedir-sdlc-pod

# Check container logs
podman logs homedir-sdlc-worker
podman logs homedir-app

# Recreate pod
./container/pod-create.sh production
```

### Worker not processing issues

```bash
# Check worker logs
podman logs -f homedir-sdlc-worker | grep -i "error\|warn"

# Check heartbeat
podman exec homedir-sdlc-worker cat /var/lib/homedir-sdlc/heartbeat.json | jq

# Check GitHub authentication
podman exec homedir-sdlc-worker gh auth status

# Check policies loaded
podman exec homedir-sdlc-worker ls -la /app/config/
```

### Dashboard not accessible

```bash
# Check app container
podman logs homedir-app

# Test from inside pod (containers share network)
podman exec homedir-sdlc-worker curl -I http://localhost:8080/sdlc/dashboard

# Test from host
curl -I http://localhost:8080/sdlc/dashboard
```

## 🔐 Security

### Rootless Containers

The worker runs as user `homedir-sdlc` (UID 1000), not root:

```bash
# Verify
podman exec homedir-sdlc-worker whoami
# Output: homedir-sdlc
```

### Secrets Management

**DO NOT** commit secrets to git. Use:

1. **Environment files** (`.local.env` in `.gitignore`)
2. **Podman secrets** (recommended):

```bash
# Create secret
echo "ghp_your_token" | podman secret create gh_token -

# Use in pod-create.sh (NEVER pass tokens via --env or command-line flags)
podman run -d \
  --secret gh_token,type=env,target=GH_TOKEN \
  ...
```

3. **Systemd credentials** (for system-level deployments)
   - See `container/config/production.env` for all secrets — copy to `production.local.env`, keep it out of git
   - Use `LoadCredential=` in systemd service files for production deployments

## 📈 Monitoring

### Metrics

```bash
# Pod stats
podman pod stats homedir-sdlc-pod

# Container stats
podman stats homedir-sdlc-worker homedir-app

# Disk usage
podman system df
```

### Health Checks

```bash
# Manual health check
podman healthcheck run homedir-sdlc-worker

# View health history
podman inspect homedir-sdlc-worker | jq '.[0].State.Health'
```

### Logs

```bash
# Export logs
podman logs homedir-sdlc-worker > worker-logs-$(date +%Y%m%d).log

# Structured logs with journald
journalctl -u homedir-sdlc-pod.service --since "1 hour ago" -o json
```

## 🧪 Development

### Local Testing

```bash
# Build and run locally
podman build -f container/Containerfile.sdlc-worker -t homedir-sdlc:dev .
./container/pod-create.sh development

# Test with specific commit
podman build -f container/Containerfile.sdlc-worker -t homedir-sdlc:test-feature .
# Edit development.env to use homedir-sdlc:test-feature
./container/pod-create.sh development
```

### Debugging

```bash
# Shell into worker
podman exec -it homedir-sdlc-worker bash

# Inside container:
cd /app
source scripts/policy-loader.sh
load_policies
echo "${#POLICIES[@]} policies loaded"

# Run worker manually
./scripts/homedir-sdlc-worker.sh reconcile
```

## 📚 References

- [Podman Documentation](https://docs.podman.io/)
- [Podman Pods](https://docs.podman.io/en/latest/markdown/podman-pod.1.html)
- [SystemD Integration](https://docs.podman.io/en/latest/markdown/podman-generate-systemd.1.html)
- [OCI Image Spec](https://github.com/opencontainers/image-spec)
