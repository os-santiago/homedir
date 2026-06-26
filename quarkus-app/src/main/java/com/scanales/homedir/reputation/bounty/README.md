# Bounty Hunters Program

Implementation of Issue #997 - Bounty Hunters Program for Issue Impact and Resolution Reputation.

## Overview

The Bounty Hunters Program recognizes community members who create valuable issues and contributors who resolve them through approved pull requests.

## Core Components

### Entities

1. **BountyHunterScore** - Tracks user scores, levels, and contribution counts
   - Total points (issue creation + resolution)
   - Issue creation points
   - Issue resolution points
   - Current level
   - Issue counts
   - Last updated timestamp

2. **BountyHunterLevel** - Six-tier progression system
   - NONE (0 points)
   - NOVICE (50+ points) → bounty-hunter-novice-frame
   - EXPERIENCED (150+ points) → bounty-hunter-experienced-frame
   - PROFESSIONAL (400+ points) → bounty-hunter-professional-frame
   - ULTIMATE (800+ points) → bounty-hunter-ultimate-frame
   - TRANSCENDENTAL (1500+ points) → bounty-hunter-transcendental-frame

3. **BountyHunterEvent** - Audit trail for all scoring events
   - Event ID
   - User ID
   - Event type
   - Issue/PR numbers
   - Points awarded
   - Label
   - Validator user ID
   - Timestamp

4. **IssueImpactLabel** - Label configuration with point values

### Services

1. **BountyHunterConfigService** - Configuration management
   - Eligible labels and point values
   - Admin user authorization
   - Label validation

2. **BountyHunterRepository** - Data persistence
   - In-memory storage with ConcurrentHashMap
   - Event log with synchronized access
   - Leaderboard generation
   - User score queries

3. **BountyHunterService** - Business logic
   - Award issue creation points (admin-validated)
   - Award issue resolution points (PR merge)
   - Retrieve user scores
   - Generate leaderboards
   - Track event history

## Configuration

### Eligible Labels and Points

| Label | Points | Description |
|-------|--------|-------------|
| bug-impact-low | 5 | Low impact bug fix |
| bug-impact-medium | 15 | Medium impact bug fix |
| bug-impact-high | 30 | High impact bug fix |
| feature-request | 20 | Feature request or enhancement |
| documentation-improvement | 10 | Documentation improvement |
| platform-maintenance | 15 | Platform maintenance task |

### Admin Users

Only these users can validate issues and award creation points:
- scanalesespinoza
- admin-github-user

## Usage

### Award Issue Creation Points

When an administrator validates an issue:

```java
@Inject
BountyHunterService bountyHunterService;

BountyHunterScore updated = bountyHunterService.awardIssueCreationPoints(
    "github-username",  // Issue creator
    "123",              // Issue number
    "bug-impact-high",  // Label
    "admin-user"        // Validator (must be admin)
);
```

### Award Issue Resolution Points

When a PR resolving an issue is merged:

```java
BountyHunterScore updated = bountyHunterService.awardIssueResolutionPoints(
    "github-username",  // PR author
    "123",              // Issue number
    "456",              // PR number
    "bug-impact-high"   // Label from issue
);
```

### Get Leaderboard

```java
List<BountyHunterScore> top100 = bountyHunterService.getLeaderboard(100);
```

### Get User Score

```java
Optional<BountyHunterScore> score = bountyHunterService.getScoreForUser("username");
```

### Get User Event History

```java
List<BountyHunterEvent> events = bountyHunterService.getEventsForUser("username");
```

## Level Progression

Points are automatically calculated from total contributions:

- **0-49 points**: NONE - No level yet
- **50-149 points**: NOVICE BOUNTY HUNTER - First validated contributions
- **150-399 points**: EXPERIENCED BOUNTY HUNTER - Recurrent validated contributions
- **400-799 points**: PROFESSIONAL BOUNTY HUNTER - High-impact contributor
- **800-1499 points**: ULTIMATE BOUNTY HUNTER - Top-tier impact contributor
- **1500+ points**: TRANSCENDENTAL BOUNTY HUNTER - Exceptional long-term contributor

Each level unlocks a corresponding user frame reward in the store.

## Event Types

- **ISSUE_CREATED** - Issue created (no points yet)
- **ISSUE_VALIDATED** - Issue validated by admin
- **ISSUE_LABEL_APPROVED** - Label approved, points awarded to creator
- **ISSUE_RESOLVED_BY_PR** - Issue resolved by merged PR, points awarded to resolver
- **PR_APPROVED** - Pull request approved
- **REWARD_UNLOCKED** - Bounty Hunter frame unlocked

## Validation Rules

### Issue Creation Points

✅ Awarded when:
- Issue has an eligible label
- Label was added/approved by an administrator
- User has not already received points for this issue

❌ Not awarded when:
- Label is not in eligible list
- Validator is not an authorized admin
- Points already awarded for this issue

### Issue Resolution Points

✅ Awarded when:
- PR is linked to an issue
- Issue has an eligible label
- PR is merged/approved
- User has not already received resolution points for this issue

❌ Not awarded when:
- Label is not eligible
- PR is not merged
- Points already awarded for this resolution

## Testing

Unit tests verify:
- Level calculation logic
- Score mutations
- Point accumulation
- Service validation rules
- Configuration management

Run tests:
```bash
cd quarkus-app
./mvnw test -Dtest="BountyHunter*"
```

## Future Enhancements

Potential improvements for follow-up issues:

1. **REST API** - Public endpoints for leaderboard and profiles
2. **Dashboard UI** - Bounty Hunter leaderboard page
3. **Profile Page** - Detailed user bounty hunter profile
4. **Store Integration** - Unlock frames based on level
5. **GitHub Webhook** - Automatic point awarding on issue/PR events
6. **Persistent Storage** - Database persistence instead of in-memory
7. **Admin UI** - Configuration interface for labels and admins
8. **Notifications** - Alert users when they earn points or level up
9. **Badges** - Visual badges for different achievement types
10. **Analytics** - Contribution trends and leaderboard history

## Related Files

- Issue: https://github.com/os-santiago/homedir/issues/997
- Branch: feat/issue-997-bounty-hunters
- Package: com.scanales.homedir.reputation.bounty

## Implementation Status

### ✅ Completed

- Core entity models (Score, Level, Event, Label)
- Service layer with business logic
- Repository with in-memory persistence
- Configuration service
- Unit tests for models and service
- Documentation

### 🚧 In Progress / Future Work

- REST API endpoints
- Frontend dashboard
- Store integration
- GitHub webhook automation
- Persistent database storage

---

**Issue**: #997  
**Author**: Claude Sonnet 4.5  
**Date**: 2026-06-25  
**Status**: Core implementation complete, ready for review
