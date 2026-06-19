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

## Pending Improvements
- AppMessages.java split by domain (2589 lines → multiple files)
- WebSocket race condition fixes
- Bounded deduplication maps
- Template i18n improvements

See REFACTORING.md for implementation details.
