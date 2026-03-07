#!/usr/bin/env bash
set -euo pipefail

homedir_env_load() {
  local env_file="$1"
  if [[ -f "${env_file}" ]]; then
    set -a
    # shellcheck source=/dev/null
    source "${env_file}"
    set +a
  fi
  homedir_env_resolve_file_refs
}

homedir_env_resolve_file_refs() {
  local file_var key file_path value
  while IFS='=' read -r file_var _; do
    [[ "${file_var}" =~ ^[A-Za-z_][A-Za-z0-9_]*_FILE$ ]] || continue
    key="${file_var%_FILE}"
    file_path="${!file_var:-}"
    [[ -n "${file_path}" ]] || continue
    [[ -f "${file_path}" ]] || {
      echo "ERROR: ${file_var} points to missing file: ${file_path}" >&2
      return 1
    }
    value="$(<"${file_path}")"
    value="${value%$'\r'}"
    value="${value%$'\n'}"
    export "${key}=${value}"
  done < <(env)
}

