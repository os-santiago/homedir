"""Static assertions for issue #1318 (split from #1017): page scripts should
use a consistent IIFE entry pattern and early-return when their root element is
not on the page, instead of relying on a redundant DOMContentLoaded wrapper
(scripts are loaded with `defer`).
"""

from pathlib import Path

JS_DIR = Path("quarkus-app/src/main/resources/META-INF/resources/js")


def _read(name: str) -> str:
    return (JS_DIR / name).read_text()


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
