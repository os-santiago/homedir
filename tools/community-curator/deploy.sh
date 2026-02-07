#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 3 ]]; then
  echo "usage: deploy.sh <host> <user> <local_dir> [remote_dir]" >&2
  exit 1
fi

HOST="$1"
USER="$2"
LOCAL_DIR="$3"
REMOTE_DIR="${4:-/var/lib/homedir/community/content}"

if [[ ! -d "$LOCAL_DIR" ]]; then
  echo "local directory not found: $LOCAL_DIR" >&2
  exit 1
fi

echo "Deploying curated files from '$LOCAL_DIR' to '$USER@$HOST:$REMOTE_DIR'..."

if command -v rsync >/dev/null 2>&1; then
  rsync -avz --delete "$LOCAL_DIR"/ "$USER@$HOST:$REMOTE_DIR"/
else
  ssh "$USER@$HOST" "mkdir -p '$REMOTE_DIR'"
  scp -r "$LOCAL_DIR"/. "$USER@$HOST:$REMOTE_DIR"/
fi

echo "Deploy completed."

