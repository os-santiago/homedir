#!/usr/bin/env bash
# ============================================================================
# Policy Matcher - Match issues to autonomous decision policies
# ============================================================================
#
# Analyzes issue content and determines which policy applies
# Returns policy-driven decision if match found
#
# Usage:
#   source platform/scripts/policy-matcher.sh
#   match_policy_for_issue <issue_number> <issue_title> <issue_body>
#
# ============================================================================

set -euo pipefail

# Requires policy-loader.sh
if ! declare -p POLICIES &>/dev/null; then
  log "WARN" "POLICIES associative array not declared. Load policies first."
  return 1 2>/dev/null || exit 1
fi

# ============================================================================
# Match policy for issue
# ============================================================================
# Returns JSON object with matched policy or null
match_policy_for_issue() {
  local issue_number="$1"
  local issue_title="$2"
  local issue_body="${3:-}"

  local combined_text="${issue_title} ${issue_body}"
  combined_text="${combined_text,,}"  # Lowercase for matching

  log "INFO" "Matching policy for issue #${issue_number}: ${issue_title}"

  # Try each policy category in order
  # Compliance/Legal evaluated first so legal triggers are never masked by auto-approvable categories
  local match=""

  # 1. Compliance/Legal policies (evaluated first to prevent auto-approval of sensitive issues)
  match=$(match_compliance_policy "$combined_text") && [[ -n "$match" ]] && {
    echo "$match"
    return 0
  }

  # 2. Performance policies
  match=$(match_performance_policy "$combined_text") && [[ -n "$match" ]] && {
    echo "$match"
    return 0
  }

  # 3. Rate limiting policies
  match=$(match_rate_limiting_policy "$combined_text") && [[ -n "$match" ]] && {
    echo "$match"
    return 0
  }

  # 4. Database optimization policies
  match=$(match_database_policy "$combined_text") && [[ -n "$match" ]] && {
    echo "$match"
    return 0
  }

  # 5. Dependency update policies
  match=$(match_dependency_policy "$combined_text") && [[ -n "$match" ]] && {
    echo "$match"
    return 0
  }

  # 6. Security policies
  match=$(match_security_policy "$combined_text") && [[ -n "$match" ]] && {
    echo "$match"
    return 0
  }

  # 7. Error handling policies
  match=$(match_error_handling_policy "$combined_text") && [[ -n "$match" ]] && {
    echo "$match"
    return 0
  }

  # 8. Testing policies
  match=$(match_testing_policy "$combined_text") && [[ -n "$match" ]] && {
    echo "$match"
    return 0
  }

  # 9. Refactoring policies
  match=$(match_refactoring_policy "$combined_text") && [[ -n "$match" ]] && {
    echo "$match"
    return 0
  }

  # No policy matched
  log "INFO" "No explicit policy matched for issue #${issue_number}"
  echo "null"
  return 1
}

# ============================================================================
# Performance Policy Matcher
# ============================================================================
match_performance_policy() {
  local text="$1"

  # API response time
  if [[ "$text" =~ (slow api|api.*slow|api.*latency|response.*time) ]]; then
    local target=$(get_policy "performance.api_response_time.target_ms")
    local acceptable=$(get_policy "performance.api_response_time.acceptable_ms")

    cat <<EOF
{
  "category": "PERFORMANCE",
  "subcategory": "api_response_time",
  "policy": "performance.api_response_time",
  "decision": "Optimize API to target ${target}ms (acceptable ${acceptable}ms)",
  "rationale": "Issue mentions API performance - applying performance.api_response_time policy",
  "confidence": "HIGH",
  "action": "optimize_api_performance",
  "targets": {
    "target_ms": ${target},
    "acceptable_ms": ${acceptable}
  },
  "strategy": "database_queries,caching,code_refactoring"
}
EOF
    return 0
  fi

  # Frontend bundle size (CSS, JS files)
  if [[ "$text" =~ (large.*css|large.*js|bundle.*size|css.*file.*large|consolidate.*css) ]]; then
    local max_kb=$(get_policy "performance.frontend_bundle_size.max_kb")

    cat <<EOF
{
  "category": "PERFORMANCE",
  "subcategory": "frontend_bundle_size",
  "policy": "performance.frontend_bundle_size",
  "decision": "Consolidate/minify to under ${max_kb}KB",
  "rationale": "Issue mentions large files - applying frontend_bundle_size policy",
  "confidence": "HIGH",
  "action": "consolidate_files",
  "targets": {
    "max_kb": ${max_kb}
  },
  "strategy": "consolidate_files,remove_duplicates,minification"
}
EOF
    return 0
  fi

  # Database query performance
  if [[ "$text" =~ (slow.*query|query.*slow|database.*slow|db.*performance) ]]; then
    local max_ms=$(get_policy "performance.database_query_time.max_ms")

    cat <<EOF
{
  "category": "PERFORMANCE",
  "subcategory": "database_query_time",
  "policy": "performance.database_query_time",
  "decision": "Optimize queries to under ${max_ms}ms",
  "rationale": "Issue mentions database performance - applying database_query_time policy",
  "confidence": "HIGH",
  "action": "optimize_database",
  "targets": {
    "max_ms": ${max_ms}
  },
  "strategy": "add_index,optimize_query,add_caching"
}
EOF
    return 0
  fi

  return 1
}

# ============================================================================
# Rate Limiting Policy Matcher
# ============================================================================
match_rate_limiting_policy() {
  local text="$1"

  if [[ "$text" =~ (rate.*limit|throttle|api.*limit|request.*limit) ]]; then
    local anon=$(get_policy "rate_limiting.default_limits.anonymous")
    local auth=$(get_policy "rate_limiting.default_limits.authenticated")
    local premium=$(get_policy "rate_limiting.default_limits.premium")

    cat <<EOF
{
  "category": "SECURITY",
  "subcategory": "rate_limiting",
  "policy": "rate_limiting.default_limits",
  "decision": "Implement rate limiting: ${anon} anon / ${auth} auth / ${premium} premium (per minute)",
  "rationale": "Issue mentions rate limiting - applying default_limits policy",
  "confidence": "HIGH",
  "action": "implement_rate_limiting",
  "limits": {
    "anonymous": ${anon},
    "authenticated": ${auth},
    "premium": ${premium}
  }
}
EOF
    return 0
  fi

  return 1
}

# ============================================================================
# Database Policy Matcher
# ============================================================================
match_database_policy() {
  local text="$1"

  if [[ "$text" =~ (database.*slow|optimize.*database|add.*index|slow.*query) ]]; then
    cat <<EOF
{
  "category": "PERFORMANCE",
  "subcategory": "database_optimization",
  "policy": "architecture.database_optimization_order",
  "decision": "Apply database optimization strategy: add_index → optimize_query → add_caching",
  "rationale": "Issue mentions database optimization - applying optimization_order policy",
  "confidence": "HIGH",
  "action": "optimize_database",
  "strategy": "add_index,optimize_query,add_caching"
}
EOF
    return 0
  fi

  return 1
}

# ============================================================================
# Dependency Update Policy Matcher
# ============================================================================
match_dependency_policy() {
  local text="$1"

  # Spring Boot updates
  if [[ "$text" =~ (update.*spring.*boot|spring.*boot.*[0-9]+\.[0-9]+|upgrade.*spring) ]]; then
    local max_version=$(get_policy "dependencies.frameworks.spring_boot.max_major_version")

    # Extract version from text if present
    local version_match=""
    if [[ "$text" =~ spring.*boot.*([0-9]+\.[0-9]+) ]]; then
      version_match="${BASH_REMATCH[1]}"
    fi

    cat <<EOF
{
  "category": "DEPENDENCIES",
  "subcategory": "spring_boot_update",
  "policy": "dependencies.frameworks.spring_boot",
  "decision": "Spring Boot update requires approval if major version change",
  "rationale": "Spring Boot updates follow max_major_version (${max_version}) policy",
  "confidence": "MEDIUM",
  "action": "create_upgrade_plan",
  "requires_approval": true,
  "max_major_version": "${max_version}"
}
EOF
    return 0
  fi

  # Generic dependency updates
  if [[ "$text" =~ (update.*dependenc|upgrade.*dependenc|bump.*version) ]]; then
    cat <<EOF
{
  "category": "DEPENDENCIES",
  "subcategory": "dependency_update",
  "policy": "dependencies.auto_update",
  "decision": "Auto-update: patch=yes, minor=yes, major=requires_approval",
  "rationale": "Dependency updates follow auto_update policy",
  "confidence": "HIGH",
  "action": "update_dependencies",
  "requires_approval": true,
  "rules": {
    "patch": true,
    "minor": true,
    "major": false
  }
}
EOF
    return 0
  fi

  return 1
}

# ============================================================================
# Security Policy Matcher
# ============================================================================
match_security_policy() {
  local text="$1"

  # Input validation
  if [[ "$text" =~ (input.*validation|validate.*input|sanitize|xss|sql.*injection) ]]; then
    cat <<EOF
{
  "category": "SECURITY",
  "subcategory": "input_validation",
  "policy": "security.input_validation",
  "decision": "Add input validation with XSS and SQL injection protection",
  "rationale": "Security issue detected - applying input_validation policy",
  "confidence": "HIGH",
  "action": "add_input_validation",
  "protections": ["xss", "sql_injection", "max_length"]
}
EOF
    return 0
  fi

  return 1
}

# ============================================================================
# Error Handling Policy Matcher
# ============================================================================
match_error_handling_policy() {
  local text="$1"

  if [[ "$text" =~ (500.*error|error.*500|api.*error|fix.*error.*handling) ]]; then
    cat <<EOF
{
  "category": "ERROR_HANDLING",
  "subcategory": "error_investigation",
  "policy": "error_handling.when_error_lacks_info",
  "decision": "Attempt auto-investigation: check logs, search codebase, reproduce",
  "rationale": "Error report detected - applying error investigation policy",
  "confidence": "MEDIUM",
  "action": "investigate_error",
  "steps": ["check_recent_logs", "search_codebase_for_error_code", "attempt_reproduction"]
}
EOF
    return 0
  fi

  return 1
}

# ============================================================================
# Testing Policy Matcher
# ============================================================================
match_testing_policy() {
  local text="$1"

  if [[ "$text" =~ (add.*test|test.*coverage|unit.*test|integration.*test) ]]; then
    local min_coverage=$(get_policy "testing.coverage.minimum_percent")

    cat <<EOF
{
  "category": "TESTING",
  "subcategory": "test_coverage",
  "policy": "testing.coverage",
  "decision": "Add tests to achieve ${min_coverage}% coverage",
  "rationale": "Testing issue detected - applying coverage policy",
  "confidence": "HIGH",
  "action": "add_tests",
  "targets": {
    "minimum_percent": ${min_coverage}
  }
}
EOF
    return 0
  fi

  return 1
}

# ============================================================================
# Refactoring Policy Matcher
# ============================================================================
match_refactoring_policy() {
  local text="$1"

  if [[ "$text" =~ (refactor|clean.*code|reduce.*complexity|extract.*method|simplify) ]]; then
    cat <<EOF
{
  "category": "REFACTORING",
  "subcategory": "code_quality",
  "policy": "refactoring.strategies",
  "decision": "Refactor using: extract_method, remove_duplication, simplify_conditionals",
  "rationale": "Refactoring issue detected - applying refactoring strategies policy",
  "confidence": "MEDIUM",
  "action": "refactor_code",
  "strategies": ["extract_method", "remove_duplication", "simplify_conditionals"]
}
EOF
    return 0
  fi

  return 1
}

# ============================================================================
# Compliance/Legal Policy Matcher
# ============================================================================
match_compliance_policy() {
  local text="$1"

  # Data collection requiring legal review
  if [[ "$text" =~ (store.*ip.*address|collect.*ip|geolocation|biometric|personal.*data|ccpa|gdpr|hipaa|sox|pci.*dss) ]]; then
    cat <<EOF
{
  "category": "COMPLIANCE",
  "subcategory": "data_collection",
  "policy": "compliance.data_collection.requires_legal_review",
  "decision": "NEEDS-HUMAN: Requires legal review before implementation",
  "rationale": "Data collection detected - requires_legal_review policy applies",
  "confidence": "HIGH",
  "action": "escalate_legal_review",
  "requires_legal": true,
  "reason": "GDPR/CCPA compliance review required for PII collection"
}
EOF
    return 0
  fi

  return 1
}

# ============================================================================
# Get policy-driven autonomous decision
# ============================================================================
# Returns complete decision object for logging
get_policy_decision() {
  local issue_number="$1"
  local issue_title="$2"
  local issue_body="${3:-}"

  local policy_match
  policy_match=$(match_policy_for_issue "$issue_number" "$issue_title" "$issue_body")

  if [[ "$policy_match" == "null" ]] || [[ -z "$policy_match" ]]; then
    echo "null"
    return 1
  fi

  # Add issue metadata to policy match
  local decision_with_metadata
  decision_with_metadata=$(echo "$policy_match" | jq \
    --arg issue "$issue_number" \
    --arg title "$issue_title" \
    '. + {issueNumber: $issue, issueTitle: $title, policyDriven: true}')

  echo "$decision_with_metadata"
  return 0
}

# ============================================================================
# Check if decision requires human approval
# ============================================================================
requires_human_approval() {
  local policy_decision="$1"

  # Extract requires_approval or requires_legal flags
  local requires_approval
  requires_approval=$(echo "$policy_decision" | jq -r '.requires_approval // false')

  local requires_legal
  requires_legal=$(echo "$policy_decision" | jq -r '.requires_legal // false')

  [[ "$requires_approval" == "true" ]] || [[ "$requires_legal" == "true" ]]
}

# ============================================================================
# Format policy decision for logging
# ============================================================================
format_policy_decision_log() {
  local policy_decision="$1"
  local pr_number="${2:-}"

  # Extract fields
  local category
  category=$(echo "$policy_decision" | jq -r '.category')

  local decision
  decision=$(echo "$policy_decision" | jq -r '.decision')

  local rationale
  rationale=$(echo "$policy_decision" | jq -r '.rationale')

  local policy
  policy=$(echo "$policy_decision" | jq -r '.policy')

  local confidence
  confidence=$(echo "$policy_decision" | jq -r '.confidence')

  # Format for log_autonomous_decision
  cat <<EOF
{
  "category": "${category}",
  "decision": "${decision}",
  "rationale": "${rationale}",
  "policyReference": "${policy}",
  "confidence": "${confidence}",
  "policyDriven": true
}
EOF
}
