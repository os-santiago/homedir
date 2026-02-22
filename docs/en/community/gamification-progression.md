# Gamification Progression Model

This document defines how HomeDir awards XP by class and how level progression is calculated.

## Class XP model

Users do not select a single class manually. Each action awards XP to one class:

- `Engineer`: platform/project and event delivery exploration.
- `Scientist`: community curation, board discovery, and public profile exploration.
- `Warrior`: session/agenda and CFP execution.
- `Mage`: consistency, onboarding, and profile continuity.

The profile shows:

- global XP + level
- per-class XP
- dominant class (highest XP)

## Activity map (current)

| Activity key | Class | XP | Rule |
|---|---:|---:|---|
| `first_login_bonus` | Mage | 100 | once ever |
| `daily_checkin` | Mage | 10 | once per day |
| `home_view` | Mage | 4 | once per day |
| `profile_view` | Mage | 3 | once per day |
| `github_linked` | Engineer | 25 | once ever |
| `discord_linked` | Mage | 25 | once ever |
| `community_main_view` | Scientist | 4 | once per day |
| `community_picks_view` | Scientist | 5 | once per day |
| `community_propose_view` | Scientist | 5 | once per day |
| `community_board_view` | Scientist | 4 | once per day |
| `community_board_members_view` | Scientist | 5 | once per day per group |
| `community_review` | Scientist | 2 | once per day per view/filter |
| `community_vote` | Scientist | 5 | every valid vote |
| `community_submission` | Scientist | 20 | every submission |
| `community_submission_approved` | Scientist | 40 | every approval |
| `public_profile_view` | Scientist | 2 | once per day per profile |
| `event_directory_view` | Engineer | 4 | once per day |
| `event_view` | Engineer | 3 | once per day per event |
| `project_view` | Engineer | 3 | once per day |
| `talk_view` | Warrior | 5 | once per day per talk |
| `agenda_view` | Warrior | 4 | once per day per agenda/scope |
| `cfp_submit` | Warrior | 30 | every submission |
| `cfp_accepted` | Warrior | 40 | every accepted CFP |
| `session_evaluation` | Warrior | 12 | first rating only |
| `board_profile_open` | Scientist | 4 | once per day per member |

## Level curve

`QuestService` uses a dynamic formula (instead of a fixed 10-level table):

- Technical max level: `9999`
- Projection target: `level 1000` at approximately `100,000 XP`
- Intended progression pace: ~5 years for a sustained active user (~55 XP/day average)

Formula:

- Let `n = level - 1`
- `xpForLevel(level) = round((100 * n) + (A * n^2))`
- `A` is derived so that `xpForLevel(1000) ~= 100,000`

The service resolves level by binary-searching the highest level whose threshold is <= current XP.

## Notes for product tuning

- Adjust activity XP first when you need behavior changes.
- Adjust curve parameters only when progression pacing is globally too fast/slow.
- Keep once-per-day gates on navigation activities to avoid refresh abuse.
