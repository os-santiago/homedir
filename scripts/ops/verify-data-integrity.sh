#!/usr/bin/env bash
# Data Integrity Verification Script
# Verifies JSON structure and basic schema of persisted data files
#
# Usage:
#   ./verify-data-integrity.sh [--dry-run] [--data-dir <path>]
#
# Exit codes:
#   0 - All files valid
#   1 - One or more corrupted files
#   2 - One or more schema validation failures

set -euo pipefail

# Default configuration
DATA_DIR="${DATA_DIR:-data}"
DRY_RUN=false

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Parse arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    --data-dir)
      if [[ $# -lt 2 || -z "${2:-}" ]]; then
        echo "Missing value for --data-dir"
        echo "Usage: $0 [--dry-run] [--data-dir <path>]"
        exit 3
      fi
      DATA_DIR="$2"
      shift 2
      ;;
    *)
      echo "Unknown option: $1"
      echo "Usage: $0 [--dry-run] [--data-dir <path>]"
      exit 3
      ;;
  esac
done

# Verify python3 is available
if ! command -v python3 &> /dev/null; then
  echo -e "${RED}ERROR${NC}: python3 not found. Required for JSON validation."
  exit 3
fi

# Verify data directory exists
if [[ ! -d "$DATA_DIR" ]]; then
  echo -e "${RED}ERROR${NC}: Data directory not found: $DATA_DIR"
  exit 3
fi

echo "Data Integrity Verification"
echo "Data directory: $DATA_DIR"
echo "---"

# File schema definitions embedded in Python for simplicity
# Dry run mode
if [[ "$DRY_RUN" == "true" ]]; then
  python3 - "$DATA_DIR" << 'PYEOF'
import sys
from pathlib import Path

data_dir = Path(sys.argv[1])

schemas = [
    "events.json", "speakers.json", "user-profiles.json", "system-errors.json",
    "economy-state.json", "challenge-state.json", "reputation-state.json",
    "reputation-ga-observation-journal.json", "campaign-state.json",
    "campaign-operations-state.json", "cfp-submissions.json", "cfp-config.json",
    "cfp-event-config.json", "volunteer-submissions.json", "volunteer-event-config.json",
    "volunteer-lounge.json", "volunteer-shifts.json", "volunteer-availabilities.json",
    "community/submissions/pending.json", "community/lightning/state.json",
    "event-operations-state.json", "agenda-proposal-config.json"
]

print("Dry run mode - files to verify:")
for schema_file in schemas:
    filepath = data_dir / schema_file
    if filepath.exists():
        print(f"  - {schema_file}")

for schedule_file in data_dir.glob("user-schedule-*.json"):
    print(f"  - {schedule_file.name}")
PYEOF
  exit 0
fi

# Run full validation - Python handles everything and outputs summary
python3 - "$DATA_DIR" << 'PYEOF'
import json
import sys
from pathlib import Path

data_dir = Path(sys.argv[1])

# Schema definitions: (expected_type, required_fields_csv)
schemas = {
    "events.json": ("array", ""),
    "speakers.json": ("array", ""),
    "user-profiles.json": ("array", ""),
    "system-errors.json": ("array", ""),
    "economy-state.json": ("object", "currentBalance,totalEarned"),
    "challenge-state.json": ("object", ""),
    "reputation-state.json": ("object", ""),
    "reputation-ga-observation-journal.json": ("object", ""),
    "campaign-state.json": ("object", ""),
    "campaign-operations-state.json": ("object", ""),
    "cfp-submissions.json": ("object", ""),
    "cfp-config.json": ("object", ""),
    "cfp-event-config.json": ("object", ""),
    "volunteer-submissions.json": ("object", ""),
    "volunteer-event-config.json": ("object", ""),
    "volunteer-lounge.json": ("object", ""),
    "volunteer-shifts.json": ("object", ""),
    "volunteer-availabilities.json": ("object", ""),
    "community/submissions/pending.json": ("array", ""),
    "community/lightning/state.json": ("object", ""),
    "event-operations-state.json": ("object", ""),
    "agenda-proposal-config.json": ("object", ""),
}

# ANSI colors
RED = '\033[0;31m'
GREEN = '\033[0;32m'
YELLOW = '\033[1;33m'
NC = '\033[0m'

files_ok = 0
files_corrupted = 0
files_schema_invalid = 0
files_total = 0

def validate_file(filepath, rel_path, expected_type, required_fields):
    global files_ok, files_corrupted, files_schema_invalid, files_total
    files_total += 1
    
    try:
        with open(filepath, 'r') as f:
            data = json.load(f)
        
        # Check type
        actual_type = 'array' if isinstance(data, list) else 'object' if isinstance(data, dict) else 'unknown'
        
        if expected_type and actual_type != expected_type:
            print(f"{YELLOW}SCHEMA_INVALID{NC}: {rel_path} (expected {expected_type}, got {actual_type})")
            files_schema_invalid += 1
            return
        
        # Check required fields
        if required_fields and isinstance(data, dict):
            missing = [f for f in required_fields.split(',') if f and f not in data]
            if missing:
                print(f"{YELLOW}SCHEMA_INVALID{NC}: {rel_path} (missing fields: {', '.join(missing)})")
                files_schema_invalid += 1
                return
        
        print(f"{GREEN}OK{NC}: {rel_path}")
        files_ok += 1
        
    except (json.JSONDecodeError, ValueError):
        print(f"{RED}CORRUPTED{NC}: {rel_path} (invalid JSON)")
        files_corrupted += 1
    except Exception as e:
        print(f"{RED}CORRUPTED{NC}: {rel_path} ({str(e)})")
        files_corrupted += 1

# Validate known files
for rel_path, (expected_type, required_fields) in sorted(schemas.items()):
    filepath = data_dir / rel_path
    if filepath.exists():
        validate_file(filepath, rel_path, expected_type, required_fields)

# Validate dynamic user schedule files
for schedule_file in sorted(data_dir.glob("user-schedule-*.json")):
    rel_path = schedule_file.name
    validate_file(schedule_file, rel_path, "", "")

# Print summary
print("---")
print("Summary:")
print(f"  Total files checked: {files_total}")
print(f"  {GREEN}OK{NC}: {files_ok}")
print(f"  {RED}CORRUPTED{NC}: {files_corrupted}")
print(f"  {YELLOW}SCHEMA_INVALID{NC}: {files_schema_invalid}")

# Exit code
if files_corrupted > 0:
    sys.exit(1)
elif files_schema_invalid > 0:
    sys.exit(2)
else:
    sys.exit(0)
PYEOF
exit $?
