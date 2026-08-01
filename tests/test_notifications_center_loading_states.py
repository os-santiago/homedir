"""Static assertions for issue #1022: the notifications center must show a
loading skeleton on initial paint, hide it after the first render, and present
an error state that is visually distinct from the empty state when local
storage cannot be read.

The skeleton must be hidden when JS is disabled (via a ``no-js`` CSS class that
JS removes on load) so users are not stuck on a loading state forever, and the
list must NOT start with ``class="hidden"`` so it remains accessible without JS.

The skeleton uses the reusable ``hd-skeleton`` / ``hd-skeleton-card`` classes
defined in the shared ``homedir.css`` (criterion 4: reusable loading pattern).
The error state remains page-specific (``.notif-error``) in ``notifications.css``
because its visual treatment is unique to this page.
"""

from pathlib import Path

CENTER = Path("quarkus-app/src/main/resources/templates/notifications/center.html").read_text()
JS = Path("quarkus-app/src/main/resources/META-INF/resources/js/notifications-center.js").read_text()
CSS = Path("quarkus-app/src/main/resources/META-INF/resources/css/notifications.css").read_text()
HOMEDIR_CSS = Path("quarkus-app/src/main/resources/META-INF/resources/css/homedir.css").read_text()
I18N = Path("quarkus-app/src/main/resources/i18n.properties").read_text()
I18N_ES = Path("quarkus-app/src/main/resources/i18n_es.properties").read_text()
APP_MESSAGES = Path(
    "quarkus-app/src/main/java/com/scanales/homedir/config/AppMessages.java"
).read_text()


def test_center_has_skeleton_and_error_markup() -> None:
    assert 'id="notif-skeleton"' in CENTER
    assert 'class="hd-skeleton-card"' in CENTER
    assert 'id="notif-error"' in CENTER
    assert 'role="alert"' in CENTER
    # The list must NOT start hidden — otherwise no-JS users are stuck on the
    # skeleton forever. JS manages visibility on first render instead.
    assert 'id="notif-list" aria-live="polite" class="hidden"' not in CENTER
    assert 'id="notif-list" aria-live="polite"' in CENTER


def test_skeleton_uses_no_js_class_for_progressive_enhancement() -> None:
    """The skeleton has a 'no-js' class in HTML that CSS hides by default.
    JS removes it on load so the skeleton becomes visible for JS users."""
    assert 'class="hd-skeleton no-js"' in CENTER
    assert ".hd-skeleton.no-js" in HOMEDIR_CSS
    assert "display: none" in HOMEDIR_CSS
    # JS must remove the no-js class so the skeleton is visible for JS users.
    assert "classList.remove('no-js')" in JS


def test_no_qute_breaking_noscript_inline_style() -> None:
    """The previous <noscript><style> approach broke Qute because {display:none}
    was parsed as a template expression. Ensure it's gone."""
    assert "<noscript>" not in CENTER or "display:none" not in CENTER


def test_js_hides_skeleton_and_distinguishes_error_from_empty() -> None:
    assert "hideSkeleton()" in JS
    assert "skeletonEl" in JS
    assert "errorEl" in JS
    assert "storageFailed" in JS
    # Error path returns before the empty-state path (distinct states).
    assert JS.index("storageFailed") < JS.index("items = all.filter")


def test_reusable_skeleton_pattern_in_homedir_css() -> None:
    """The reusable skeleton pattern (hd-skeleton / hd-skeleton-card) must be
    defined in the shared homedir.css so other pages can use it (criterion 4)."""
    assert ".hd-skeleton" in HOMEDIR_CSS
    assert ".hd-skeleton-card" in HOMEDIR_CSS
    assert ".hd-skeleton.hidden" in HOMEDIR_CSS
    assert ".hd-skeleton.no-js" in HOMEDIR_CSS


def test_reusable_button_loading_pattern_in_homedir_css() -> None:
    """A reusable button loading state (.hd-btn.is-loading) must be defined in
    the shared homedir.css for use across async button actions (criterion 2)."""
    assert ".hd-btn.is-loading" in HOMEDIR_CSS
    assert "hd-loading-dots" in HOMEDIR_CSS


def test_css_has_error_state_distinct_from_empty() -> None:
    """The error state remains page-specific in notifications.css, visually
    distinct from both the loading skeleton and the empty state."""
    assert ".notif-error" in CSS
    assert ".notif-error.hidden" in CSS


def test_no_duplicate_skeleton_css_in_notifications_css() -> None:
    """The page-specific notif-skeleton CSS must be removed from
    notifications.css — the reusable hd-skeleton pattern in homedir.css
    replaces it to avoid duplication."""
    assert ".notif-skeleton" not in CSS
    assert ".notif-skeleton-card" not in CSS


def test_i18n_keys_exist_for_error_state() -> None:
    assert "notifications_center_error=" in I18N
    assert "notifications_center_error=" in I18N_ES


def test_app_messages_defines_error_method() -> None:
    """The Qute template uses {i18n:notifications_center_error} which requires a
    matching method on AppMessages.java — without it the build fails."""
    assert "notifications_center_error()" in APP_MESSAGES
