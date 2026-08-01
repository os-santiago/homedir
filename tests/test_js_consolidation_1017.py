"""Tests for issue #1017: JS consolidation — shared utilities, consistent IIFE
pattern, and deduplication of escapeHtml/escapeText across loaded JS files.

Validates that:
1. utils.js exposes escapeHtml, escapeAttr, formatDate, formatDateTime
2. admin-notifications-sim.js uses IIFE pattern (not DOMContentLoaded) with
   root-element guard
3. admin-notifications.js has root-element guard
4. community-bundle.js escapeText (the HTML-escaping variant) delegates to
   HomeDirUtils.escapeHtml
5. Dead JS files (homedir.js, app.js, community-content.js, etc.) are not
   referenced by any template
"""

from pathlib import Path
import re

JS_DIR = Path("quarkus-app/src/main/resources/META-INF/resources/js")
TEMPLATE_DIR = Path("quarkus-app/src/main/resources/templates")


def _read_js(name: str) -> str:
    """Read a JavaScript source file from the JS resource directory."""
    return (JS_DIR / name).read_text()


def test_utils_exposes_shared_utilities() -> None:
    """utils.js must expose escapeHtml, escapeAttr, formatDate, formatDateTime."""
    utils = _read_js("utils.js")
    assert "escapeHtml" in utils
    assert "escapeAttr" in utils
    assert "formatDate" in utils
    assert "formatDateTime" in utils
    assert "window.HomeDirUtils" in utils
    # All four should be in the exposed object
    assert re.search(r"window\.HomeDirUtils\s*=\s*\{.*escapeHtml.*escapeAttr.*formatDate.*formatDateTime.*\}", utils, re.DOTALL)


def test_admin_notifications_sim_uses_iife_with_guard() -> None:
    """admin-notifications-sim.js must use IIFE pattern with root-element guard,
    not DOMContentLoaded (redundant with defer)."""
    sim = _read_js("admin-notifications-sim.js")
    stripped = sim.strip()
    assert stripped.startswith("(function") or stripped.startswith("(()"), \
        "admin-notifications-sim.js must start with IIFE"
    # DOMContentLoaded must not be used as an event listener (comments are OK)
    assert "addEventListener('DOMContentLoaded'" not in sim and \
           'addEventListener("DOMContentLoaded"' not in sim, \
        "admin-notifications-sim.js must not use DOMContentLoaded event listener (defer makes it redundant)"
    # Must have root-element guard
    assert "if (!eventId" in sim or "if (!eventId ||" in sim, \
        "admin-notifications-sim.js must guard against missing root elements"


def test_admin_notifications_has_root_guard() -> None:
    """admin-notifications.js must have a root-element guard."""
    admin = _read_js("admin-notifications.js")
    assert "if (!listEl)" in admin, \
        "admin-notifications.js must guard against missing #admin-list element"


def test_community_bundle_escape_text_delegates_to_homedirutils() -> None:
    """The HTML-escaping escapeText in community-bundle.js must delegate to
    HomeDirUtils.escapeHtml (first IIFE section, used in innerHTML templates)."""
    bundle = _read_js("community-bundle.js")
    # The first escapeText (line ~67) is the one used in innerHTML — it must
    # delegate to HomeDirUtils.escapeHtml
    first_escape = re.search(
        r'function escapeText\(value\)\s*\{.*?return "";',
        bundle,
        re.DOTALL,
    )
    assert first_escape, "First escapeText function not found"
    # Find the full function body
    first_func = re.search(
        r'function escapeText\(value\)\s*\{(.*?)\n  \}',
        bundle,
        re.DOTALL,
    )
    assert first_func, "First escapeText function body not found"
    body = first_func.group(1)
    assert "HomeDirUtils" in body and "escapeHtml" in body, \
        "First escapeText must delegate to HomeDirUtils.escapeHtml"


def test_dead_js_files_not_referenced_by_templates() -> None:
    """Dead JS files must not be referenced by any template."""
    dead_files = [
        "homedir.js",
        "app.js",
        "community-content.js",
        "community-submissions.js",
        "home-lightning.js",
        "home-lta-preview.js",
    ]
    for template_file in TEMPLATE_DIR.rglob("*.html"):
        content = template_file.read_text()
        for dead in dead_files:
            # Check for script src references (not just mentions in comments)
            assert f'src="/js/{dead}' not in content, \
                f"{template_file} references dead JS file {dead}"


def test_all_loaded_scripts_use_iife_or_async_iife() -> None:
    """All loaded page scripts must use IIFE pattern (no bare top-level code)."""
    loaded_scripts = [
        "admin-notifications.js",
        "admin-notifications-sim.js",
        "notifications-center.js",
        "community-bundle.js",
        "community-board.js",
        "reputation-hub-vitals.js",
        "reputation-recognition.js",
        "beta-map.js",
    ]
    for name in loaded_scripts:
        content = _read_js(name).strip()
        assert content.startswith("(function") or content.startswith("(async function") or content.startswith("(()"), \
            f"{name} must start with IIFE or async IIFE, got: {content[:50]}"
