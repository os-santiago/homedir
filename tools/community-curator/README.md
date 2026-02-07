# Community Curator (Stub Workflow)

This folder contains helper scripts for the curated community-content pipeline.

## Goal
Generate one content file per item (YAML) and upload the directory to a VPS path consumed by Homedir.

## Expected Remote Path
- `/var/lib/homedir/community/content`

## Typical Flow
1. Prepare a list of URLs.
2. Generate YAML files with the stub generator:
```bash
python3 tools/community-curator/generate_stub.py \
  --input urls.txt \
  --output ./out
```
3. Deploy to VPS:
```bash
bash tools/community-curator/deploy.sh <host> <user> ./out
```

Optional destination override:
```bash
bash tools/community-curator/deploy.sh <host> <user> ./out /var/lib/homedir/community/content
```

## Notes
- `generate_stub.py` does not do web search. It receives URLs and creates normalized placeholders.
- Files generated follow:
  - `<YYYYMMDD>-<slug>-<id>.yml`
- The app tolerates invalid files by skipping and logging them.

