#!/usr/bin/env bash
set -euo pipefail

sarif_file=${1:-reports/arch.sarif}
changed_file=${2:-reports/changed-files.txt}

if [[ ! -f "$sarif_file" ]]; then
  echo "SARIF report not found: $sarif_file" >&2
  exit 1
fi

if [[ ! -f "$changed_file" ]]; then
  echo "Changed files list not found: $changed_file" >&2
  exit 1
fi

mapfile -t changed < "$changed_file"

jq_query='.runs[]?.results[]? | {ruleId, message: .message.text, file: .locations[0].physicalLocation.artifactLocation.uri}'
results=$(jq -c "$jq_query" "$sarif_file" 2>/dev/null || echo '')

new_count=0
existing_count=0
new_results=()

if [[ -n "$results" ]]; then
  while IFS= read -r res; do
    file=$(jq -r '.file' <<<"$res")
    if printf '%s\n' "${changed[@]}" | grep -Fxq "$file"; then
      new_results+=("$res")
      ((new_count++))
    else
      ((existing_count++))
    fi
  done <<<"$results"
fi

{
  echo "### Architecture Rules Summary"
  echo "- New violations (diff): $new_count"
  echo "- Existing (out-of-diff): $existing_count"

  if (( new_count > 0 )); then
    echo ""
    echo "#### Top new violations"
    for res in "${new_results[@]:0:3}"; do
      rule=$(jq -r '.ruleId' <<<"$res")
      msg=$(jq -r '.message' <<<"$res")
      file=$(jq -r '.file' <<<"$res")
      case "$rule" in
        cycle*) suggestion="Evita ciclo entre paquetes. Extrae interfaz en domain y dependa app de ella." ;;
        api-to-infra*) suggestion="api no debe llamar infra. Usa app/service como intermediario." ;;
        domain-to-infra*) suggestion="domain no debe conocer infraestructura. Usa un puerto en app/service." ;;
        static-mutable*) suggestion="Evita estado global mutable. Inyecta dependencias o usa config." ;;
        *) suggestion="" ;;
      esac
      echo "- $file: $msg${suggestion:+ — $suggestion}"
    done
  fi
} >> "$GITHUB_STEP_SUMMARY"

if (( new_count > 0 )); then
  exit 1
fi
