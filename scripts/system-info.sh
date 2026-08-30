#!/usr/bin/env bash
# System information script for E2E testing
# Created for issue #1559

set -euo pipefail

# Print hostname
printf 'Hostname: %s\n' "$(hostname)"

# Print current date
printf 'Date: %s\n' "$(date)"

# Exit successfully
exit 0
