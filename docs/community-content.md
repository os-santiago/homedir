# Community Content (Curated + Voting)

This module powers `/comunidad` with curated content loaded from files and community voting.

## Overview
- Content source: filesystem (not DB), generated externally by a curator CLI.
- Votes: persisted in an embedded DB table (`content_vote`) with upsert semantics.
- Views:
  - `featured`: ranked by votes + optional time decay.
  - `new`: sorted by `created_at` descending.
- Community Board:
  - `/comunidad/board` summary (HomeDir users, GitHub users, Discord users).
  - `/comunidad/board/{group}` detail list with search and profile-link copy.
  - `/community/member/{group}/{id}` stable shareable member profile page.

## Content Directory
- Production target: `${homedir.data.dir}/community/content`
- Current VPS runtime target: `/work/data/community/content`
- Legacy path: `/var/lib/homedir/community/content` (supported if linked or configured)
- Config env var: `COMMUNITY_CONTENT_DIR`
- App default (when env not set): `${homedir.data.dir}/community/content`
- Optional Community Board Discord source:
  - `community.board.discord.file` (YAML/JSON, default `${homedir.data.dir}/community/board/discord-users.yml`)

## File Schema
- One file per item (`.yml` or `.yaml`).
- Suggested naming: `<YYYYMMDD>-<slug>-<id>.yml`

Required fields:
- `id` (string unique)
- `title`
- `url` (`http/https`)
- `summary`
- `source`
- `created_at` (ISO-8601)

Optional fields:
- `published_at` (ISO-8601)
- `tags` (array)
- `author`

Invalid/incomplete files are skipped and logged.

## Cache
- In-memory cache with TTL (`community.content.cache-ttl`, default `PT1H`).
- Scheduled async refresh (`community.content.refresh-interval`, default `15m`).
- On TTL expiry, requests return current cache and trigger async refresh.
- Metrics exposed in API list responses:
  - `cache_size`
  - `last_load_time`
  - `load_duration_ms`
  - `files_loaded`
  - `files_invalid`
- Community Board Discord users cache:
  - `community.board.cache-ttl` (default `PT1H`)
  - If refresh fails, last in-memory snapshot is kept.

## Voting
- Endpoint: `PUT /api/community/content/{id}/vote`
- Body:
```json
{ "vote": "recommended|must_see|not_for_me" }
```
- Auth required.
- Idempotent upsert by `(user_id, content_id)` (replaces previous vote).
- Daily guardrail: `community.votes.daily-limit` (default `100`).

## Ranking
Base score:
- `score_base = 3 * must_see + 1 * recommended - 0.5 * not_for_me`

Optional recency decay:
- `score = score_base * 0.85^(days_since_created)`
- Controlled by `community.content.ranking.decay-enabled` (default `true`)

Featured window:
- `community.content.featured.window-days` (default `7`)

## API
- `GET /api/community/content?view=new|featured&limit=&offset=`
- `GET /api/community/content/{id}`
- `PUT /api/community/content/{id}/vote`

## Community Board Data Sources
- `HomeDir users`: internal `UserProfile` store (Google-auth users with local profile).
- `GitHub users`: union of linked GitHub accounts in `UserProfile` + synced community members.
- `Discord users`: optional file (`members` array) loaded from configured path and cached.

Each item includes counts and current user vote:
- `vote_counts.recommended`
- `vote_counts.must_see`
- `vote_counts.not_for_me`
- `my_vote`
