# Operations Scripts

Scripts for operational tasks like data integrity verification and monitoring.

## verify-data-integrity.sh

Verifies JSON structure and basic schema of persisted data files.

### Usage

```bash
./verify-data-integrity.sh [--dry-run] [--data-dir <path>]
```

### Options

- `--dry-run`: List files to verify without actually validating
- `--data-dir <path>`: Data directory to verify (default: `data`)

### Exit Codes

- `0`: All files valid
- `1`: One or more corrupted files (invalid JSON)
- `2`: One or more schema validation failures
- `3`: Usage error or missing dependencies

### Examples

```bash
# Verify default data directory
./verify-data-integrity.sh

# Verify custom directory
./verify-data-integrity.sh --data-dir /path/to/data

# Dry run to see what would be verified
./verify-data-integrity.sh --dry-run
```

### Validated Files

The script verifies:
- All known persisted JSON files (events.json, user-profiles.json, etc.)
- Dynamic files (user-schedule-*.json)
- JSON syntax validity
- Expected root type (array vs object)
- Required fields (where applicable)

### Requirements

- `bash`
- `python3`
