"""Tests for issue #1043: GitHub Achievement Hub.

Validates that:
1. The AchievementCatalog defines all required achievements with guides
2. The AchievementService can verify achievements and build a leaderboard
3. The AchievementResource serves the /achievements page
4. The AchievementApiResource provides verify and claim endpoints
5. GamificationActivity entries exist for all achievements
6. The template renders the dashboard with all sections
7. The CSS is mobile-responsive
8. The JS handles the claim XP button
9. i18n messages are defined in both languages
10. Documentation exists
"""

from pathlib import Path
import re

ROOT = Path(".")
JAVA_DIR = ROOT / "quarkus-app/src/main/java/com/scanales/homedir"
TEMPLATES_DIR = ROOT / "quarkus-app/src/main/resources/templates"
CSS_DIR = ROOT / "quarkus-app/src/main/resources/META-INF/resources/css"
JS_DIR = ROOT / "quarkus-app/src/main/resources/META-INF/resources/js"
DOCS_DIR = ROOT / "docs"


def test_achievement_catalog_defines_all_achievements() -> None:
    """AchievementCatalog must define all required achievements."""
    catalog = (JAVA_DIR / "achievements/AchievementCatalog.java").read_text()
    required = [
        "pull-shark", "yolo", "quickdraw", "pair-extraordinaire",
        "starstruck", "galaxy-brain", "public-sponsor",
        "heart-on-your-sleeve", "open-sourcerer",
    ]
    for key in required:
        assert key in catalog, f"AchievementCatalog missing achievement: {key}"


def test_achievement_catalog_has_bilingual_descriptions() -> None:
    """Each achievement must have English and Spanish descriptions."""
    catalog = (JAVA_DIR / "achievements/AchievementCatalog.java").read_text()
    assert "descriptionEs" in catalog, "Catalog must have Spanish descriptions"
    assert "stepsEs" in catalog, "Catalog must have Spanish steps"


def test_achievement_catalog_has_org_repos() -> None:
    """Catalog must define os-santiago org repos."""
    catalog = (JAVA_DIR / "achievements/AchievementCatalog.java").read_text()
    assert "os-santiago" in catalog, "Catalog must reference os-santiago repos"
    assert "OrgRepo" in catalog, "Catalog must define OrgRepo records"


def test_achievement_service_verifies_via_github_api() -> None:
    """AchievementService must verify achievements via GitHub API."""
    service = (JAVA_DIR / "achievements/AchievementService.java").read_text()
    assert "api.github.com" in service, "Service must query GitHub API"
    assert "countSearchResults" in service, "Service must count search results"
    assert "verifySingleAchievement" in service, "Service must verify individual achievements"
    assert "verifyAchievements" in service, "Service must verify all achievements"


def test_achievement_service_builds_leaderboard() -> None:
    """AchievementService must build a leaderboard."""
    service = (JAVA_DIR / "achievements/AchievementService.java").read_text()
    assert "buildLeaderboard" in service, "Service must build leaderboard"
    assert "LeaderboardEntry" in service, "Service must define LeaderboardEntry"
    assert "unlockedCount" in service, "Leaderboard must track unlocked count"


def test_achievement_service_awards_xp() -> None:
    """AchievementService must award XP via GamificationService."""
    service = (JAVA_DIR / "achievements/AchievementService.java").read_text()
    assert "awardAchievementXp" in service, "Service must have awardAchievementXp method"
    assert "gamificationService.award" in service, "Service must call gamificationService.award"
    assert "verifySingleAchievementCached" in service, \
        "Service must route single-achievement verification through the cache"
    assert "cached == null || cached.isExpired()" in service, \
        "cache guard must treat expired entries as misses"
    assert "login == null || login.isBlank()" in service, \
        "cache guard must not NPE on a null login"


def test_verification_messages_are_localized() -> None:
    """Verification response messages must come from AppMessages, not hardcoded
    English strings."""
    service = (JAVA_DIR / "achievements/AchievementService.java").read_text()
    messages = (JAVA_DIR / "config/AppMessages.java").read_text()
    assert '"Achievement unlocked!"' not in service, \
        "unlocked message must not be hardcoded in the service"
    assert '"Verification unavailable"' not in service, \
        "unavailable message must not be hardcoded in the service"
    for key in (
        "achievements_verification_unlocked",
        "achievements_verification_progress",
        "achievements_verification_unavailable",
    ):
        assert key in messages, f"AppMessages must declare {key}"


def test_yolo_query_uses_review_none() -> None:
    """The yolo query must use review:none (is:merged and is:unmerged are
    mutually exclusive GitHub search qualifiers)."""
    service = (JAVA_DIR / "achievements/AchievementService.java").read_text()
    assert "review:none" in service, "yolo query must use review:none"
    assert "is:merged is:unmerged" not in service, \
        "yolo query must not combine mutually exclusive is:merged is:unmerged"


def test_quickdraw_enforces_five_minute_window() -> None:
    """Quickdraw must filter closed issues/PRs to the 5-minute window using
    seconds (Duration.toMinutes truncates, counting 5:59 as 5 minutes)."""
    service = (JAVA_DIR / "achievements/AchievementService.java").read_text()
    assert "countQuickdraw" in service, "Service must have countQuickdraw"
    assert "Duration.between" in service, \
        "Quickdraw must compute created/closed duration server-side"
    assert ".toSeconds()" in service and "<= 300" in service, \
        "Quickdraw must compare seconds against 300, not truncating minutes"
    assert "type:issue is:closed" not in service, \
        "Quickdraw must include pull requests (no type:issue restriction)"
    assert "closed:>=2024-01-01" not in service, \
        "Quickdraw must not count every issue closed after a fixed date"


def test_pair_extraordinaire_uses_commit_search() -> None:
    """Pair Extraordinaire must use the Commit search API since GitHub Issues
    search does not index co-authored-by: trailers, and must not treat
    co-authored-by: as a non-existent search qualifier."""
    service = (JAVA_DIR / "achievements/AchievementService.java").read_text()
    assert "countCoAuthoredCommits" in service, \
        "Service must have countCoAuthoredCommits"
    assert "/search/commits" in service, \
        "Pair Extraordinaire must use the Commit search API"
    assert "co-authored-by:" + " " not in service.replace('"co-authored-by:"', ''), \
        "must not build a co-authored-by: qualifier; free-text search is required"
    assert "org:os-santiago" in service, \
        "commit search must use the org: qualifier (repo: requires owner/name)"


def test_starstruck_link_header_parsing_is_correct() -> None:
    """Starstruck pagination must parse the page query parameter at a query
    boundary, not the page= suffix inside per_page."""
    service = (JAVA_DIR / "achievements/AchievementService.java").read_text()
    assert 'rel=\\"last\\"' in service, "Service must parse the rel=last link header"
    assert "page=(\\\\d+)" in service, \
        "Service must parse the page number at a query boundary"
    assert "part.indexOf(\"page=\") + 6" not in service, \
        "Service must not parse page= at the wrong offset"


def test_leaderboard_sorts_unlocked_desc_then_xp_desc() -> None:
    """Leaderboard must sort by unlocked count descending, then total XP
    descending, without a second reversed() flipping the whole comparator."""
    service = (JAVA_DIR / "achievements/AchievementService.java").read_text()
    assert "Comparator.comparingInt(LeaderboardEntry::totalXp).reversed()" in service, \
        "totalXp must be reversed independently"
    assert ".thenComparingInt(LeaderboardEntry::totalXp)\n            .reversed()" not in service, \
        "the cumulative comparator must not be reversed a second time"


def test_achievement_resource_serves_page() -> None:
    """AchievementResource must serve the /achievements page."""
    resource = (JAVA_DIR / "public_/AchievementResource.java").read_text()
    assert '@Path("/achievements")' in resource, "Resource must be at /achievements"
    assert "@PermitAll" in resource, "Page must be publicly accessible"
    assert "AchievementService" in resource, "Resource must use AchievementService"
    assert "activePage" in resource, "Resource must set activePage"


def test_achievement_api_resource_has_verify_and_claim() -> None:
    """AchievementApiResource must have verify and claim endpoints."""
    resource = (JAVA_DIR / "public_/AchievementApiResource.java").read_text()
    assert '@Path("/api/achievements")' in resource, "API must be at /api/achievements"
    assert "/verify/" in resource, "API must have verify endpoint"
    assert "/claim/" in resource, "API must have claim endpoint"


def test_claim_endpoint_uses_post() -> None:
    """Claiming an achievement must be a POST request (awarding XP via GET is
    non-idempotent and can be triggered by link prefetching)."""
    resource = (JAVA_DIR / "public_/AchievementApiResource.java").read_text()
    assert re.search(
        r"@POST\s*\n\s*@Path\(\"/claim/", resource
    ), "claim endpoint must be annotated @POST"


def test_api_error_messages_are_localized() -> None:
    """API error/success messages must come from AppMessages, not hardcoded
    English strings."""
    resource = (JAVA_DIR / "public_/AchievementApiResource.java").read_text()
    assert "achievements_api_no_github" in resource, \
        "no-github error must use AppMessages"
    assert "achievements_api_award_success" in resource, \
        "award-success message must use AppMessages"
    assert "achievements_api_award_failure" in resource, \
        "award-failure message must use AppMessages"


def test_js_claim_uses_post() -> None:
    """The claim XP fetch must use method POST."""
    js = (JS_DIR / "achievements.js").read_text()
    assert re.search(
        r'fetch\("/api/achievements/claim/.*?method:\s*"POST"',
        js,
        re.DOTALL,
    ), "claim fetch must use POST method"


def test_template_guards_user_snapshot_in_claim_block() -> None:
    """The claim-button block must guard on userSnapshot to avoid a Qute
    rendering error when GitHub verification fails."""
    template = (TEMPLATES_DIR / "AchievementResource/index.html").read_text()
    assert "userAuthenticated && githubLinked && userSnapshot" in template, \
        "claim block must guard on userSnapshot"


def test_gamification_activity_has_achievement_entries() -> None:
    """GamificationActivity must have entries for all achievements."""
    activity = (JAVA_DIR / "model/GamificationActivity.java").read_text()
    required = [
        "ACHIEVEMENT_PULL_SHARK",
        "ACHIEVEMENT_YOLO",
        "ACHIEVEMENT_QUICKDRAW",
        "ACHIEVEMENT_PAIR_EXTRAORDINAIRE",
        "ACHIEVEMENT_STARSTRUCK",
        "ACHIEVEMENT_GALAXY_BRAIN",
        "ACHIEVEMENT_PUBLIC_SPONSOR",
        "ACHIEVEMENT_HEART_ON_SLEEVE",
        "ACHIEVEMENT_OPEN_SOURCERER",
    ]
    for name in required:
        assert name in activity, f"GamificationActivity missing: {name}"


def test_template_renders_dashboard() -> None:
    """Template must render the achievement dashboard with all sections."""
    template = (TEMPLATES_DIR / "AchievementResource/index.html").read_text()
    assert "achievements-heading" in template or "achievements_heading" in template
    assert "achievements-grid" in template, "Template must have achievements grid"
    assert "achievement-card" in template, "Template must have achievement cards"
    assert "achievements-leaderboard" in template, "Template must have leaderboard"
    assert "achievements-org-repos" in template, "Template must have org repos section"
    assert "achievements-highlights" in template, "Template must have highlights section"
    assert "achievement-guide" in template, "Template must have interactive guides"
    assert "achievement-claim-btn" in template, "Template must have claim XP buttons"


def test_template_has_progress_bar() -> None:
    """Template must show user progress overview."""
    template = (TEMPLATES_DIR / "AchievementResource/index.html").read_text()
    assert "achievements-progress-overview" in template or "achievement-progress" in template


def test_template_has_link_github_prompt() -> None:
    """Template must prompt users to link GitHub if not linked."""
    template = (TEMPLATES_DIR / "AchievementResource/index.html").read_text()
    assert "githubLinked" in template, "Template must check githubLinked"
    assert "/private/github/start" in template, "Template must have GitHub link URL"


def test_css_is_mobile_responsive() -> None:
    """CSS must be mobile-responsive."""
    css = (CSS_DIR / "achievements.css").read_text()
    assert "@media" in css, "CSS must have media queries"
    assert "max-width" in css, "CSS must have max-width breakpoints"
    assert "grid-template-columns" in css, "CSS must use CSS grid"


def test_css_uses_design_system_tokens() -> None:
    """CSS must use the HomeDir design system tokens."""
    css = (CSS_DIR / "achievements.css").read_text()
    assert "--font-display" in css or "--font-body" in css, \
        "CSS must use design system font tokens"
    assert "--color-accent" in css or "--white-rgb" in css or "--black-rgb" in css, \
        "CSS must use design system color tokens"


def test_js_handles_claim_button() -> None:
    """JS must handle the claim XP button."""
    js = (JS_DIR / "achievements.js").read_text()
    assert "achievement-claim-btn" in js, "JS must target claim button"
    assert "/api/achievements/claim/" in js, "JS must call claim API"
    assert "is-loading" in js, "JS must use is-loading class (issue #1022 pattern)"
    assert "addEventListener" in js, "JS must attach click listener"


def test_i18n_messages_defined() -> None:
    """i18n messages must be defined in AppMessages.java."""
    messages = (JAVA_DIR / "config/AppMessages.java").read_text()
    required = [
        "nav_achievements",
        "achievements_page_title",
        "achievements_heading",
        "achievements_subtitle",
        "achievements_status_unlocked",
        "achievements_status_locked",
        "achievements_claim_xp",
        "achievements_leaderboard_title",
    ]
    for key in required:
        assert key in messages, f"AppMessages missing: {key}"


def test_i18n_spanish_translations_exist() -> None:
    """Spanish translations must exist in i18n_es.properties."""
    props = (ROOT / "quarkus-app/src/main/resources/i18n_es.properties").read_text()
    assert "nav_achievements=" in props, "Spanish nav_achievements missing"
    assert "achievements_heading=" in props, "Spanish achievements_heading missing"
    assert "achievements_claim_xp=" in props, "Spanish achievements_claim_xp missing"


def test_nav_link_added_to_header() -> None:
    """Header must have a nav link to /achievements."""
    header = (TEMPLATES_DIR / "fragments/header.html").read_text()
    assert '/achievements' in header, "Header must link to /achievements"
    assert "nav_achievements" in header, "Header must use i18n key for achievements nav"


def test_documentation_exists() -> None:
    """Documentation must exist in docs/."""
    doc = DOCS_DIR / "en/features/github-achievement-hub.md"
    assert doc.exists(), "Documentation file must exist"
    content = doc.read_text()
    assert "How to Add New Achievements" in content, \
        "Docs must explain how to add new achievements"
    assert "AchievementCatalog" in content, "Docs must reference AchievementCatalog"
    assert "GamificationActivity" in content, "Docs must reference GamificationActivity"
