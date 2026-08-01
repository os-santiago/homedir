"""Static assertions for issue #1017: the notification page scripts should use
a consistent IIFE entry pattern with early-return guards (instead of a redundant
DOMContentLoaded wrapper, since they are loaded with ``defer``) and should not
duplicate the HTML-escaping logic — they must delegate to the shared
``window.HomeDirUtils.escapeHtml`` from ``utils.js``.

Each script must guard against ``window.HomeDirUtils`` being unavailable so the
page degrades gracefully (console.error + early return) instead of throwing a
TypeError at a call site.
"""

from pathlib import Path

JS_DIR = Path("quarkus-app/src/main/resources/META-INF/resources/js")


def _read(name: str) -> str:
    return (JS_DIR / name).read_text()


# ---------------------------------------------------------------------------
# IIFE entry pattern (issue #1017 / former #1318)
# ---------------------------------------------------------------------------


def test_admin_notifications_sim_uses_iife_not_domcontentloaded() -> None:
    src = _read("admin-notifications-sim.js")
    assert src.startswith("(function () {")
    assert "document.addEventListener('DOMContentLoaded'" not in src
    assert src.rstrip().endswith("})();")


def test_admin_scripts_early_return_when_root_missing() -> None:
    sim = _read("admin-notifications-sim.js")
    assert "if (!eventId || !pivot || !states || !resultsTable || !optin)" in sim
    assert "return;" in sim

    admin = _read("admin-notifications.js")
    assert "if (!listEl)" in admin
    assert "return;" in admin


def test_admin_notifications_uses_iife() -> None:
    src = _read("admin-notifications.js")
    assert src.startswith("(async function(){")


# ---------------------------------------------------------------------------
# Shared escaping — no duplicates (issue #1017 / former #1316)
# ---------------------------------------------------------------------------


def test_utils_is_single_source_of_escape_html() -> None:
    utils = _read("utils.js")
    assert "window.HomeDirUtils = { escapeHtml" in utils
    # utils.js must still own the actual replacement logic.
    assert "&amp;" in utils


def test_notification_scripts_do_not_redefine_escape() -> None:
    for name in ("notifications-center.js", "admin-notifications.js", "admin-notifications-sim.js"):
        src = _read(name)
        # No local escaping implementation (no replace of the HTML special chars).
        assert "function escapeHtml" not in src, name
        assert "function esc(" not in src, name
        assert "function escapeAttr" not in src, name
        assert "&amp;" not in src, name
        # They delegate to the shared util.
        assert "window.HomeDirUtils.escapeHtml" in src, name


def test_notification_scripts_guard_against_missing_homedirutils() -> None:
    """Each script must check window.HomeDirUtils before using it so a failed
    utils.js load degrades gracefully instead of throwing."""
    for name in ("notifications-center.js", "admin-notifications.js", "admin-notifications-sim.js"):
        src = _read(name)
        assert "if (!window.HomeDirUtils)" in src, name
        assert "console.error" in src, name
        assert "return;" in src, name


def test_notifications_center_call_sites_use_shared_util() -> None:
    src = _read("notifications-center.js")
    assert "${window.HomeDirUtils.escapeHtml(" in src
    # The old local escapeAttr function must be gone.
    assert "escapeAttr(" not in src
