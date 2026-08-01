"""Static assertions for issue #1022: reusable loading states for async
operations across the frontend.

This PR focuses on the actual gaps:
1. A reusable ``.hd-btn.is-loading`` CSS class in ``homedir.css`` for button
   loading states during async operations (disabled + animated dots).
2. Button loading states applied to ``community-submissions.js``,
   ``community-content.js``, and ``home-lightning.js``.
3. A visual loading indicator for community board ``fetchAndSwap`` operations
   via ``body.community-board-loading`` in ``community-page.css``.

The notification center reads from localStorage synchronously (fast, no
skeleton needed). The events page is server-rendered (no client-side loading
needed). The community page already had a page-specific skeleton.
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


def test_no_unused_skeleton_css() -> None:
    """The .hd-skeleton / .hd-skeleton-card classes should NOT be in homedir.css
    since no template uses them (the notification center reads from localStorage
    synchronously and doesn't need a skeleton)."""
    assert ".hd-skeleton" not in HOMEDIR_CSS
    assert ".hd-skeleton-card" not in HOMEDIR_CSS


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
    submit_match = re.search(
        r'form\.addEventListener\("submit".*?submitBtn\.classList\.add\("is-loading"\)',
        COMMUNITY_SUBMISSIONS_JS,
        re.DOTALL,
    )
    assert submit_match is not None, "submit button must get is-loading class"
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
# Notification center does NOT have unnecessary skeleton/error state
# ---------------------------------------------------------------------------


def test_notification_center_has_no_skeleton() -> None:
    """The notification center must NOT have a skeleton loader since it reads
    from localStorage synchronously (no async fetch to wait for)."""
    notif_center_html = Path(
        "quarkus-app/src/main/resources/templates/notifications/center.html"
    ).read_text()
    assert "notif-skeleton" not in notif_center_html
    assert "hd-skeleton" not in notif_center_html


def test_notification_center_has_no_error_state() -> None:
    """The notification center must NOT have a separate error state element
    since localStorage errors are already handled gracefully by the catch
    block returning an empty array."""
    notif_center_html = Path(
        "quarkus-app/src/main/resources/templates/notifications/center.html"
    ).read_text()
    assert "notif-error" not in notif_center_html


def test_no_unused_notification_error_i18n() -> None:
    """The notifications_center_error i18n key should NOT exist since the
    error state element was removed."""
    en = Path(
        "quarkus-app/src/main/resources/com/scanales/homedir/config/AppMessages.properties"
    ).read_text()
    es = Path(
        "quarkus-app/src/main/resources/com/scanales/homedir/config/AppMessages_es.properties"
    ).read_text()
    assert "notifications_center_error=" not in en
    assert "notifications_center_error=" not in es


# ---------------------------------------------------------------------------
# Community page skeleton (already existed)
# ---------------------------------------------------------------------------


def test_community_page_has_skeleton() -> None:
    """The community page must have a skeleton loader.
    The community page already had a page-specific skeleton
    (``community-skeleton``)."""
    community_html = Path(
        "quarkus-app/src/main/resources/templates/CommunityResource/community.html"
    ).read_text()
    assert "community-skeleton" in community_html
    assert "community-skeleton-card" in community_html
