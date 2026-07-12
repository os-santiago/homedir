# SystemD Configuration for HomeDir SDLC Worker

## Overview

Systemd user services for the autonomous SDLC worker that processes GitHub issues using SCC (sc-agent-cli).

## Files

- `homedir-sdlc-worker.service` - Main worker service (oneshot)
- `homedir-sdlc-worker.timer` - Timer that triggers worker every 3 minutes

## Installation

```bash
# 1. Copy service files to user systemd directory
mkdir -p ~/.config/systemd/user
cp homedir-sdlc-worker.service ~/.config/systemd/user/
cp homedir-sdlc-worker.timer ~/.config/systemd/user/

# 2. Create environment file (see Environment Variables section below)
mkdir -p ~/.config/homedir-sdlc
cat > ~/.config/homedir-sdlc/env << 'EOF'
export PATH=/path/to/.local/bin:/usr/local/bin:/usr/bin:/bin
export SCC_BIN=/path/to/scc
export HOMEDIR_SDLC_STATE_DIR=/path/to/state
export HOMEDIR_SDLC_LOGFILE=/path/to/logs/worker.log
export HOMEDIR_SDLC_REPO=owner/repo
# ... add other required variables
EOF

# 3. Enable and start timer
systemctl --user daemon-reload
systemctl --user enable homedir-sdlc-worker.timer
systemctl --user start homedir-sdlc-worker.timer
```

## Environment Variables

Required environment variables (stored in `~/.config/homedir-sdlc/env`):

**Critical Path Variables:**
- `HOMEDIR_SDLC_ENV_FILE` - Path to env file (set via systemd Environment directive)
- `HOMEDIR_SDLC_STATE_DIR` - State directory for worker (e.g., `~/.local/state/homedir-sdlc`)
- `HOMEDIR_SDLC_LOGFILE` - Log file path

**Tool Paths:**
- `SCC_BIN` - Path to sc-agent-cli binary
- `PATH` - Must include directories containing `gh`, `scc`, `node`, etc.

**GitHub Configuration:**
- `HOMEDIR_SDLC_REPO` - GitHub repository in `owner/repo` format
- `GITHUB_TOKEN` - GitHub Personal Access Token (with repo permissions)

**Worker Behavior:**
- `HOMEDIR_SDLC_DRY_RUN` - Set to `true` to run in dry-run mode (no mutations)
- `HOMEDIR_SDLC_DEBUG` - Set to `true` for verbose logging

## Architecture Notes

### Environment Variable Loading Order

**CRITICAL**: The systemd service uses `Environment` directive instead of `EnvironmentFile` to ensure variables are available BEFORE the bash script initializes.

```ini
# ✅ CORRECT (used in current service)
Environment="HOMEDIR_SDLC_ENV_FILE=/home/user/.config/homedir-sdlc/env"

# ❌ WRONG (causes permission errors)
EnvironmentFile=%h/.config/homedir-sdlc/env
```

**Why**: `EnvironmentFile` loads variables into the service environment but AFTER bash reads the script. This causes the script's variable initialization (`VAR=${ENV:-default}`) to use defaults instead of loaded values, leading to permission errors when defaults point to system paths like `/var/lib`.

**Solution**: Pass `HOMEDIR_SDLC_ENV_FILE` via `Environment` directive. The worker script sources this file explicitly at startup (lines 6-10), ensuring all variables are loaded before directory creation (line 57).

### Timer Configuration

```ini
[Timer]
OnBootSec=2min          # First run 2 minutes after boot
OnCalendar=*:0/3        # Run every 3 minutes, on the minute
AccuracySec=30s         # Allow up to 30s scheduling delay
Persistent=true         # Catch up after system sleep
```

The timer uses `OnCalendar=*:0/3` for fixed three-minute clock scheduling. `Type=oneshot` prevents overlapping runs — if the service is still running when the next timer fires, systemd skips that trigger.

### Service Type

```ini
Type=oneshot
```

The service runs once per timer trigger and exits. This is appropriate for:
- Event-driven processing (check for work, process, exit)
- No long-running daemon needed
- State managed via lock files

## Monitoring

### Check service status
```bash
systemctl --user status homedir-sdlc-worker.service
```

### Check timer status
```bash
systemctl --user status homedir-sdlc-worker.timer
systemctl --user list-timers homedir-sdlc-worker.timer
```

### View logs
```bash
journalctl --user -u homedir-sdlc-worker.service -f
```

### Check worker heartbeat
```bash
cat ~/.local/state/homedir-sdlc/heartbeat.json | jq
```

## Troubleshooting

### Service fails with "Permission denied"

**Symptom**: `mkdir: cannot create directory '/var/lib/homedir-sdlc': Permission denied`

**Cause**: Environment variables not loaded, script using system path defaults

**Fix**: Ensure `Environment="HOMEDIR_SDLC_ENV_FILE=..."` is set in service file (not `EnvironmentFile`)

### Service fails with "command not found" (gh/scc)

**Symptom**: `gh: command not found`

**Cause**: `PATH` not set correctly in environment file

**Fix**: Add full PATH to `~/.config/homedir-sdlc/env`:
```bash
export PATH=/home/user/.local/bin:/usr/local/bin:/usr/bin:/bin
```

### Worker stuck (lock file)

**Symptom**: Worker not processing issues, lock file exists

**Fix**:
```bash
# Check lock file age
stat ~/.local/state/homedir-sdlc/worker.lock

# If stale (>10 min), remove
rm ~/.local/state/homedir-sdlc/worker.lock

# Restart service
systemctl --user restart homedir-sdlc-worker.service
```

## Security

**Important**: Do NOT commit sensitive environment files to git.

Files to exclude:
- `~/.config/homedir-sdlc/env` (contains tokens)
- State directory contents (contains issue data)
- Log files (may contain sensitive information)

The systemd service files are safe to commit as they only contain:
- Paths (using `%h` for home directory)
- Service configuration
- No credentials or tokens
