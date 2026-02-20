# Community Content (Curated + Voting)

This module powers `/comunidad` with curated content loaded from files and community voting.

Operational curation guide:
- `docs/community-picks-playbook.md`

## Overview
- Content source: filesystem (not DB), generated externally by a curator CLI.
- Votes: persisted in an embedded DB table (`content_vote`) with upsert semantics.
- Visual cards:
  - `video_story` / `podcast` support inline preview widgets (lazy-loaded, on click).
  - `article_blog` supports cover images through optional `thumbnail_url`.
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
  - Optional Discord API integration for board counters:
    - `community.board.discord.integration.enabled` (default `false`)
    - `community.board.discord.guild-id` (Discord server/guild id)
    - `community.board.discord.bot-token` (optional, from secret env var)
    - `community.board.discord.cache-ttl` (default `PT1H`)
    - `community.board.discord.retry-interval` (default `PT5M`)
    - `community.board.discord.refresh-interval` (default `30m`)
    - `community.board.discord.request-timeout` (default `PT5S`)

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
- `media_type` (`video_story|podcast|article_blog`, defaults to `article_blog`)
- `thumbnail_url` (`http/https`, optional cover image used by Community cards)

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
  - Discord API counters cache:
    - Data is refreshed asynchronously and never blocks requests.
    - Source fallback order: `bot_api` -> `preview_api` -> `widget_api` -> file snapshot.
    - If Discord API fails, board keeps last successful in-memory snapshot.

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
  - Optional filter: `filter=all|internet|members`
  - Optional media filter: `media=all|video_story|podcast|article_blog`
- `GET /api/community/content/{id}`
- `PUT /api/community/content/{id}/vote`

### Community Feed submissions
- `POST /api/community/submissions` (auth required)
- `GET /api/community/submissions/mine` (auth required)
- `GET /api/community/submissions/pending` (admin only)
- `PUT /api/community/submissions/{id}/approve` (admin only)
- `PUT /api/community/submissions/{id}/reject` (admin only)

Submission notes:
- Proposals are persisted asynchronously in `${homedir.data.dir}/community/submissions/pending.json`.
- Daily guardrail per user: `community.submissions.daily-limit` (default `5`).
- URL validation is canonicalized (`http/https`, strips tracking params like `utm_*`) to avoid duplicate proposals for the same resource.
- On approve, Homedir generates one YAML item in the curated content directory so the existing feed/ranking/votes pipeline remains unchanged.
- Admins can moderate pending proposals from `/comunidad/moderation` (approve/reject UI backed by the API endpoints above).

## Community Board Data Sources
- `HomeDir users`: internal `UserProfile` store (Google-auth users with local profile).
- `GitHub users`: union of linked GitHub accounts in `UserProfile` + synced community members.
- `Discord users`: optional file (`members` array) loaded from configured path and cached.
  - Discord card now also shows:
    - listed profiles count (`discord-users.yml`),
    - online now (if provided by Discord API),
    - data source and last successful sync timestamp.

### Discord Integration Security Notes
- Recommended: keep `COMMUNITY_BOARD_DISCORD_BOT_TOKEN` only as an environment secret in the VPS.
- Never commit tokens to git or write them to `.properties`.
- The app does not log token values.
- If no token is provided, integration still works in public mode using preview/widget endpoints when available.

Each item includes counts and current user vote:
- `vote_counts.recommended`
- `vote_counts.must_see`
- `vote_counts.not_for_me`
- `my_vote`
