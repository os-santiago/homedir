#!/usr/bin/env bash
set -euo pipefail

CONFIG_FILE="${CONFIG_FILE:-/etc/default/homedir-backup}"
if [[ -f "$CONFIG_FILE" ]]; then
  # shellcheck source=/etc/default/homedir-backup
  . "$CONFIG_FILE"
fi

SOURCE_DIR="${SOURCE_DIR:-/work/data}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/homedir}"
MAX_FILES="${MAX_FILES:-120}"
LOCK_FILE="${LOCK_FILE:-/var/lock/homedir-persistent-backup.lock}"
TIMESTAMP="$(date -u +%Y%m%d-%H%M%S)"
HOSTNAME_SHORT="$(hostname -s)"
BASENAME="homedir-data-${HOSTNAME_SHORT}-${TIMESTAMP}.tar.gz"
TARGET_FILE="${BACKUP_DIR}/${BASENAME}"
TMP_FILE="${TARGET_FILE}.tmp"

mkdir -p "$BACKUP_DIR"
mkdir -p "$(dirname "$LOCK_FILE")"

(
  flock -n 9 || {
    echo "[$(date -u --iso-8601=seconds)] backup skipped: another run is active" >&2
    exit 0
  }

  if [[ ! -d "$SOURCE_DIR" ]]; then
    echo "[$(date -u --iso-8601=seconds)] source dir not found: $SOURCE_DIR" >&2
    exit 1
  fi

  if command -v ionice >/dev/null 2>&1; then
    ionice -c2 -n7 -p $$ >/dev/null 2>&1 || true
  fi
  renice 19 $$ >/dev/null 2>&1 || true

  tar --warning=no-file-changed --ignore-failed-read --xattrs --acls --selinux -C "$SOURCE_DIR" -czf "$TMP_FILE" .
  mv "$TMP_FILE" "$TARGET_FILE"

  sha256sum "$TARGET_FILE" > "${TARGET_FILE}.sha256"
  printf '%s %s\n' "$(date -u --iso-8601=seconds)" "$TARGET_FILE" >> "${BACKUP_DIR}/backup.log"

  mapfile -t backups < <(ls -1t "${BACKUP_DIR}"/homedir-data-*.tar.gz 2>/dev/null || true)
  if (( ${#backups[@]} > MAX_FILES )); then
    for old in "${backups[@]:MAX_FILES}"; do
      rm -f "$old" "${old}.sha256"
    done
  fi
) 9>"$LOCK_FILE"