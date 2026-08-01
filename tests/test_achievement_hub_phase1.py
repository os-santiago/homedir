"""Static assertions for issue #1043: GitHub Achievement Hub (Phase 1).

Phase 1 is a read-only catalog page at /achievements that displays the 9 GitHub
achievements with bilingual step-by-step guides, the os-santiago repositories
that help earn each one, and a highlights section. Per-user progress tracking,
GitHub API verification, XP awards, and the community leaderboard are deferred
to Phase 2+.
"""

from pathlib import Path

CATALOG_JAVA = Path(
    "quarkus-app/src/main/java/com/scanales/homedir/achievements/AchievementCatalog.java"
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
# 2. AchievementView is read-only (Phase 1 scope)
# ---------------------------------------------------------------------------


def test_achievement_view_is_read_only() -> None:
    """AchievementView must not reference AchievementState or progress tracking."""
    assert "AchievementState" not in VIEW_JAVA
    assert "status" not in VIEW_JAVA
    assert "progressCount" not in VIEW_JAVA
    assert "verifiedVia" not in VIEW_JAVA


def test_achievement_view_has_locale_aware_from() -> None:
    """AchievementView.from must accept a guide and locale for bilingual display."""
    assert "from(AchievementGuide guide, String locale)" in VIEW_JAVA


# ---------------------------------------------------------------------------
# 3. AchievementResource is read-only (Phase 1 scope — no verify/claim)
# ---------------------------------------------------------------------------


def test_resource_has_index_page() -> None:
    """The resource must serve the index page at /achievements."""
    assert '@Path("/achievements")' in RESOURCE_JAVA
    assert "index" in RESOURCE_JAVA


def test_resource_has_no_verify_endpoint() -> None:
    """Phase 1 must NOT include a /verify endpoint (deferred to Phase 2)."""
    assert "/verify" not in RESOURCE_JAVA
    assert "verifyAchievements" not in RESOURCE_JAVA


def test_resource_has_no_claim_endpoint() -> None:
    """Phase 1 must NOT include a /claim endpoint (deferred to Phase 2)."""
    assert "/claim" not in RESOURCE_JAVA
    assert "selfClaim" not in RESOURCE_JAVA


def test_resource_has_no_leaderboard() -> None:
    """Phase 1 must NOT include leaderboard data (deferred to Phase 2)."""
    assert "getLeaderboard" not in RESOURCE_JAVA
    assert "LeaderboardEntry" not in RESOURCE_JAVA


def test_resource_has_no_progress_tracking() -> None:
    """Phase 1 must NOT reference AchievementProgress or AchievementService."""
    assert "AchievementProgress" not in RESOURCE_JAVA
    assert "AchievementService" not in RESOURCE_JAVA
    assert "completedCount" not in RESOURCE_JAVA
    assert "userAuthenticated" not in RESOURCE_JAVA


# ---------------------------------------------------------------------------
# 4. Template renders catalog, guides, org repos, and highlights only
# ---------------------------------------------------------------------------


def test_template_has_correct_head_section() -> None:
    """The template must use {/head} (not the broken {/#head})."""
    assert "{/#head}" not in TEMPLATE
    assert "{/head}" in TEMPLATE


def test_template_has_interactive_guides() -> None:
    """The template must render step-by-step guides (criterion #2)."""
    assert "achievements-guide" in TEMPLATE
    assert "achievements-guide-steps" in TEMPLATE
    assert "achievement.steps" in TEMPLATE


def test_template_has_locked_badge() -> None:
    """The template must show a 'locked' badge for each achievement (Phase 1)."""
    assert "achievements-badge--locked" in TEMPLATE
    assert "achievements_status_locked" in TEMPLATE


def test_template_has_no_progress_bar() -> None:
    """Phase 1 must NOT show a progress bar (no per-user progress)."""
    assert "achievements-progress-summary" not in TEMPLATE
    assert "completedCount" not in TEMPLATE


def test_template_has_no_verify_button() -> None:
    """Phase 1 must NOT have a verify button (deferred to Phase 2)."""
    assert "/achievements/verify" not in TEMPLATE
    assert "achievements_verify_button" not in TEMPLATE


def test_template_has_no_claim_button() -> None:
    """Phase 1 must NOT have a claim button (deferred to Phase 2)."""
    assert "/achievements/claim" not in TEMPLATE
    assert "achievements_claim_button" not in TEMPLATE


def test_template_has_no_leaderboard() -> None:
    """Phase 1 must NOT render a leaderboard (deferred to Phase 2)."""
    assert "achievements-leaderboard" not in TEMPLATE
    assert "achievements_leaderboard_title" not in TEMPLATE
    assert "leaderboard" not in TEMPLATE


def test_template_has_highlights_section() -> None:
    """The template must have a highlights section (criterion #5)."""
    assert "achievements-highlights" in TEMPLATE
    assert "achievements_highlight_pro_title" in TEMPLATE
    assert "achievements_highlight_pro_desc" in TEMPLATE
    assert "achievements_highlight_dev_program_title" in TEMPLATE
    assert "achievements_highlight_dev_program_desc" in TEMPLATE
    assert "achievements_highlight_security_bounty_title" in TEMPLATE
    assert "achievements_highlight_security_bounty_desc" in TEMPLATE
    assert "achievements_highlight_galaxy_brain_title" in TEMPLATE
    assert "achievements_highlight_galaxy_brain_desc" in TEMPLATE


def test_template_has_org_repos() -> None:
    """The template must list os-santiago org repos."""
    assert "achievements-org" in TEMPLATE
    assert "orgRepos" in TEMPLATE


def test_template_external_links_are_safe() -> None:
    """External links must use rel=noopener noreferrer."""
    assert 'rel="noopener noreferrer"' in TEMPLATE


def test_template_doc_link_uses_dedicated_i18n_key() -> None:
    """The external documentation link must use achievements_read_docs (not
    achievements_how_to_earn, which is the guide heading)."""
    assert "achievements_read_docs" in TEMPLATE
    assert "achievements-doc-link" in TEMPLATE


# ---------------------------------------------------------------------------
# 5. CSS for Phase 1 UI elements only
# ---------------------------------------------------------------------------


def test_css_has_locked_badge_style() -> None:
    """CSS must style the locked badge."""
    assert ".achievements-badge--locked" in CSS


def test_css_has_no_progress_bar() -> None:
    """Phase 1 CSS must NOT include progress bar styles."""
    assert ".achievements-progress-bar" not in CSS
    assert ".achievements-progress-fill" not in CSS


def test_css_has_no_leaderboard_styles() -> None:
    """Phase 1 CSS must NOT include leaderboard styles."""
    assert ".achievements-leaderboard-table" not in CSS


def test_css_has_guide_styles() -> None:
    """CSS must style the interactive guides."""
    assert ".achievements-guide" in CSS
    assert ".achievements-guide-steps" in CSS


def test_css_has_highlights_styles() -> None:
    """CSS must style the highlights section."""
    assert ".achievements-highlights-grid" in CSS
    assert ".achievements-highlight-item" in CSS


# ---------------------------------------------------------------------------
# 6. i18n keys in all languages (Phase 1 only)
# ---------------------------------------------------------------------------


def test_i18n_has_phase1_keys_english() -> None:
    """All Phase 1 achievement i18n keys must exist in English."""
    required = [
        "achievements_page_title",
        "achievements_heading",
        "achievements_subtitle",
        "achievements_status_locked",
        "achievements_how_to_earn",
        "achievements_read_docs",
        "achievements_org_repos_title",
        "achievements_highlights_title",
        "achievements_highlight_pro_title",
        "achievements_highlight_pro_desc",
        "achievements_highlight_dev_program_title",
        "achievements_highlight_dev_program_desc",
        "achievements_highlight_security_bounty_title",
        "achievements_highlight_security_bounty_desc",
        "achievements_highlight_galaxy_brain_title",
        "achievements_highlight_galaxy_brain_desc",
    ]
    for key in required:
        assert f"{key}=" in I18N, f"i18n.properties missing key: {key}"
        assert f"{key}=" in I18N_EN, f"i18n_en.properties missing key: {key}"


def test_i18n_has_phase1_keys_spanish() -> None:
    """All Phase 1 achievement i18n keys must exist in Spanish."""
    required = [
        "achievements_page_title",
        "achievements_heading",
        "achievements_subtitle",
        "achievements_status_locked",
        "achievements_how_to_earn",
        "achievements_read_docs",
        "achievements_org_repos_title",
        "achievements_highlights_title",
        "achievements_highlight_pro_title",
        "achievements_highlight_pro_desc",
        "achievements_highlight_dev_program_title",
        "achievements_highlight_dev_program_desc",
        "achievements_highlight_security_bounty_title",
        "achievements_highlight_security_bounty_desc",
        "achievements_highlight_galaxy_brain_title",
        "achievements_highlight_galaxy_brain_desc",
    ]
    for key in required:
        assert f"{key}=" in I18N_ES, f"i18n_es.properties missing key: {key}"


def test_i18n_does_not_have_phase2_keys() -> None:
    """Phase 2 keys (verify, claim, leaderboard, progress) must NOT be present."""
    phase2_keys = [
        "achievements_status_in_progress",
        "achievements_status_completed",
        "achievements_verify_button",
        "achievements_link_github",
        "achievements_claim_button",
        "achievements_self_claimed",
        "achievements_verified",
        "achievements_leaderboard_title",
        "achievements_leaderboard_empty",
        "achievements_leaderboard_user",
        "achievements_leaderboard_count",
    ]
    for key in phase2_keys:
        assert f"{key}=" not in I18N, f"Phase 2 key '{key}' should not be in i18n.properties"
        assert f"{key}=" not in I18N_EN, f"Phase 2 key '{key}' should not be in i18n_en.properties"
        assert f"{key}=" not in I18N_ES, f"Phase 2 key '{key}' should not be in i18n_es.properties"


def test_app_messages_has_phase1_methods() -> None:
    """AppMessages.java must have methods for Phase 1 achievement i18n keys."""
    required = [
        "achievements_page_title",
        "achievements_heading",
        "achievements_subtitle",
        "achievements_status_locked",
        "achievements_how_to_earn",
        "achievements_read_docs",
        "achievements_org_repos_title",
        "achievements_highlights_title",
        "achievements_highlight_pro_title",
        "achievements_highlight_pro_desc",
        "achievements_highlight_dev_program_title",
        "achievements_highlight_dev_program_desc",
        "achievements_highlight_security_bounty_title",
        "achievements_highlight_security_bounty_desc",
        "achievements_highlight_galaxy_brain_title",
        "achievements_highlight_galaxy_brain_desc",
    ]
    for key in required:
        assert f"String {key}()" in APP_MESSAGES, f"AppMessages.java missing method: {key}"


def test_app_messages_does_not_have_phase2_methods() -> None:
    """AppMessages.java must NOT have Phase 2 methods."""
    phase2_methods = [
        "achievements_status_in_progress",
        "achievements_status_completed",
        "achievements_verify_button",
        "achievements_link_github",
        "achievements_claim_button",
        "achievements_self_claimed",
        "achievements_verified",
        "achievements_leaderboard_title",
        "achievements_leaderboard_empty",
        "achievements_leaderboard_user",
        "achievements_leaderboard_count",
    ]
    for key in phase2_methods:
        assert f"String {key}()" not in APP_MESSAGES, (
            f"Phase 2 method '{key}' should not be in AppMessages.java"
        )


# ---------------------------------------------------------------------------
# 7. Header navigation link
# ---------------------------------------------------------------------------


def test_header_has_achievements_link() -> None:
    """The header must include a link to the achievements page."""
    assert "/achievements" in HEADER
    assert "nav_achievements" in HEADER
