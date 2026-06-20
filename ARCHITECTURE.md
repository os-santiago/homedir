# Homedir Architecture Documentation

## Overview
This document describes architectural patterns, design decisions, and technical guidelines for the Homedir codebase.

## Key Improvements (Issue #733)

### 1. H2 Database Credentials Externalization ✅
- **File**: CommunityVoteService.java
- **Change**: Hardcoded credentials removed
- **Configuration**: Use `community.votes.db.username` and `community.votes.db.password`

### 2. HTTP Client Reuse ✅
- **Pattern**: Singleton HttpClient instances (not per-request)
- **GithubService**: Already correct
- **DiscordLinkService**: Needs refactoring (documented)

### 3. Documentation ✅  
- Architecture patterns documented
- Implementation guide created

## Design Patterns

### Persistence
- File-based JSON with atomic writes (PersistenceService)
- H2 embedded for votes (credentials now externalized)
- Schema versioning

### Concurrency
- Synchronized blocks (EconomyService.stateLock)
- AtomicReference for caches
- Concurrent collections

### HTTP Clients
- Singleton pattern (application-scoped)
- Configure timeouts upfront
- Reuse across requests

## Trending GitHub Repositories Service

### Overview
The TrendingService scrapes GitHub's trending page to display popular repositories with automatic cache refreshing.

### Architecture
- **TrendingService**: Application-scoped service managing cache and scraping
  - Singleton HttpClient (follows GithubService pattern)
  - AtomicReference caches for each period (daily/weekly/monthly)
  - Scheduled refresh jobs using Quarkus @Scheduled
  - File-based JSON persistence in `data/trending/`
  - Exponential backoff on rate limiting

- **Cache Strategy**:
  - Separate cache per period (daily/weekly/monthly)
  - TTL: 48 hours (configurable)
  - Fallback to stale cache on scraping failure
  - On-demand refresh for stale caches

- **Scraping**:
  - URL: `https://github.com/trending?since={period}`
  - HTML parsing with regex (no external dependencies)
  - Rate limiting: 429 response triggers 60s wait
  - User-Agent: `Mozilla/5.0 (compatible; HomedirBot/1.0)`

- **Scheduling**:
  - Daily: `0 0 * * ?` (midnight UTC)
  - Weekly: `0 0 * * MON` (Monday midnight UTC)
  - Monthly: `0 0 1 * *` (1st of month midnight UTC)
  - All configurable via `application.properties`

### Data Model
- **TrendingRepo**: Record with name, owner, description, stars, language, url, descriptionEs
- **TrendingCacheSnapshot**: Repos list + timestamps + period
- **TrendingPeriod**: Enum (DAILY, WEEKLY, MONTHLY)

### Zero Dependencies
The implementation uses only JDK HttpClient and regex parsing, avoiding external scraping libraries to maintain minimal dependencies.

## Pending Improvements
- AppMessages.java split by domain (2589 lines → multiple files)
- WebSocket race condition fixes
- Bounded deduplication maps
- Template i18n improvements

See REFACTORING.md for implementation details.
