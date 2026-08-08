# Bounty Hunters Program

## Overview

The **Bounty Hunters Program** is a gamified reputation system that recognizes and rewards community members who contribute to Homedir's quality and evolution through issue creation and resolution.

## Goals

- **Encourage quality issue reporting** - Recognize members who identify bugs, gaps, and improvements
- **Motivate issue resolution** - Reward contributors who fix reported problems
- **Build contributor reputation** - Create visible recognition for different types of contributions
- **Gamify participation** - Make contributing fun and competitive through levels and leaderboards

## How It Works

### Issue Creation Points

When you create an issue that adds value to the platform:

1. **Submit the issue** with clear description and context
2. **Admin validates** the issue and assigns an eligible label
3. **You earn points** based on the label's impact level

**Example:**
- You report a critical bug → Admin adds `bug-impact-high` → You get **30 points**

### Issue Resolution Points

When you resolve an existing issue:

1. **Submit a PR** that fixes a labeled issue
2. **PR gets approved** and merged
3. **You earn points** based on the issue's label

**Example:**
- You fix issue #123 labeled `feature-request` → PR merged → You get **20 points**

### Levels & Progression

As you accumulate points, you progress through 5 levels:

| Level | Required Points | Reward Frame |
|-------|----------------|--------------|
| 🥉 Novice Bounty Hunter | 50+ | `bounty-hunter-novice-frame` |
| 🥈 Experienced Bounty Hunter | 150+ | `bounty-hunter-experienced-frame` |
| 🥇 Professional Bounty Hunter | 400+ | `bounty-hunter-professional-frame` |
| 💎 Ultimate Bounty Hunter | 800+ | `bounty-hunter-ultimate-frame` |
| ⭐ Transcendental Bounty Hunter | 1500+ | `bounty-hunter-transcendental-frame` |

Each level unlocks exclusive visual rewards in the Homedir store.

## Eligible Labels

Only issues with these labels (assigned by admins) generate Bounty Hunter points:

### Bug Fixes
- **`bug-impact-low`** (5 points) - Minor bugs, typos, cosmetic issues
- **`bug-impact-medium`** (15 points) - Functional bugs affecting some users
- **`bug-impact-high`** (30 points) - Critical bugs, security issues, data loss risks

### Features
- **`feature-request`** (20 points) - New features or significant enhancements

### Documentation
- **`documentation-improvement`** (10 points) - Docs additions, clarifications, examples

### Maintenance
- **`platform-maintenance`** (15 points) - Infrastructure, performance, tooling improvements

## Validation Rules

### Who Can Validate Issues?

Only authorized admin accounts can assign labels that trigger points:
- `os-santiago`
- `scanales-stack`
- `admin-github-user`

### When Are Points Awarded?

**For Issue Creation:**
✅ Issue is reviewed and validated  
✅ Admin assigns an eligible label  
✅ Points awarded to issue creator  

❌ Self-assigned labels don't count  
❌ Points only awarded once per issue  

**For Issue Resolution:**
✅ PR is linked to the issue  
✅ PR is approved and merged  
✅ Points awarded to PR author  

❌ Draft PRs don't count  
❌ Points only awarded once per issue  

## API Access

The Bounty Hunters system is fully accessible via REST API:

### Get Leaderboard
```bash
curl https://homedir.opensourcesantiago.io/api/bounty-hunters/leaderboard?limit=50
```

**Response:**
```json
[
  {
    "userId": "scanales-stack",
    "totalPoints": 245,
    "level": "Experienced Bounty Hunter",
    "updatedAt": "2026-08-04T15:30:00Z"
  }
]
```

### Get User Profile
```bash
curl https://homedir.opensourcesantiago.io/api/bounty-hunters/profile/scanales-stack
```

**Response:**
```json
{
  "userId": "scanales-stack",
  "totalPoints": 245,
  "issueCreationPoints": 125,
  "issueResolutionPoints": 120,
  "currentLevel": "Experienced Bounty Hunter",
  "currentLevelThreshold": 150,
  "issuesCreatedCount": 8,
  "issuesResolvedCount": 6,
  "rank": 3,
  "totalHunters": 42,
  "history": [
    {
      "eventId": "bh_a1b2c3d4e5f6g7h8",
      "userId": "scanales-stack",
      "eventType": "ISSUE_RESOLVED_BY_PR",
      "issueNumber": "1374",
      "prNumber": "1376",
      "pointsAwarded": 15,
      "label": "platform-maintenance",
      "timestamp": "2026-08-04T15:30:00Z"
    }
  ]
}
```

### Get Eligible Labels
```bash
curl https://homedir.opensourcesantiago.io/api/bounty-hunters/config/labels
```

### Get All Levels
```bash
curl https://homedir.opensourcesantiago.io/api/bounty-hunters/config/levels
```

## Finding Bounty Challenges

Look for open issues with bounty hunter labels:

**Via GitHub Search:**
```
is:issue is:open repo:os-santiago/homedir label:platform-maintenance
is:issue is:open repo:os-santiago/homedir label:bug-impact-high
is:issue is:open repo:os-santiago/homedir label:feature-request
```

**Via GitHub CLI:**
```bash
gh issue list --label platform-maintenance --state open
gh issue list --label bug-impact-high --state open
```

**Example Bounty Challenge:**
- Issue [#1374](https://github.com/os-santiago/homedir/issues/1374) - Performance investigation (15 pts)

## Best Practices

### Creating Quality Issues

**✅ Good issue:**
- Clear title describing the problem
- Steps to reproduce (for bugs)
- Expected vs actual behavior
- Screenshots/logs when relevant
- Impact assessment

**❌ Low-quality issue:**
- Vague title like "Fix the thing"
- No context or reproduction steps
- Duplicate of existing issue
- Feature request with no use case

### Resolving Issues Effectively

**✅ Good PR:**
- Links to the issue (`Closes #123`)
- Follows contribution guidelines
- Includes tests
- Passes CI checks
- Clear commit messages

**❌ Low-quality PR:**
- No issue reference
- Breaks existing functionality
- No tests
- Code style violations

## Dashboard (Coming Soon)

A public web dashboard is planned ([#1375](https://github.com/os-santiago/homedir/issues/1375)) that will include:

- 📊 **Leaderboard** - Top Bounty Hunters ranking
- 👤 **User Profiles** - Detailed stats and history
- 🎯 **Active Bounties** - List of open issues with labels
- 🏅 **Level Progress** - Visual progression tracking
- 🛍️ **Store Integration** - Unlock frames by level

Want to contribute to the dashboard? Check out [#1375](https://github.com/os-santiago/homedir/issues/1375)!

## Technical Implementation

### Backend Components

**Entities:**
- `BountyHunterScore` - User scores and stats
- `BountyHunterEvent` - Audit trail of all scoring events
- `BountyHunterLevel` - Level definitions and thresholds
- `IssueImpactLabel` - Label configurations with points

**Services:**
- `BountyHunterService` - Business logic (award points, calculate levels)
- `BountyHunterConfigService` - Configuration management
- `BountyHunterRepository` - Data persistence (in-memory)

**API Resource:**
- `BountyHunterApiResource` - REST endpoints at `/api/bounty-hunters`

**Code Location:**
- `quarkus-app/src/main/java/com/scanales/homedir/reputation/bounty/`

### Event Types

The system tracks these event types:

- `ISSUE_CREATED` - Issue submitted (no points yet)
- `ISSUE_VALIDATED` - Issue reviewed by admin
- `ISSUE_LABEL_APPROVED` - Label assigned, points awarded to creator
- `ISSUE_RESOLVED_BY_PR` - PR merged, points awarded to resolver
- `PR_APPROVED` - Pull request approved
- `REWARD_UNLOCKED` - Bounty Hunter frame unlocked

### Storage

Currently uses **in-memory storage** with:
- `ConcurrentHashMap` for scores (thread-safe)
- Synchronized `ArrayList` for event log
- No persistence across restarts (planned for future)

## Roadmap

### ✅ Implemented (v3.403+)
- Backend API and business logic
- 6 eligible labels with points
- 5 progression levels
- Leaderboard and profile endpoints
- Event tracking and audit trail

### 🚧 In Progress
- Public web dashboard ([#1375](https://github.com/os-santiago/homedir/issues/1375))
- Store integration with frames
- Navigation visibility

### 📋 Planned
- Persistent storage (database)
- GitHub webhook automation
- Email notifications for level-ups
- Season-based leaderboards
- Additional label types
- Admin validation UI
- Profile badges

## FAQ

**Q: Can I assign the labels myself?**  
A: No, only authorized admins can assign labels that trigger points. This prevents gaming the system.

**Q: Do draft PRs count?**  
A: No, only approved and merged PRs earn resolution points.

**Q: Can I earn points for old issues?**  
A: Yes, as long as an admin validates them and assigns an eligible label.

**Q: What happens if my PR is reverted?**  
A: Currently points are not deducted. This may change in future versions.

**Q: Are there seasonal resets?**  
A: No, points accumulate permanently (for now). Seasonal leaderboards may be added later.

**Q: How do I see my current rank?**  
A: Use the API endpoint `/api/bounty-hunters/profile/{yourGitHubUsername}` until the dashboard is live.

**Q: Can I contribute to the dashboard?**  
A: Absolutely! Check [#1375](https://github.com/os-santiago/homedir/issues/1375) and submit a PR.

## Examples

### Example 1: Bug Report

1. You discover a performance issue
2. You create issue #1374 with clear description
3. Admin reviews and adds label `platform-maintenance`
4. You earn **15 points** for issue creation

### Example 2: Feature Implementation

1. You see issue #1375 labeled `feature-request` (20 pts)
2. You implement the Bounty Hunters dashboard
3. You submit PR #1376 with `Closes #1375`
4. PR is approved and merged
5. You earn **20 points** for issue resolution
6. Combined with other contributions, you hit **50 points**
7. You unlock **Novice Bounty Hunter** level 🥉

### Example 3: Critical Bug Fix

1. You find a security vulnerability
2. You report it privately per SECURITY.md
3. Admin creates issue and assigns `bug-impact-high`
4. You submit the fix
5. You earn **30 points** creation + **30 points** resolution = **60 points**
6. You jump directly to Novice level!

## References

- Original Feature Request: [#997](https://github.com/os-santiago/homedir/issues/997)
- Backend Implementation: [PR #1003](https://github.com/os-santiago/homedir/pull/1003)
- Dashboard UI: [#1375](https://github.com/os-santiago/homedir/issues/1375)
- Example Bounty: [#1374](https://github.com/os-santiago/homedir/issues/1374)

## Contact

Questions about Bounty Hunters? Ask in:
- [Discord #soporte-homedir](https://discord.gg/3eawzc9ybc)
- GitHub Discussions
- Issue comments

---

**Ready to become a Bounty Hunter?** Start by looking for [open bounty issues](https://github.com/os-santiago/homedir/issues?q=is%3Aissue+is%3Aopen+label%3Aplatform-maintenance%2Cbug-impact-high%2Cbug-impact-medium%2Cbug-impact-low%2Cfeature-request%2Cdocumentation-improvement) and make your first contribution!
