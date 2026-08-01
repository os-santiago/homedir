# GitHub Achievement Hub

The Achievement Hub (`/achievements`) is a dashboard that helps community members unlock GitHub achievements/highlights through guided contributions to the os-santiago organization.

## Overview

The hub provides:

1. **Achievement catalog** — A curated list of GitHub achievements (Pull Shark, YOLO, Quickdraw, Pair Extraordinaire, Starstruck, Galaxy Brain, Public Sponsor, Heart On Your Sleeve, Open Sourcerer) with bilingual step-by-step guides.
2. **GitHub API verification** — Each achievement is verified against the GitHub API (Search API, Repos API) to check if the user has met the criteria.
3. **XP rewards** — When an achievement is verified, the user can claim XP via the gamification system. Each achievement has a corresponding `GamificationActivity` entry.
4. **Leaderboard** — A leaderboard showing the top members by unlocked achievement count and total XP.
5. **Org repos** — Links to the os-santiago repositories that help earn each achievement.
6. **GitHub highlights** — Information about GitHub profile highlights (Pro, Developer Program, Security Bounty, Galaxy Brain).
7. **Mobile-responsive** — The dashboard follows the HomeDir design system and is fully responsive.

## Architecture

### Files

| File | Purpose |
|------|---------|
| `achievements/AchievementCatalog.java` | Static catalog of achievements and org repos |
| `achievements/AchievementService.java` | GitHub API verification, XP awarding, leaderboard |
| `public_/AchievementResource.java` | Web resource for `/achievements` page |
| `public_/AchievementApiResource.java` | REST API for verification and claiming XP |
| `templates/AchievementResource/index.html` | Qute template for the dashboard |
| `css/achievements.css` | Styles for the dashboard |
| `js/achievements.js` | Client-side logic for the claim XP button |

### Data Flow

1. User visits `/achievements`
2. `AchievementResource` checks if the user is authenticated and has GitHub linked
3. If GitHub is linked, `AchievementService.verifyAchievements()` queries the GitHub API
4. Results are cached for 30 minutes per GitHub login
5. The template renders the achievement catalog with verification status
6. User can click "Claim XP" to trigger `AchievementApiResource.claimAchievement()`
7. The API verifies the achievement again and awards XP via `GamificationService`

## How to Add New Achievements

### 1. Add to the catalog

Open `AchievementCatalog.java` and add a new `guide()` call in the constructor:

```java
guide(
    "my-new-achievement",           // unique key
    "My New Achievement",           // title
    "Description in English",       // description
    "Descripción en español",       // descriptionEs
    "contribution",                 // category: contribution, collaboration, social
    "https://docs.github.com/...",  // docUrl
    1,                              // threshold (minimum count to unlock)
    50,                             // xpReward
    List.of("Step 1", "Step 2"),    // steps (English)
    List.of("Paso 1", "Paso 2")),   // stepsEs (Spanish)
```

### 2. Add a GamificationActivity

Open `GamificationActivity.java` and add:

```java
ACHIEVEMENT_MY_NEW(
    "achievement_my_new", 50, QuestClass.ENGINEER, false, true,
    "GitHub Achievement: My New Achievement"),
```

### 3. Add verification logic

Open `AchievementService.java` and add a case in `verifySingleAchievement()`:

```java
case "my-new-achievement" -> countSearchResults(
    "author:" + login + " type:pr is:merged org:os-santiago", token);
```

### 4. Map the activity

In `AchievementService.activityForAchievement()`, add:

```java
case "my-new-achievement" -> GamificationActivity.ACHIEVEMENT_MY_NEW;
```

### 5. Add route mapping

In `GamificationService.normalizeReputationReference()`, add:

```java
case ACHIEVEMENT_MY_NEW -> "achievement-my-new";
```

### 6. Add i18n messages (if needed)

Add any new UI messages to `AppMessages.java` and the properties files.

## GitHub API Rate Limits

The service uses the `GH_TOKEN` environment variable for server-side API calls. Without a token, the GitHub API has a rate limit of 10 requests/minute for search and 60 requests/hour for other endpoints. With a token, the limits are 30 requests/minute for search and 5000 requests/hour for other endpoints.

Achievement verification results are cached for 30 minutes per GitHub login to minimize API calls.

## Future Enhancements

- **Phase 2**: Interactive walkthroughs with progress tracking
- **Phase 3**: Webhook-based real-time achievement detection
- **Phase 4**: Achievement badges on public profiles
- **Phase 5**: Achievement-specific challenges and quests
