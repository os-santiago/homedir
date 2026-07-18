#!/bin/bash
for i in {1..20}; do
  clear
  echo "Check #$i at $(date '+%H:%M:%S')"
  echo ""
  ./check-issue-1115.sh
  
  # Check if closed
  STATE=$(gh issue view 1115 --repo os-santiago/homedir --json state --jq '.state')
  if [[ "$STATE" == "CLOSED" ]]; then
    echo ""
    echo "🎉 ISSUE CLOSED! Cycle complete."
    break
  fi
  
  if [[ $i -lt 20 ]]; then
    echo ""
    echo "Next check in 2 minutes... (Ctrl+C to stop)"
    sleep 120
  fi
done
