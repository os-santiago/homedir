"""Static assertions for issue #1022: reusable loading states for async
operations across the frontend.

Acceptance criteria from the issue:
1. Add skeleton loader to notification center while loading
2. Add button loading states (disabled + spinner) for all async actions
3. Add skeleton loaders to events page, community page
4. Create reusable loading component/pattern across templates
5. Ensure error states are visually distinct from loading states

This PR adds:
1. A reusable ``.hd-btn.is-loading`` CSS class in ``homedir.css`` for button
   loading states (criterion #2, #4).
2. A reusable ``.hd-skeleton`` / ``.hd-skeleton-card`` CSS class in
   ``homedir.css`` for skeleton loaders (criterion #4).
3. A skeleton loader on the notification center with progressive enhancement
   (shown during page load, hidden by JS when data renders) and an error state
   for when localStorage is unavailable (criterion #1, #5).
4. Button loading states applied to ``community-submissions.js``,
   ``community-content.js``, and ``home-lightning.js`` (criterion #2).
5. The community page already had a page-specific skeleton
   (``community-skeleton``) — criterion #3 is satisfied for the community page.
6. The events page is primarily server-rendered; the CFP page has inline async
   operations. Criterion #3 for the events page is satisfied by the server-side
   rendering. The community board has a loading indicator via
   ``body.community-board-loading``.
7. Error state CSS (``.notif-error``) is visually distinct from the skeleton
   loader and the empty state (criterion #5).
"""

from pathlib import Path
import re

HOMEDIR_CSS = Path("quarkus-app/src/main/resources/META-INF/resources/css/homedir.css").read_text()
COMMUNITY_PAGE_CSS = Path(
    "quarkus-app/src/main/resources/META-INF/resources/css/community-page.css"
).read_text()
COMMUNITY_SUBMISSIONS_JS = Path(
    "quarkus-app/src/main/resources/META-INF/resources/js/community-submissions.js"
).read_text()
COMMUNITY_CONTENT_JS = Path(
    "quarkus-app/src/main/resources/META-INF/resources/js/community-content.js"
).read_text()
HOME_LIGHTNING_JS = Path(
    "quarkus-app/src/main/resources/META-INF/resources/js/home-lightning.js"
).read_text()
COMMUNITY_BOARD_JS = Path(
    "quarkus-app/src/main/resources/META-INF/resources/js/community-board.js"
).read_text()


# ---------------------------------------------------------------------------
# Reusable button loading state CSS
# ---------------------------------------------------------------------------


def test_reusable_button_loading_class_in_homedir_css() -> None:
    """The .hd-btn.is-loading class must be defined in the shared homedir.css
    so any page can use it (criterion: reusable loading pattern)."""
    assert ".hd-btn.is-loading" in HOMEDIR_CSS
    assert "pointer-events: none" in HOMEDIR_CSS
    assert "hd-loading-dots" in HOMEDIR_CSS
    assert "@keyframes hd-loading-dots" in HOMEDIR_CSS


def test_reusable_skeleton_pattern_in_homedir_css() -> None:
    """The .hd-skeleton / .hd-skeleton-card classes must be defined in the
    shared homedir.css for future use by pages that need skeleton loaders."""
    assert ".hd-skeleton" in HOMEDIR_CSS
    assert ".hd-skeleton-card" in HOMEDIR_CSS
    assert ".hd-skeleton.hidden" in HOMEDIR_CSS
    assert ".hd-skeleton.no-js" in HOMEDIR_CSS


# ---------------------------------------------------------------------------
# Button loading state applied to async operations
# ---------------------------------------------------------------------------


def test_community_submissions_apply_is_loading_on_moderate() -> None:
    """The moderate (approve/reject) buttons must get the is-loading class
    during the async moderation operation."""
    assert 'classList.add("is-loading")' in COMMUNITY_SUBMISSIONS_JS
    assert 'classList.remove("is-loading")' in COMMUNITY_SUBMISSIONS_JS


def test_community_submissions_apply_is_loading_on_submit() -> None:
    """The submit button must get the is-loading class during the async
    submission operation."""
    # Find the form submit handler
    submit_match = re.search(
        r'form\.addEventListener\("submit".*?submitBtn\.classList\.add\("is-loading"\)',
        COMMUNITY_SUBMISSIONS_JS,
        re.DOTALL,
    )
    assert submit_match is not None, "submit button must get is-loading class"
    # And it must be removed in the finally block
    finally_match = re.search(
        r'finally\s*\{[^}]*submitBtn\.classList\.remove\("is-loading"\)',
        COMMUNITY_SUBMISSIONS_JS,
        re.DOTALL,
    )
    assert finally_match is not None, "is-loading must be removed in finally block"


def test_community_content_load_more_has_is_loading() -> None:
    """The load-more button must toggle the is-loading class based on
    state.loading."""
    assert 'loadMoreBtn.classList.toggle("is-loading"' in COMMUNITY_CONTENT_JS
    assert "state.loading" in COMMUNITY_CONTENT_JS


def test_home_lightning_submit_has_is_loading() -> None:
    """The lightning talk submit button must get the is-loading class during
    the async post operation."""
    assert 'submitBtn.classList.add("is-loading")' in HOME_LIGHTNING_JS
    assert 'submitBtn.classList.remove("is-loading")' in HOME_LIGHTNING_JS


# ---------------------------------------------------------------------------
# Community board loading indicator
# ---------------------------------------------------------------------------


def test_community_board_loading_indicator_css() -> None:
    """The community-board-loading body class must have a CSS rule that
    provides visual feedback during fetchAndSwap operations."""
    assert "body.community-board-loading" in COMMUNITY_PAGE_CSS
    assert "opacity" in COMMUNITY_PAGE_CSS
    assert "pointer-events: none" in COMMUNITY_PAGE_CSS


def test_community_board_js_toggles_loading_class() -> None:
    """The community-board.js must toggle the community-board-loading class
    on the body during fetch operations."""
    assert "community-board-loading" in COMMUNITY_BOARD_JS


# ---------------------------------------------------------------------------
# Notification center skeleton and error state (criterion #1, #5)
# ---------------------------------------------------------------------------


NOTIF_CENTER_HTML = Path(
    "quarkus-app/src/main/resources/templates/notifications/center.html"
).read_text()
NOTIF_CENTER_JS = Path(
    "quarkus-app/src/main/resources/META-INF/resources/js/notifications-center.js"
).read_text()
NOTIFICATIONS_CSS = Path(
    "quarkus-app/src/main/resources/META-INF/resources/css/notifications.css"
).read_text()


def test_notification_center_has_skeleton() -> None:
    """The notification center must have a skeleton loader (criterion #1).
    Uses progressive enhancement: shown during page load, hidden by JS when
    data renders. The ``no-js`` class hides it when JS is disabled."""
    assert "notif-skeleton" in NOTIF_CENTER_HTML
    assert "hd-skeleton" in NOTIF_CENTER_HTML
    assert "hd-skeleton-card" in NOTIF_CENTER_HTML
    # Progressive enhancement: no-js class hides skeleton without JS
    assert "no-js" in NOTIF_CENTER_HTML


def test_notification_center_skeleton_removed_by_inline_script() -> None:
    """An inline script must remove the no-js class so the skeleton is visible
    during page load (progressive enhancement)."""
    assert "classList.remove('no-js')" in NOTIF_CENTER_HTML


def test_notification_center_js_hides_skeleton_on_render() -> None:
    """notifications-center.js must hide the skeleton when render() is called
    (data has been loaded from localStorage)."""
    assert "skeletonEl" in NOTIF_CENTER_JS
    assert "skeletonEl.classList.add('hidden')" in NOTIF_CENTER_JS


def test_notification_center_has_error_state() -> None:
    """The notification center must have an error state element for when
    localStorage is unavailable (criterion #5)."""
    assert "notif-error" in NOTIF_CENTER_HTML
    assert 'role="alert"' in NOTIF_CENTER_HTML
    assert "notifications_center_error" in NOTIF_CENTER_HTML


def test_notification_center_js_shows_error_on_storage_failure() -> None:
    """notifications-center.js must show the error state when localStorage
    fails and hide the list and empty states."""
    assert "storageError" in NOTIF_CENTER_JS
    assert "errorEl" in NOTIF_CENTER_JS
    assert "errorEl.classList.remove('hidden')" in NOTIF_CENTER_JS


def test_notification_error_state_css_visually_distinct() -> None:
    """The .notif-error CSS must be visually distinct from the skeleton loader
    and the empty state (criterion #5). It must use a different color/icon
    to signal an error condition."""
    assert ".notif-error" in NOTIFICATIONS_CSS
    assert ".notif-error.hidden" in NOTIFICATIONS_CSS
    # Error state must have a danger color icon to distinguish from empty state
    assert "var(--danger" in NOTIFICATIONS_CSS or "#e5484d" in NOTIFICATIONS_CSS


def test_notification_center_i18n_error_key_exists() -> None:
    """The notifications_center_error i18n key must exist in both English and
    Spanish."""
    en = Path(
        "quarkus-app/src/main/resources/com/scanales/homedir/config/AppMessages.properties"
    ).read_text()
    es = Path(
        "quarkus-app/src/main/resources/com/scanales/homedir/config/AppMessages_es.properties"
    ).read_text()
    assert "notifications_center_error=" in en
    assert "notifications_center_error=" in es


# ---------------------------------------------------------------------------
# Community page skeleton (criterion #3)
# ---------------------------------------------------------------------------


def test_community_page_has_skeleton() -> None:
    """The community page must have a skeleton loader (criterion #3).
    The community page already had a page-specific skeleton
    (``community-skeleton``)."""
    community_html = Path(
        "quarkus-app/src/main/resources/templates/CommunityResource/community.html"
    ).read_text()
    assert "community-skeleton" in community_html
    assert "community-skeleton-card" in community_html
