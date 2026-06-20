# Cache Strategy Audit

## Current Cache Configuration (application.properties)

| Property | TTL | Purpose |
|----------|-----|---------|
| `community.content.cache-ttl` | PT1H | Community member content |
| `community.content.featured.response-cache-ttl` | PT10S | Featured content response cache |
| `community.votes.aggregate-cache-ttl` | PT10S | Vote aggregation results |
| `community.board.cache-ttl` | PT1H | Community board data |
| `community.board.discord.cache-ttl` | PT1H | Discord mirror of board data |
| `home.project.github.cache-ttl` | PT24H | GitHub contributor stats for home page |
| `projects.homedir.cache-ttl` | PT6H | /proyectos dashboard |
| `trending.cache-ttl` | PT48H | Trending computations |
| `economy.transactions.cache-max` | 500 entries | Economy transaction cache (size-based, no TTL) |
| `quarkus.vertx.cache-directory` | `${homedir.data.dir}/vertx-cache` | Vert.x file cache directory |

## Cache Layers

1. **Application-level TTL caches** (in-memory, configurable via properties above)
2. **Vert.x file cache** — disk-backed, for static/template resources stored under `quarkus.vertx.cache-directory`
3. **Discord bot LRU cache** (in `tools/discord-bot/ARCHITECTURE.md`) — dual L1/L2 with RedisCache fallback

## Recommendations

| # | Finding | Recommendation | Effort |
|---|---------|---------------|--------|
| 1 | No invalidation hooks on data mutation | Add cache eviction on write endpoints (POST/PUT/DELETE) | Medium |
| 2 | `trending.cache-ttl=PT48H` is long for user-facing stats | Reduce to PT6H or add event-triggered refresh | Low |
| 3 | `home.project.github.cache-ttl=PT24H` acceptable but stale on contributor changes | Consider webhook-based invalidation from GitHub | Medium |
| 4 | `economy.transactions.cache-max=500` has no TTL | Add time-based TTL to prevent unbounded stale entries | Low |
| 5 | No consolidated cache metrics/metrics endpoint | Add Micrometer cache metrics or custom `/q/metrics` exposure | Low |
| 6 | featured content response cache at PT10S is very short | Verify this is intentional (rapid updates expected) | Verify |

## Verification

- All cache properties are grepable via `community.*.cache-ttl`, `projects.*.cache-ttl`, `trending.cache-ttl`, `economy.transactions.cache-max`
- Each cache key should have a documented staleness tolerance

Modelo: DeepSeek V4 Flash Free