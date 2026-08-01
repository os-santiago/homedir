"""Static assertions for issue #1043: GitHub Achievement Hub.

The Achievement Hub is an interactive section that guides community members to
unlock GitHub achievements using the os-santiago open-source repositories.

Full scope (issue #1043):
1. Achievement Hub page at /achievements with progress tracking
2. Interactive step-by-step guides per achievement
3. GitHub API integration for verification (Pull Shark, Pair Extraordinaire, etc.)
4. Gamification integration (XP awards when achievements are completed)
5. Highlights section (Pro, Developer Program, Security Bounty, Galaxy Brain)
6. Community leaderboard (who has the most completed achievements)
7. Progress states: locked / in-progress / completed
8. Self-claim mechanism for achievements without a public API
"""

from pathlib import Path
import re

CATALOG_JAVA = Path(
    "quarkus-app/src/main/java/com/scanales/homedir/achievements/AchievementCatalog.java"
).read_text()
SERVICE_JAVA = Path(
    "quarkus-app/src/main/java/com/scanales/homedir/achievements/AchievementService.java"
).read_text()
PROGRESS_JAVA = Path(
    "quarkus-app/src/main/java/com/scanales/homedir/achievements/AchievementProgress.java"
).read_text()
VIEW_JAVA = Path(
    "quarkus-app/src/main/java/com/scanales/homedir/achievements/AchievementView.java"
).read_text()
RESOURCE_JAVA = Path(
    "quarkus-app/src/main/java/com/scanales/homedir/public_/AchievementResource.java"
).read_text()
TEMPLATE = Path(
    "quarkus-app/src/main/resources/templates/AchievementResource/index.html"
).read_text()
HEADER = Path(
    "quarkus-app/src/main/resources/templates/fragments/header.html"
).read_text()
I18N = Path("quarkus-app/src/main/resources/i18n.properties").read_text()
I18N_ES = Path("quarkus-app/src/main/resources/i18n_es.properties").read_text()
I18N_EN = Path("quarkus-app/src/main/resources/i18n_en.properties").read_text()
APP_MESSAGES = Path(
    "quarkus-app/src/main/java/com/scanales/homedir/config/AppMessages.java"
).read_text()
CSS = Path(
    "quarkus-app/src/main/resources/META-INF/resources/css/achievements.css"
).read_text()
GAMIFICATION_ACTIVITY = Path(
    "quarkus-app/src/main/java/com/scanales/homedir/model/GamificationActivity.java"
).read_text()
GAMIFICATION_SERVICE = Path(
    "quarkus-app/src/main/java/com/scanales/homedir/service/GamificationService.java"
).read_text()


# ---------------------------------------------------------------------------
# 1. Achievement catalog with all 9 achievements (criterion #1)
# ---------------------------------------------------------------------------


def test_catalog_has_all_9_achievements() -> None:
    """The catalog must include all 9 GitHub achievements from the issue."""
    expected_keys = [
        "pull-shark",
        "quickdraw",
        "pair-extraordinaire",
        "yolo",
        "starstruck",
        "galaxy-brain",
        "public-sponsor",
        "heart-on-your-sleeve",
        "open-sourcerer",
    ]
    for key in expected_keys:
        assert key in CATALOG_JAVA, f"Achievement '{key}' missing from catalog"


def test_catalog_has_bilingual_descriptions() -> None:
    """Each achievement must have both English and Spanish descriptions."""
    assert "description" in CATALOG_JAVA
    assert "descriptionEs" in CATALOG_JAVA


def test_catalog_has_interactive_guides() -> None:
    """Each achievement must include step-by-step guides (criterion #2)."""
    assert "steps" in CATALOG_JAVA
    assert "stepsEs" in CATALOG_JAVA
    # Verify at least some steps are defined
    assert "Fork an os-santiago repository" in CATALOG_JAVA
    assert "Haz fork de un repositorio" in CATALOG_JAVA


def test_catalog_has_org_repos() -> None:
    """The catalog must reference os-santiago org repos."""
    assert "os-santiago/homedir" in CATALOG_JAVA
    assert "os-santiago/demo-repository" in CATALOG_JAVA


def test_catalog_find_method_exists() -> None:
    """The catalog must have a find() method for looking up achievements by key."""
    assert "public Achievement find" in CATALOG_JAVA


# ---------------------------------------------------------------------------
# 2. AchievementService with GitHub API verification (criterion #3)
# ---------------------------------------------------------------------------


def test_achievement_service_exists() -> None:
    """AchievementService must exist and be ApplicationScoped."""
    assert "@ApplicationScoped" in SERVICE_JAVA
    assert "class AchievementService" in SERVICE_JAVA


def test_achievement_service_verifies_via_github_api() -> None:
    """The service must use the GitHub API to verify achievements (criterion #3)."""
    assert "api.github.com" in SERVICE_JAVA
    assert "countMergedPRs" in SERVICE_JAVA
    assert "countCoAuthoredCommits" in SERVICE_JAVA
    assert "checkQuickdraw" in SERVICE_JAVA


def test_achievement_service_persists_progress() -> None:
    """The service must persist achievement progress to a JSON file."""
    assert "achievement-progress.json" in SERVICE_JAVA
    assert "saveProgress" in SERVICE_JAVA
    assert "loadProgress" in SERVICE_JAVA


def test_achievement_service_has_verify_endpoint_support() -> None:
    """The service must have a verifyAchievements method."""
    assert "verifyAchievements" in SERVICE_JAVA


def test_achievement_service_has_self_claim() -> None:
    """The service must support self-claiming achievements without a public API."""
    assert "selfClaim" in SERVICE_JAVA


def test_achievement_service_has_leaderboard() -> None:
    """The service must provide a leaderboard (criterion #6)."""
    assert "getLeaderboard" in SERVICE_JAVA
    assert "LeaderboardEntry" in SERVICE_JAVA


# ---------------------------------------------------------------------------
# 3. AchievementProgress model (criterion #7)
# ---------------------------------------------------------------------------


def test_achievement_progress_model_exists() -> None:
    """AchievementProgress must exist with states map."""
    assert "class AchievementProgress" in PROGRESS_JAVA
    assert "AchievementState" in PROGRESS_JAVA


def test_achievement_progress_has_three_states() -> None:
    """AchievementState must support locked, in_progress, and completed states."""
    assert "locked" in PROGRESS_JAVA
    assert "in_progress" in PROGRESS_JAVA
    assert "completed" in PROGRESS_JAVA
    assert "isCompleted" in PROGRESS_JAVA


# ---------------------------------------------------------------------------
# 4. Gamification integration (criterion #4)
# ---------------------------------------------------------------------------


def test_gamification_has_achievement_activities() -> None:
    """GamificationActivity must include entries for all 9 achievements."""
    for key in [
        "ACHIEVEMENT_PULL_SHARK",
        "ACHIEVEMENT_PAIR_EXTRAORDINAIRE",
        "ACHIEVEMENT_YOLO",
        "ACHIEVEMENT_STARSTRUCK",
        "ACHIEVEMENT_QUICKDRAW",
        "ACHIEVEMENT_GALAXY_BRAIN",
        "ACHIEVEMENT_PUBLIC_SPONSOR",
        "ACHIEVEMENT_HEART_ON_SLEEVE",
        "ACHIEVEMENT_OPEN_SOURCERER",
    ]:
        assert key in GAMIFICATION_ACTIVITY, f"GamificationActivity {key} missing"


def test_achievement_service_awards_xp() -> None:
    """The service must award XP via GamificationService when achievements are completed."""
    assert "gamificationService" in SERVICE_JAVA
    assert "gamificationService.award" in SERVICE_JAVA


def test_gamification_service_has_achievement_routes() -> None:
    """GamificationService must have route mappings for achievement activities."""
    assert "ACHIEVEMENT_PULL_SHARK" in GAMIFICATION_SERVICE
    assert "ACHIEVEMENT_OPEN_SOURCERER" in GAMIFICATION_SERVICE


# ---------------------------------------------------------------------------
# 5. AchievementResource with verify and claim endpoints (criterion #1, #3, #8)
# ---------------------------------------------------------------------------


def test_resource_has_index_page() -> None:
    """The resource must serve the index page at /achievements."""
    assert "@Path(\"/achievements\")" in RESOURCE_JAVA
    assert "index" in RESOURCE_JAVA


def test_resource_has_verify_endpoint() -> None:
    """The resource must have a /verify endpoint for GitHub API verification."""
    assert "/verify" in RESOURCE_JAVA
    assert "verify" in RESOURCE_JAVA


def test_resource_has_claim_endpoint() -> None:
    """The resource must have a /claim endpoint for self-claiming achievements."""
    assert "/claim" in RESOURCE_JAVA
    assert "claim" in RESOURCE_JAVA


def test_resource_passes_progress_and_leaderboard() -> None:
    """The resource must pass progress, leaderboard, and auth state to the template."""
    assert "leaderboard" in RESOURCE_JAVA
    assert "completedCount" in RESOURCE_JAVA
    assert "userAuthenticated" in RESOURCE_JAVA


# ---------------------------------------------------------------------------
# 6. Template renders all sections (criterion #1, #2, #5, #6)
# ---------------------------------------------------------------------------


def test_template_has_progress_states() -> None:
    """The template must render achievement status (locked/in_progress/completed)."""
    assert "achievements-{achievement.status}" in TEMPLATE
    assert "achievements_status_completed" in TEMPLATE
    assert "achievements_status_in_progress" in TEMPLATE
    assert "achievements_status_locked" in TEMPLATE


def test_template_has_interactive_guides() -> None:
    """The template must render step-by-step guides (criterion #2)."""
    assert "achievements-guide" in TEMPLATE
    assert "achievements-guide-steps" in TEMPLATE
    assert "achievement.steps" in TEMPLATE


def test_template_has_progress_bar() -> None:
    """The template must show a progress bar summary."""
    assert "achievements-progress-summary" in TEMPLATE
    assert "achievements-progress-bar" in TEMPLATE
    assert "completedCount" in TEMPLATE


def test_template_has_verify_button() -> None:
    """The template must have a verify button for authenticated users."""
    assert "/achievements/verify" in TEMPLATE
    assert "achievements_verify_button" in TEMPLATE


def test_template_has_claim_button() -> None:
    """The template must have a claim button for self-claiming achievements."""
    assert "/achievements/claim" in TEMPLATE
    assert "achievements_claim_button" in TEMPLATE


def test_template_has_highlights_section() -> None:
    """The template must have a highlights section (criterion #5)."""
    assert "achievements-highlights" in TEMPLATE
    assert "achievements_highlight_pro_desc" in TEMPLATE
    assert "achievements_highlight_dev_program_desc" in TEMPLATE
    assert "achievements_highlight_security_bounty_desc" in TEMPLATE
    assert "achievements_highlight_galaxy_brain_desc" in TEMPLATE


def test_template_has_leaderboard() -> None:
    """The template must render a leaderboard table (criterion #6)."""
    assert "achievements-leaderboard" in TEMPLATE
    assert "achievements_leaderboard_title" in TEMPLATE
    assert "leaderboard" in TEMPLATE


def test_template_has_org_repos() -> None:
    """The template must list os-santiago org repos."""
    assert "achievements-org" in TEMPLATE
    assert "orgRepos" in TEMPLATE


# ---------------------------------------------------------------------------
# 7. CSS for all UI elements
# ---------------------------------------------------------------------------


def test_css_has_progress_state_styles() -> None:
    """CSS must style locked, in_progress, and completed badges differently."""
    assert ".achievements-badge--locked" in CSS
    assert ".achievements-badge--in_progress" in CSS
    assert ".achievements-badge--completed" in CSS


def test_css_has_progress_bar() -> None:
    """CSS must style the progress bar."""
    assert ".achievements-progress-bar" in CSS
    assert ".achievements-progress-fill" in CSS


def test_css_has_guide_styles() -> None:
    """CSS must style the interactive guides."""
    assert ".achievements-guide" in CSS
    assert ".achievements-guide-steps" in CSS


def test_css_has_leaderboard_styles() -> None:
    """CSS must style the leaderboard table."""
    assert ".achievements-leaderboard-table" in CSS


def test_css_has_highlights_styles() -> None:
    """CSS must style the highlights section."""
    assert ".achievements-highlights-grid" in CSS
    assert ".achievements-highlight-item" in CSS


# ---------------------------------------------------------------------------
# 8. i18n keys in all languages
# ---------------------------------------------------------------------------


def test_i18n_has_all_achievement_keys_english() -> None:
    """All achievement i18n keys must exist in English."""
    required = [
        "achievements_page_title",
        "achievements_heading",
        "achievements_subtitle",
        "achievements_status_locked",
        "achievements_status_in_progress",
        "achievements_status_completed",
        "achievements_how_to_earn",
        "achievements_org_repos_title",
        "achievements_verify_button",
        "achievements_link_github",
        "achievements_claim_button",
        "achievements_self_claimed",
        "achievements_verified",
        "achievements_highlights_title",
        "achievements_highlight_pro_desc",
        "achievements_highlight_dev_program_desc",
        "achievements_highlight_security_bounty_desc",
        "achievements_highlight_galaxy_brain_desc",
        "achievements_leaderboard_title",
        "achievements_leaderboard_empty",
        "achievements_leaderboard_user",
        "achievements_leaderboard_count",
    ]
    for key in required:
        assert f"{key}=" in I18N, f"i18n.properties missing key: {key}"
        assert f"{key}=" in I18N_EN, f"i18n_en.properties missing key: {key}"


def test_i18n_has_all_achievement_keys_spanish() -> None:
    """All achievement i18n keys must exist in Spanish."""
    required = [
        "achievements_page_title",
        "achievements_heading",
        "achievements_status_in_progress",
        "achievements_status_completed",
        "achievements_verify_button",
        "achievements_claim_button",
        "achievements_highlights_title",
        "achievements_leaderboard_title",
    ]
    for key in required:
        assert f"{key}=" in I18N_ES, f"i18n_es.properties missing key: {key}"


def test_app_messages_has_achievement_methods() -> None:
    """AppMessages.java must have methods for all achievement i18n keys."""
    required = [
        "achievements_status_in_progress",
        "achievements_status_completed",
        "achievements_verify_button",
        "achievements_claim_button",
        "achievements_highlights_title",
        "achievements_leaderboard_title",
    ]
    for key in required:
        assert f"String {key}()" in APP_MESSAGES, f"AppMessages.java missing method: {key}"


# ---------------------------------------------------------------------------
# 9. Header navigation link
# ---------------------------------------------------------------------------


def test_header_has_achievements_link() -> None:
    """The header must include a link to the achievements page."""
    assert "/achievements" in HEADER
    assert "nav_achievements" in HEADER
