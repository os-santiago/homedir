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

homedir_sdlc_runtime_load() {
  local default_env_file="/etc/homedir-sdlc.env"
  if [[ ! -f "${default_env_file}" && -f "${HOME:-/home/homedir-sdlc}/.config/homedir-sdlc/env" ]]; then
    default_env_file="${HOME:-/home/homedir-sdlc}/.config/homedir-sdlc/env"
  fi

  local env_file="${HOMEDIR_SDLC_ENV_FILE:-${default_env_file}}"
  if [[ -f "${env_file}" ]]; then
    # shellcheck disable=SC1090
    source "${env_file}"
  fi

  local expected_runtime_dir="/run/user/$(id -u)"
  if [[ "${XDG_RUNTIME_DIR:-}" != "${expected_runtime_dir}" && -d "${expected_runtime_dir}" ]]; then
    export XDG_RUNTIME_DIR="${expected_runtime_dir}"
  fi
  if [[ -n "${XDG_RUNTIME_DIR:-}" && -z "${DBUS_SESSION_BUS_ADDRESS:-}" && -S "${XDG_RUNTIME_DIR}/bus" ]]; then
    export DBUS_SESSION_BUS_ADDRESS="unix:path=${XDG_RUNTIME_DIR}/bus"
  fi
}
