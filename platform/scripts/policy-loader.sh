#!/usr/bin/env bash
# ============================================================================
# Policy Loader - Load and parse autonomous decision policies
# ============================================================================
#
# Loads autonomous-decision-policy.yaml and makes policies available to worker
#
# Usage:
#   source platform/scripts/policy-loader.sh
#   load_policies
#   get_policy "performance.api_response_time.target_ms"  # Returns: 200
#
# ============================================================================

set -euo pipefail

# Policy file location
readonly POLICY_FILE="${PLATFORM_DIR:-platform}/config/autonomous-decision-policy.yaml"

# Global associative array for policies (bash 4+)
declare -gA POLICIES

# ============================================================================
# Load policies from YAML file
# ============================================================================
load_policies() {
  local policy_file="$POLICY_FILE"

  if [[ ! -f "$policy_file" ]]; then
    log "WARN" "Policy file not found: $policy_file"
    log "WARN" "Using autonomous guidelines only (no policies)"
    return 1
  fi

  log "INFO" "Loading policies from: $policy_file"

  if ! command -v yq &>/dev/null; then
    log "WARN" "yq not found, falling back to simple YAML parsing"
    _load_policies_fallback "$policy_file"
    return $?
  fi

  # Parse YAML to flat dot-notation keys using yq
  while IFS="=" read -r full_key value; do
    [[ -z "$full_key" ]] && continue
    POLICIES["$full_key"]="$value"
    log "DEBUG" "Loaded policy: $full_key = $value"
  done < <(yq -r '.. | to_entries | .[] | [.key] + if .value | type == "!!seq" then .value else [.value] end | join("=")' "$policy_file" 2>/dev/null || _load_policies_fallback "$policy_file")

  log "INFO" "Loaded ${#POLICIES[@]} policy values"
  return 0
}

# ============================================================================
# Get policy value by key path
# ============================================================================
# Usage: get_policy "performance.api_response_time.target_ms"
# Returns: 200 (or empty string if not found)
get_policy() {
  local key="$1"
  echo "${POLICIES[$key]:-}"
}

# ============================================================================
# Check if policy exists
# ============================================================================
# Usage: has_policy "performance.api_response_time"
# Returns: 0 if exists, 1 if not
has_policy() {
  local key="$1"
  [[ -n "${POLICIES[$key]:-}" ]]
}

# ============================================================================
# Get all policy keys matching prefix
# ============================================================================
# Usage: get_policy_keys "performance.api_response_time"
# Returns: Array of matching keys
get_policy_keys() {
  local prefix="$1"
  local keys=()

  for key in "${!POLICIES[@]}"; do
    if [[ "$key" == "$prefix"* ]]; then
      keys+=("$key")
    fi
  done

  printf '%s\n' "${keys[@]}"
}

# ============================================================================
# Export policies as environment variables
# ============================================================================
# Converts: performance.api_response_time.target_ms = 200
# To:       POLICY_PERFORMANCE_API_RESPONSE_TIME_TARGET_MS=200
export_policies_as_env() {
  for key in "${!POLICIES[@]}"; do
    local env_var="POLICY_${key^^}"  # Uppercase
    env_var="${env_var//./_}"        # Replace dots with underscores
    export "$env_var"="${POLICIES[$key]}"
  done

  log "INFO" "Exported ${#POLICIES[@]} policies as environment variables"
}

# ============================================================================
# Get policy section as JSON
# ============================================================================
# Usage: get_policy_section_json "performance"
# Returns: JSON object with all performance.* policies
get_policy_section_json() {
  local section="$1"
  local json="{"
  local first=true

  for key in "${!POLICIES[@]}"; do
    if [[ "$key" == "$section"* ]]; then
      local short_key="${key#$section.}"  # Remove section prefix
      local value="${POLICIES[$key]}"

      if [[ "$first" == true ]]; then
        first=false
      else
        json+=","
      fi

      json+="\"$short_key\":\"$value\""
    fi
  done

  json+="}"
  echo "$json"
}

# ============================================================================
# Validate policy file syntax
# ============================================================================
validate_policy_file() {
  local policy_file="$POLICY_FILE"

  if [[ ! -f "$policy_file" ]]; then
    log "ERROR" "Policy file not found: $policy_file"
    return 1
  fi

  # Check for required sections
  local required_sections=(
    "performance"
    "rate_limiting"
    "architecture"
    "dependencies"
    "compliance"
    "error_handling"
  )

  for section in "${required_sections[@]}"; do
    if ! grep -q "^${section}:" "$policy_file"; then
      log "ERROR" "Missing required section: $section"
      return 1
    fi
  done

  log "INFO" "Policy file validation passed"
  return 0
}

# ============================================================================
# Helper: Log function (if not already defined)
# ============================================================================
if ! declare -f log >/dev/null; then
  log() {
    local level="$1"
    shift
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] [$level] $*" >&2
  }
fi

# ============================================================================
# Auto-load on source (optional)
# ============================================================================
# Uncomment to auto-load when script is sourced
# load_policies || true
