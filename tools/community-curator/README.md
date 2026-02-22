# Community Curator

This folder contains scripts to curate and deploy filesystem-based community content for `/comunidad`.

## Related Docs
- `docs/en/community/community-picks-playbook.md` (official process, criteria, schema, prompts).
- `tools/community-curator/prompts/` (LLM prompt templates for assisted curation).

## Goal
- Generate one YAML file per curated item.
- Keep a local curation history to avoid duplicates in future runs.
- Read existing published items and vote aggregates from Homedir API to bias future selection.
- Deploy the generated files to the persistent VPS folder consumed by Homedir.

## Expected Remote Path
- Preferred (current VPS runtime): `/work/data/community/content`
- Legacy/compat path: `/var/lib/homedir/community/content` (can be symlinked to `/work/data/community/content`)

## Standard Flow (repeatable)
1. Run curator:
```bash
python3 tools/community-curator/curate_from_web.py \
  --output-dir ./tools/community-curator/out/run-$(date +%Y%m%d-%H%M%S) \
  --api-base https://homedir.opensourcesantiago.io \
  --limit 15
```
2. Review generated YAML files + `manifest.json`.
3. Deploy to VPS:
```bash
bash tools/community-curator/deploy.sh <host> <user> ./tools/community-curator/out/<run-dir>
```
4. Verify:
```bash
curl "https://homedir.opensourcesantiago.io/api/community/content?view=new&limit=20&offset=0"
```

## Production Notes (Homedir VPS)
- The app reads `${homedir.data.dir}/community/content`.
- In current deployment, `JAVA_TOOL_OPTIONS` sets `-Dhomedir.data.dir=/work/data`.
- Therefore deploy to `/work/data/community/content` unless your runtime overrides `homedir.data.dir`.

## Incremental Learning
- Local state file: `tools/community-curator/state/history.json`
  - stores previously published URLs and recent records.
  - prevents duplicate URL publication in future runs.
- Remote feedback loop:
  - `curate_from_web.py` reads existing items + `vote_counts` from API.
  - computes tag bias from score (`3*must_see + recommended - 0.5*not_for_me`).
  - applies bias in ranking to improve future curation.
- On first load, no prior evaluation exists (cold start), so only relevance + recency + source quality are used.

## Scripts
- `curate_from_web.py`
  - fetches candidates from curated RSS/Atom sources and Hacker News trending queries.
  - scores by topic relevance (`ai-engineering`, `platform-engineering`, `cloud-native`, `security`, `developer-experience`, `trending-tech`), recency, source trust, and historical tag bias.
  - outputs Homedir-compatible YAML files (`<YYYYMMDD>-<slug>-<id>.yml`) including `media_type`, optional `thumbnail_url`, and `manifest.json`.
- `deploy.sh`
  - syncs generated files to VPS path.
  - default is incremental sync (no delete).
  - pass `--delete` to mirror local directory exactly.
- `generate_stub.py`
  - fallback utility that builds placeholder YAML from explicit URLs.
  - supports `--media auto|video_story|podcast|article_blog`.
- `generate_preview_mix.py`
  - creates a deterministic 10-item preview pack for production validation.
  - distribution: `video_story=3`, `podcast=3`, `article_blog=4`.

## Prompt Templates (LLM-assisted)
- `prompts/system-curator.md`
- `prompts/user-curation-batch.md`
- `prompts/user-normalize-item.md`

## Deploy Command Reference
```bash
# incremental (recommended)
bash tools/community-curator/deploy.sh <host> <user> <local_dir> [remote_dir]

# mirror local directory exactly (destructive)
bash tools/community-curator/deploy.sh --delete <host> <user> <local_dir> [remote_dir]

# generate 10-item preview mix
python3 tools/community-curator/generate_preview_mix.py \
  --output-dir ./tools/community-curator/out/preview-$(date +%Y%m%d-%H%M%S)
```
