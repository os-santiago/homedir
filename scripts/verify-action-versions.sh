#!/usr/bin/env bash
set -euo pipefail

declare -A STANDARD_VERSIONS=(
    ["actions/checkout"]="v6"
    ["actions/setup-java"]="v5"
    ["actions/setup-python"]="v6"
    ["actions/upload-artifact"]="v7"
    ["actions/dependency-review-action"]="v4"
    ["github/codeql-action/init"]="v4"
    ["github/codeql-action/analyze"]="v4"
    ["github/codeql-action/autobuild"]="v4"
    ["github/codeql-action/upload-sarif"]="v4"
    ["anchore/sbom-action"]="v0"
    ["anchore/scan-action"]="v6"
    ["mathieudutour/github-tag-action"]="v6.2"
    ["webfactory/ssh-agent"]="v0.9.0"
    ["trufflesecurity/trufflehog"]="main"
)

WORKFLOWS_DIR=".github/workflows"
VIOLATIONS=0
VIOLATIONS_OUTPUT=""

echo "GitHub Actions Version Compliance Check"
echo "========================================"
echo ""

if [[ ! -d "$WORKFLOWS_DIR" ]]; then
    echo "ERROR: Workflows directory not found: $WORKFLOWS_DIR"
    exit 1
fi

shopt -s nullglob
for workflow in "$WORKFLOWS_DIR"/*.yml "$WORKFLOWS_DIR"/*.yaml; do
    workflow_name=$(basename "$workflow")

    while IFS=: read -r line_num line_content; do
        if [[ "$line_content" =~ ^[[:space:]]*# ]]; then
            continue
        fi

        if [[ "$line_content" =~ uses:[[:space:]]*([^@]+)@([^[:space:]]+) ]]; then
            action="${BASH_REMATCH[1]}"
            version="${BASH_REMATCH[2]}"

            if [[ -v "STANDARD_VERSIONS[$action]" ]]; then
                expected="${STANDARD_VERSIONS[$action]}"

                if [[ "$version" != "$expected" ]]; then
                    VIOLATIONS=$((VIOLATIONS + 1))
                    VIOLATIONS_OUTPUT="${VIOLATIONS_OUTPUT}  - ${workflow_name}:${line_num} -> ${action}@${version} (expected: ${expected})"$'\n'
                fi
            else
                echo "WARNING: Unknown action: ${workflow_name}:${line_num} -> ${action}@${version}"
            fi
        fi
    done < <(grep -n "uses:" "$workflow" || true)
done

echo ""
if [[ $VIOLATIONS -eq 0 ]]; then
    echo "SUCCESS: All actions use standard versions"
    exit 0
else
    echo "FAILED: Version mismatches found (${VIOLATIONS} violations):"
    echo "$VIOLATIONS_OUTPUT"
    echo ""
    echo "See policy: cat docs/ci/action-versioning-policy.md"
    exit 1
fi
