#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="${ENV_FILE:-/etc/homedir.env}"
if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck source=/dev/null
  source "$ENV_FILE"
  set +a
fi

DISCORD_ALERTS_ENABLED="${DISCORD_ALERTS_ENABLED:-true}"
WEBHOOK_URL="${ALERTS_DISCORD_WEBHOOK_URL:-${DISCORD_ALERT_WEBHOOK_URL:-}}"
ALERTS_LOGFILE="${DISCORD_ALERTS_LOGFILE:-/var/log/homedir-alerts.log}"
ALERTS_APP_NAME="${DISCORD_ALERTS_APP_NAME:-HomeDir Deploy}"
ALERTS_USERNAME="${DISCORD_ALERTS_USERNAME:-HomeDir Alerts}"
ALERTS_TIMEOUT_SECONDS="${DISCORD_ALERTS_TIMEOUT_SECONDS:-10}"

if [[ "${DISCORD_ALERTS_ENABLED,,}" != "true" ]]; then
  exit 0
fi

if [[ -z "${WEBHOOK_URL:-}" ]]; then
  exit 0
fi

SEVERITY="${1:-INFO}"
TITLE="${2:-Notification}"
MESSAGE="${3:-}"
DETAILS="${4:-}"

severity_upper="$(echo "$SEVERITY" | tr '[:lower:]' '[:upper:]')"
icon="ℹ️"
color="3447003"

case "$severity_upper" in
  WARN)
    icon="⚠️"
    color="16776960"
    ;;
  FAIL)
    icon="❌"
    color="15158332"
    ;;
  RECOVERY)
    icon="✅"
    color="3066993"
    ;;
  INFO)
    ;;
  *)
    severity_upper="INFO"
    ;;
esac

log() {
  echo "$(date -Iseconds): $*" >> "$ALERTS_LOGFILE"
}

python3 - "$WEBHOOK_URL" "$ALERTS_USERNAME" "$ALERTS_APP_NAME" "$severity_upper" "$icon" "$color" "$TITLE" "$MESSAGE" "$DETAILS" "$ALERTS_TIMEOUT_SECONDS" <<'PY'
import json
import sys
import urllib.request
from datetime import datetime, timezone

(
    webhook_url,
    username,
    app_name,
    severity,
    icon,
    color_raw,
    title,
    message,
    details,
    timeout_raw,
) = sys.argv[1:11]

description = message or title
embed = {
    "title": f"{icon} {title}",
    "description": description[:4000],
    "color": int(color_raw),
    "fields": [
        {"name": "Severity", "value": severity, "inline": True},
    ],
    "footer": {"text": app_name},
    "timestamp": datetime.now(timezone.utc).isoformat(),
}

if details:
    embed["fields"].append({"name": "Details", "value": details[:1024], "inline": False})

payload = {
    "username": username,
    "embeds": [embed],
}

request = urllib.request.Request(
    webhook_url,
    data=json.dumps(payload).encode("utf-8"),
    headers={"Content-Type": "application/json", "User-Agent": "homedir-discord-alert/1.0"},
    method="POST",
)

timeout = max(3, min(int(timeout_raw), 30))
with urllib.request.urlopen(request, timeout=timeout) as response:
    if response.status < 200 or response.status >= 300:
        raise RuntimeError(f"discord webhook unexpected status: {response.status}")
PY

log "discord alert sent severity=${severity_upper} title=${TITLE}"
