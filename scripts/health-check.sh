#!/usr/bin/env bash
# Health check script for Homedir deployment
# Exits 0 if healthy, non-zero if unhealthy

set -euo pipefail

# Configuration
NAMESPACE="${NAMESPACE:-homedir}"
SERVICE_NAME="${SERVICE_NAME:-homedir}"
POD_SELECTOR="${POD_SELECTOR:-app=homedir}"
HEALTH_ENDPOINT_LIVE="/q/health/live"
HEALTH_ENDPOINT_READY="/health/ready"
PORT="${PORT:-8080}"
TIMEOUT="${TIMEOUT:-10}"
MAX_RETRIES="${MAX_RETRIES:-3}"
RETRY_DELAY="${RETRY_DELAY:-2}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $(date '+%Y-%m-%d %H:%M:%S') $*"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $(date '+%Y-%m-%d %H:%M:%S') $*"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $(date '+%Y-%m-%d %H:%M:%S') $*"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $(date '+%Y-%m-%d %H:%M:%S') $*"
}

# Check if kubectl is available
check_kubectl() {
    if ! command -v kubectl &> /dev/null; then
        log_error "kubectl is not installed or not in PATH"
        return 2
    fi
    log_info "kubectl found: $(kubectl version --client --short 2>/dev/null || kubectl version --client 2>/dev/null | head -1)"
    return 0
}

# Check if namespace exists
check_namespace() {
    if ! kubectl get namespace "$NAMESPACE" &> /dev/null; then
        log_error "Namespace '$NAMESPACE' does not exist"
        return 2
    fi
    log_info "Namespace '$NAMESPACE' exists"
    return 0
}

# Get service endpoint
get_service_endpoint() {
    local service_ip
    service_ip=$(kubectl get service "$SERVICE_NAME" -n "$NAMESPACE" -o jsonpath='{.spec.clusterIP}' 2>/dev/null)

    if [[ -z "$service_ip" || "$service_ip" == "None" ]]; then
        log_error "Service '$SERVICE_NAME' not found or has no cluster IP in namespace '$NAMESPACE'"
        return 1
    fi

    echo "http://${service_ip}:${PORT}"
    return 0
}

# Check a single health endpoint
check_endpoint() {
    local base_url="$1"
    local endpoint="$2"
    local description="$3"
    local retries=0

    while [[ $retries -lt $MAX_RETRIES ]]; do
        log_info "Checking $description at ${base_url}${endpoint} (attempt $((retries + 1))/$MAX_RETRIES)..."

        if response=$(curl -s -f --max-time "$TIMEOUT" "${base_url}${endpoint}" 2>/dev/null); then
            log_success "$description check passed"
            log_info "Response: $response"
            return 0
        else
            log_warning "$description check failed (attempt $((retries + 1))/$MAX_RETRIES)"
            retries=$((retries + 1))
            if [[ $retries -lt $MAX_RETRIES ]]; then
                sleep "$RETRY_DELAY"
            fi
        fi
    done

    log_error "$description check failed after $MAX_RETRIES attempts"
    return 1
}

# Check pod status
check_pod_status() {
    log_info "Checking pod status in namespace '$NAMESPACE'..."

    local pods
    pods=$(kubectl get pods -n "$NAMESPACE" -l "$POD_SELECTOR" -o jsonpath='{range .items[*]}{.metadata.name} {.status.phase} {range .status.containerStatuses[*]}{.ready}{","}{end}{"\n"}{end}' 2>/dev/null)

    if [[ -z "$pods" ]]; then
        log_error "No pods found with label '$POD_SELECTOR' in namespace '$NAMESPACE'"
        return 1
    fi

    local all_ready=true
    while IFS= read -r line; do
        if [[ -n "$line" ]]; then
            local pod_name phase ready_str
            pod_name=$(echo "$line" | awk '{print $1}')
            phase=$(echo "$line" | awk '{print $2}')
            ready_str=$(echo "$line" | awk '{print $3}')

            # Check all container readiness statuses
            local pod_ready=true
            IFS=',' read -ra ready_array <<< "$ready_str"
            for r in "${ready_array[@]}"; do
                if [[ "$r" == "false" ]]; then
                    pod_ready=false
                    break
                fi
            done

            if [[ "$phase" == "Running" && "$pod_ready" == "true" ]]; then
                log_success "Pod '$pod_name' is Running and Ready"
            else
                log_error "Pod '$pod_name' is not healthy (Phase: $phase, Ready: $ready_str)"
                all_ready=false
            fi
        fi
    done <<< "$pods"

    if [[ "$all_ready" == "true" ]]; then
        return 0
    else
        return 1
    fi
}

# Main health check
main() {
    log_info "Starting Homedir deployment health check"
    log_info "Namespace: $NAMESPACE, Service: $SERVICE_NAME, Port: $PORT"

    # Check prerequisites
    if ! check_kubectl; then
        exit 2
    fi

    if ! check_namespace; then
        exit 2
    fi

    # Check pod status
    if ! check_pod_status; then
        log_error "Pod health check failed"
        exit 1
    fi

    # Get service endpoint
    local base_url
    if ! base_url=$(get_service_endpoint); then
        log_error "Failed to get service endpoint"
        exit 1
    fi

    log_info "Service endpoint: $base_url"

    # Check liveness endpoint
    if ! check_endpoint "$base_url" "$HEALTH_ENDPOINT_LIVE" "Liveness"; then
        log_error "Liveness check failed"
        exit 1
    fi

    # Check readiness endpoint
    if ! check_endpoint "$base_url" "$HEALTH_ENDPOINT_READY" "Readiness"; then
        log_error "Readiness check failed"
        exit 1
    fi

    log_success "All health checks passed! Deployment is healthy."
    exit 0
}

# Handle script arguments
case "${1:-}" in
    --help|-h)
        cat << EOF
Homedir Deployment Health Check Script

Usage: $0 [OPTIONS]

Environment Variables:
    NAMESPACE       Kubernetes namespace (default: homedir)
    SERVICE_NAME    Service name (default: homedir)
    POD_SELECTOR    Pod label selector (default: app=homedir)
    PORT            Service port (default: 8080)
    TIMEOUT         Curl timeout in seconds (default: 10)
    MAX_RETRIES     Maximum retry attempts (default: 3)
    RETRY_DELAY     Delay between retries in seconds (default: 2)

Options:
    --help, -h      Show this help message
    --live-only     Check only liveness endpoint
    --ready-only    Check only readiness endpoint
    --pods-only     Check only pod status

Exit Codes:
    0   All checks passed (healthy)
    1   One or more checks failed (unhealthy)
    2   Prerequisites not met (kubectl, namespace, etc.)

Examples:
    $0                              # Full health check
    NAMESPACE=staging $0            # Check staging namespace
    $0 --live-only                  # Check only liveness
    TIMEOUT=5 MAX_RETRIES=5 $0      # Custom timeout and retries
EOF
        exit 0
        ;;
    --live-only)
        if ! check_kubectl; then
            exit 2
        fi
        if ! check_namespace; then
            exit 2
        fi
        if ! base_url=$(get_service_endpoint); then
            log_error "Failed to get service endpoint"
            exit 1
        fi
        if ! check_endpoint "$base_url" "$HEALTH_ENDPOINT_LIVE" "Liveness"; then
            log_error "Liveness check failed"
            exit 1
        fi
        exit 0
        ;;
    --ready-only)
        if ! check_kubectl; then
            exit 2
        fi
        if ! check_namespace; then
            exit 2
        fi
        if ! base_url=$(get_service_endpoint); then
            log_error "Failed to get service endpoint"
            exit 1
        fi
        if ! check_endpoint "$base_url" "$HEALTH_ENDPOINT_READY" "Readiness"; then
            log_error "Readiness check failed"
            exit 1
        fi
        exit 0
        ;;
    --pods-only)
        if ! check_kubectl; then
            exit 2
        fi
        if ! check_namespace; then
            exit 2
        fi
        if ! check_pod_status; then
            log_error "Pod health check failed"
            exit 1
        fi
        exit 0
        ;;
    *)
        main
        ;;
esac