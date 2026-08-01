"""Static assertions for issue #1043: GitHub Achievement Hub.

Verifies that the achievement hub implementation includes:
- A /achievements route with a Qute template
- Real GitHub API verification (not just a static dashboard)
- Gamification XP awards for completed achievements
- Progress tracking (locked / in-progress / completed)
- A leaderboard section
- i18n keys in EN and ES
- A nav link in the header
"""

from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "quarkus-app/src/main/java/com/scanales/homedir"
RESOURCES = ROOT / "quarkus-app/src/main/resources"
TEMPLATES = RESOURCES / "templates"


def test_achievement_resource_exists():
    """The /achievements route must be served by AchievementResource."""
    resource = (JAVA / "public_/AchievementResource.java").read_text()
    assert '@Path("/achievements")' in resource
    assert "@PermitAll" in resource
    assert "@Authenticated" in resource
    assert "/api/verify" in resource


def test_verify_endpoint_uses_post_not_get():
    """The /api/verify endpoint awards XP (state-changing) and must use POST,
    not GET, to prevent CSRF attacks via <img> tags or crafted links."""
    resource = (JAVA / "public_/AchievementResource.java").read_text()
    # Find the verify method and assert it uses @POST
    assert "@POST" in resource
    verify_section = resource[resource.index("/api/verify"):]
    assert "@POST" in resource[:resource.index("/api/verify") + 200]
    # The verify endpoint must NOT use @GET
    verify_method_start = resource.index("@POST")
    verify_method_end = resource.index("}", resource.index("public Response verify()"))
    verify_method = resource[verify_method_start:verify_method_end + 1]
    assert "@GET" not in verify_method, (
        "The /api/verify endpoint must not use @GET — it awards XP and is "
        "vulnerable to CSRF via GET. Use @POST instead."
    )


def test_verify_endpoint_no_exception_leak():
    """The verify endpoint's error response must not leak internal exception
    messages to clients. Log statements may use e.getMessage() but the
    client-facing JSON response must use a generic message."""
    resource = (JAVA / "public_/AchievementResource.java").read_text()
    # Extract only the verify method body (between public Response verify and the next method)
    verify_start = resource.index("public Response verify()")
    verify_end = resource.index("}", resource.index("return Response.serverError()", verify_start)) + 1
    verify_body = resource[verify_start:verify_end]
    assert "e.getMessage()" not in verify_body, (
        "Exception messages must not be included in client-facing error "
        "responses — they can leak internal implementation details."
    )
    assert "Please try again later" in verify_body


def test_leaderboard_gated_behind_authentication():
    """The leaderboard must not be built for anonymous users to avoid exposing
    all community members' GitHub identities on a @PermitAll page."""
    resource = (JAVA / "public_/AchievementResource.java").read_text()
    assert "authenticated ? buildLeaderboard()" in resource
    assert "List.of()" in resource


def test_achievement_service_does_real_verification():
    """AchievementService must call the GitHub API (not just return static data)."""
    service = (JAVA / "achievements/AchievementService.java").read_text()
    assert "api.github.com/search/issues" in service
    assert "api.github.com/repos/" in service
    assert "MERGED_PRS" in service
    assert "COAUTHORED_PRS" in service
    assert "REPO_STARS" in service


def test_achievement_service_awards_xp():
    """The service must award XP via GamificationService when an achievement is completed."""
    service = (JAVA / "achievements/AchievementService.java").read_text()
    assert "gamificationService.award" in service
    assert "ACHIEVEMENT_PULL_SHARK" in service
    assert "newlyCompleted" in service


def test_achievement_service_caches_results():
    """Results must be cached per GitHub login with a TTL (issue #1043 requirement)."""
    service = (JAVA / "achievements/AchievementService.java").read_text()
    assert "ConcurrentHashMap" in service
    assert "CachedVerification" in service
    assert "getCachedProgress" in service
    assert "Duration.ofHours" in service


def test_achievement_catalog_has_verification_types():
    """Each achievement must have a VerificationType for measuring progress."""
    catalog = (JAVA / "achievements/AchievementCatalog.java").read_text()
    achievement = (JAVA / "achievements/Achievement.java").read_text()
    assert "VerificationType" in achievement
    assert "MERGED_PRS" in catalog
    assert "COAUTHORED_PRS" in catalog
    assert "MERGED_PRS_NO_REVIEW" in catalog
    assert "REPO_STARS" in catalog
    assert "MANUAL_ONLY" in catalog


def test_achievement_progress_tracks_status():
    """AchievementProgress must track locked/in-progress/completed states."""
    progress = (JAVA / "achievements/AchievementProgress.java").read_text()
    assert "LOCKED" in progress
    assert "IN_PROGRESS" in progress
    assert "COMPLETED" in progress
    assert "fromCount" in progress
    assert "percent" in progress


def test_gamification_activities_for_achievements():
    """GamificationActivity must include XP awards for each achievement."""
    activities = (JAVA / "model/GamificationActivity.java").read_text()
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
        assert key in activities, f"Missing gamification activity: {key}"


def test_template_has_progress_bars_and_verify_button():
    """The template must render progress bars, a verify button, and a leaderboard."""
    template = (TEMPLATES / "AchievementResource/index.html").read_text()
    assert "achievements-progress-bar" in template
    assert "achievements-progress-fill" in template
    assert "verifyAchievements" in template
    assert "data-verify-url" in template
    assert "leaderboard" in template
    assert "progressMap" in template


def test_template_has_bilingual_descriptions():
    """The template must switch between EN and ES descriptions based on locale."""
    template = (TEMPLATES / "AchievementResource/index.html").read_text()
    assert "resolvedLocaleCode == 'en'" in template
    assert "descriptionEs" in template


def test_nav_link_in_header():
    """The header must include a nav link to /achievements."""
    header = (TEMPLATES / "fragments/header.html").read_text()
    assert 'href="/achievements"' in header
    assert "nav_achievements" in header


def test_i18n_keys_present_in_all_locales():
    """Achievement i18n keys must be present in EN and ES property files."""
    required_keys = [
        "achievements_page_title",
        "achievements_heading",
        "achievements_subtitle",
        "achievements_verify_button",
        "achievements_status_locked",
        "achievements_status_in_progress",
        "achievements_status_completed",
        "achievements_how_to_earn",
        "achievements_org_repos_title",
        "achievements_leaderboard_title",
        "nav_achievements",
    ]
    for prop_file in ["i18n.properties", "i18n_en.properties", "i18n_es.properties"]:
        content = (RESOURCES / prop_file).read_text()
        for key in required_keys:
            assert key in content, f"Missing key {key} in {prop_file}"


def test_appmessages_has_achievement_methods():
    """AppMessages.java must declare @Message methods for all achievement keys."""
    messages = (JAVA / "config/AppMessages.java").read_text()
    for method in [
        "nav_achievements",
        "achievements_page_title",
        "achievements_heading",
        "achievements_subtitle",
        "achievements_verify_button",
        "achievements_link_github",
        "achievements_status_locked",
        "achievements_status_in_progress",
        "achievements_status_completed",
        "achievements_how_to_earn",
        "achievements_org_repos_title",
        "achievements_leaderboard_title",
    ]:
        assert f"String {method}()" in messages, f"Missing AppMessages method: {method}"


def test_achievements_css_exists():
    """The achievements CSS file must exist with progress bar and badge styles."""
    css = (RESOURCES / "META-INF/resources/css/achievements.css").read_text()
    assert "achievements-progress-bar" in css
    assert "achievements-badge--completed" in css
    assert "achievements-badge--in_progress" in css
    assert "achievements-leaderboard" in css


def test_achievements_js_exists():
    """The achievements JS must wire up the verify button with a POST request."""
    js = (RESOURCES / "META-INF/resources/js/achievements.js").read_text()
    assert "verifyAchievements" in js
    assert "data-verify-url" in js
    assert "fetch" in js
    assert "method: 'POST'" in js or 'method: "POST"' in js
    assert "credentials: 'same-origin'" in js


# ---------------------------------------------------------------------------
# Security: GitHub login sanitization (review feedback)
# ---------------------------------------------------------------------------


def test_achievement_service_sanitizes_github_login():
    """AchievementService must validate the GitHub login against a username
    pattern before interpolating it into GitHub Search API queries. This
    prevents query injection via malformed stored login values."""
    service = (JAVA / "achievements/AchievementService.java").read_text()
    assert "GITHUB_LOGIN_PATTERN" in service
    assert "Pattern" in service
    assert "matcher(githubLogin).matches()" in service
    assert "achievement_verify_rejected_invalid_login" in service


def test_achievement_service_no_dead_cache_ttl_method():
    """The dead cacheTtl() method (never called) must be removed.
    CachedVerification.isExpired() uses DEFAULT_CACHE_TTL directly."""
    service = (JAVA / "achievements/AchievementService.java").read_text()
    assert "private Duration cacheTtl()" not in service


def test_achievement_service_no_unused_userprofileservice_injection():
    """UserProfileService must not be injected into AchievementService if it
    is not used there (it is used in AchievementResource instead)."""
    service = (JAVA / "achievements/AchievementService.java").read_text()
    assert "UserProfileService" not in service
