"""Static assertions for issue #1316 (split from #1017): page scripts must not
redefine escaping logic; they must use the shared `window.HomeDirUtils.escapeHtml`
from `utils.js` (loaded first in the layout <head>).
"""

from pathlib import Path

JS_DIR = Path("quarkus-app/src/main/resources/META-INF/resources/js")


def _read(name: str) -> str:
    return (JS_DIR / name).read_text()


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
        assert "&amp;" not in src, name
        # They delegate to the shared util.
        assert "window.HomeDirUtils.escapeHtml" in src, name


def test_notifications_center_call_sites_use_shared_util() -> None:
    src = _read("notifications-center.js")
    assert "${window.HomeDirUtils.escapeHtml(" in src
