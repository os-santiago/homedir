#!/usr/bin/env bash
set -euo pipefail

DELETE_MODE=0
if [[ "${1:-}" == "--delete" ]]; then
  DELETE_MODE=1
  shift
fi

if [[ $# -lt 3 ]]; then
  echo "usage: deploy.sh [--delete] <host> <user> <local_dir> [remote_dir]" >&2
  exit 1
fi

HOST="$1"
USER="$2"
LOCAL_DIR="$3"
REMOTE_DIR="${4:-/work/data/community/content}"

if [[ ! -d "$LOCAL_DIR" ]]; then
  echo "local directory not found: $LOCAL_DIR" >&2
  exit 1
fi

echo "Deploying curated files from '$LOCAL_DIR' to '$USER@$HOST:$REMOTE_DIR'..."

if command -v rsync >/dev/null 2>&1; then
  RSYNC_OPTS=(-avz)
  if [[ "$DELETE_MODE" -eq 1 ]]; then
    RSYNC_OPTS+=(--delete)
  fi
  rsync "${RSYNC_OPTS[@]}" "$LOCAL_DIR"/ "$USER@$HOST:$REMOTE_DIR"/
else
  ssh "$USER@$HOST" "mkdir -p '$REMOTE_DIR'"
  scp -r "$LOCAL_DIR"/. "$USER@$HOST:$REMOTE_DIR"/
fi

echo "Deploy completed."
