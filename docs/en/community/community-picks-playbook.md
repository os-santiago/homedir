# Community Picks Playbook

Last update: 2026-02-20

This document defines the official process to generate and publish Community Picks in Homedir.

## 1. Objective

Establish a repeatable curation workflow that is:

- High signal for developers and platform teams.
- Operationally lightweight (file-based ingest + cache).
- Safe and auditable (clear criteria, schema, and moderation path).

## 2. Runtime Architecture

- Source of truth for curated content: filesystem (`*.yml`), not DB.
- Production directory: `/work/data/community/content`
- Runtime alias: `${homedir.data.dir}/community/content`
- Read path in app: `COMMUNITY_CONTENT_DIR` (default `${homedir.data.dir}/community/content`)
- App behavior:
  - Loads files asynchronously.
  - Keeps in-memory cache (TTL 1h).
  - Exposes ranked views (`featured`, `new`).
  - Stores votes in DB (`content_vote`), not filesystem.

## 3. End-to-End Workflow

### Step A. Curate candidates

Run the curator:

```bash
python3 tools/community-curator/curate_from_web.py \
  --output-dir ./tools/community-curator/out/run-$(date +%Y%m%d-%H%M%S) \
  --api-base https://homedir.opensourcesantiago.io
```

### Step B. Review generated pack

Validate:

- `manifest.json` exists.
- Distribution across media types makes sense.
- No duplicated URLs/titles.
- Tags and summaries are coherent.

### Step C. Deploy to VPS

```bash
bash tools/community-curator/deploy.sh <host> <user> ./tools/community-curator/out/<run-dir>
```

### Step D. Validate in production

- API check:
  - `GET /api/community/content?view=new&limit=10`
  - `GET /api/community/content?view=featured&limit=10`
- UI check:
  - `/comunidad?view=new`
  - `/comunidad?view=featured`

### Step E. Persist curation memory

State file:

- `tools/community-curator/state/history.json`

This enables deduplication and historical bias.

## 4. Curation Criteria

A pick should satisfy most of the following:

- Relevance:
  - AI engineering, Platform Engineering, Cloud Native, Security, or Developer Experience.
- Actionability:
  - Adds practical guidance, decision insight, or implementation knowledge.
- Source quality:
  - Prefer official docs, engineering blogs, maintainers, recognized technical media.
- Freshness:
  - Prefer recent content unless older content is still highly relevant.
- Diversity:
  - Keep a healthy mix of video, podcast, and article/blog.
- Safety/compliance:
  - No malicious/phishing links.
  - No low-quality clickbait.
  - Avoid duplicate coverage of the exact same announcement.

## 5. Scoring Model (Curator Script)

`tools/community-curator/curate_from_web.py` computes:

- `base`: topic-term hits.
- `recency_bonus`: up to 1.2 based on publication date.
- `source_bonus`: trusted source map.
- `hn_bonus`: Hacker News points normalization.
- `eval_bonus`: historical tag bias (from votes on existing content).

Formula:

`total = base + recency_bonus + source_bonus + hn_bonus + eval_bonus`

## 6. Official Topic Taxonomy

Market-aligned taxonomy used by Community UI:

- `ai`
- `platform_engineering`
- `cloud_native`
- `security`

Curator generation tags (content metadata) use:

- `ai-engineering`
- `platform-engineering`
- `cloud-native`
- `security`
- `developer-experience`

Compatibility is intentionally kept with legacy tags (`devops`, `opensource`, `platform`) in UI inference.

## 7. File Structure and Schema

Naming:

- `<YYYYMMDD>-<slug>-<id>.yml`

Required fields:

- `id`
- `title`
- `url` (`http/https`)
- `summary`
- `source`
- `created_at` (ISO-8601)

Optional fields:

- `published_at`
- `media_type` (`video_story|podcast|article_blog`)
- `thumbnail_url`
- `tags`
- `author`

Example:

```yaml
id: "515535afcc14"
title: "Kubernetes intro spotlight"
url: "https://www.youtube.com/watch?v=X48VuDVv0do"
summary: "Short video primer for Kubernetes concepts used by platform teams."
source: "youtube.com"
created_at: "2026-02-20T11:41:51Z"
media_type: "video_story"
thumbnail_url: "https://i.ytimg.com/vi/X48VuDVv0do/hqdefault.jpg"
tags:
  - "kubernetes"
  - "platform-engineering"
  - "cloud-native"
```

## 8. Prompt Templates (LLM-assisted Curation)

Prompt templates are versioned in:

- `tools/community-curator/prompts/system-curator.md`
- `tools/community-curator/prompts/user-curation-batch.md`
- `tools/community-curator/prompts/user-normalize-item.md`

Use these prompts when curation is done with an agent before running deployment scripts.

## 9. Operational Guardrails

- Never commit secrets/tokens in repo.
- Keep generated output directories (`tools/community-curator/out/...`) out of commits unless explicitly needed for fixtures.
- Validate API/UI after each deploy.
- If quality drops, rollback by re-deploying last known-good content pack.
