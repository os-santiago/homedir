"""Static assertions for issue #1022: reusable loading states for async
operations across the frontend.

This PR focuses on the actual gaps:
1. A reusable ``button.is-loading`` CSS class in ``homedir.css`` for button
   loading states during async operations (disabled + CSS spinner).
2. Button loading states applied to ``community-bundle.js`` — the only JS
   file loaded by the community page template. The standalone files
   (``home-lightning.js``, ``community-content.js``, ``community-submissions.js``)
   are served as static resources but NOT referenced by any template, so
   adding loading states there would be dead code.
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
COMMUNITY_BUNDLE_JS = Path(
    "quarkus-app/src/main/resources/META-INF/resources/js/community-bundle.js"
).read_text()
COMMUNITY_BOARD_JS = Path(
    "quarkus-app/src/main/resources/META-INF/resources/js/community-board.js"
).read_text()
COMMUNITY_HTML = Path(
    "quarkus-app/src/main/resources/templates/CommunityResource/community.html"
).read_text()


# ---------------------------------------------------------------------------
# Reusable button loading state CSS
# ---------------------------------------------------------------------------


def test_reusable_button_loading_class_in_homedir_css() -> None:
    """The button.is-loading class must be defined in the shared homedir.css
    so any page can use it (criterion: reusable loading pattern)."""
    rule = re.search(
        r"button\.is-loading\s*\{([^}]*)\}", HOMEDIR_CSS
    )
    assert rule is not None, "button.is-loading rule must exist"
    rule_body = rule.group(1)
    assert "pointer-events: none" in rule_body
    assert "position: relative" in rule_body
    spinner = re.search(
        r"button\.is-loading::after\s*\{([^}]*)\}", HOMEDIR_CSS
    )
    assert spinner is not None, "button.is-loading::after rule must exist"
    assert "border-top-color: transparent" in spinner.group(1)
    assert "animation: hd-btn-spin" in spinner.group(1)
    assert "@keyframes hd-btn-spin" in HOMEDIR_CSS


def test_button_loading_uses_standard_animatable_property() -> None:
    """The spinner animation must use ``transform`` (a standard animatable
    property), not ``content`` (which is non-standard and only works in
    Chromium browsers)."""
    keyframe_block = re.search(
        r"@keyframes hd-btn-spin\s*\{([^}]*)\}", HOMEDIR_CSS
    )
    assert keyframe_block is not None, "hd-btn-spin keyframe must exist"
    assert "transform" in keyframe_block.group(1)
    assert "content" not in keyframe_block.group(1)


def test_button_loading_respects_reduced_motion() -> None:
    """The spinner animation must be disabled under prefers-reduced-motion
    per the project CSS style guide."""
    media_blocks = re.finditer(
        r"@media\s*\(prefers-reduced-motion:\s*reduce\)\s*\{",
        HOMEDIR_CSS,
    )
    inner_blocks = []
    for media in media_blocks:
        start = media.end()
        depth = 1
        i = start
        while i < len(HOMEDIR_CSS) and depth > 0:
            if HOMEDIR_CSS[i] == "{":
                depth += 1
            elif HOMEDIR_CSS[i] == "}":
                depth -= 1
            i += 1
        inner_blocks.append(HOMEDIR_CSS[start:i - 1])
    assert inner_blocks, "prefers-reduced-motion block must exist"
    matched = any(
        re.search(r"button\.is-loading::after\s*\{([^}]*)\}", inner) is not None
        and re.search(r"button\.is-loading::after\s*\{([^}]*animation:\s*none[^}]*)\}", inner)
        is not None
        for inner in inner_blocks
    )
    assert matched, (
        "button.is-loading::after must be disabled under prefers-reduced-motion"
    )


def test_no_unused_skeleton_css() -> None:
    """The .hd-skeleton / .hd-skeleton-card classes should NOT be in homedir.css
    since no template uses them (the notification center reads from localStorage
    synchronously and doesn't need a skeleton)."""
    assert ".hd-skeleton" not in HOMEDIR_CSS
    assert ".hd-skeleton-card" not in HOMEDIR_CSS


def test_no_old_hd_btn_selector() -> None:
    """The old ``.hd-btn.is-loading`` selector must NOT remain — the buttons
    that receive ``is-loading`` use ``btn``, ``btn--primary``, or ``btn-primary``
    classes, not ``hd-btn``. The selector is now ``button.is-loading`` which
    matches any button element."""
    assert ".hd-btn.is-loading" not in HOMEDIR_CSS


# ---------------------------------------------------------------------------
# Button loading state applied to async operations in community-bundle.js
# (the only JS file loaded by the community page template)
# ---------------------------------------------------------------------------


def test_community_bundle_has_lightning_submit_is_loading() -> None:
    """The lightning talk submit button must get the is-loading class during
    the async post operation."""
    handler = re.search(
        r"async function postThread\(event\)\s*\{(.*?)\n  \}",
        COMMUNITY_BUNDLE_JS,
        re.DOTALL,
    )
    assert handler is not None, "postThread handler must exist"
    body = handler.group(1)
    assert 'submitBtn.classList.add("is-loading")' in body
    assert 'submitBtn.classList.remove("is-loading")' in body
    assert re.search(
        r"finally\s*\{[^}]*submitBtn\.classList\.remove\(\"is-loading\"\)", body
    ) is not None, "is-loading must be removed in the finally block"


def test_community_bundle_has_load_more_is_loading() -> None:
    """The load-more button must toggle the is-loading class based on
    state.loading, while remaining visible (not hidden) during the request."""
    handler = re.search(
        r"function updateLoadMoreState\(\)\s*\{(.*?)\n  \}",
        COMMUNITY_BUNDLE_JS,
        re.DOTALL,
    )
    assert handler is not None, "updateLoadMoreState handler must exist"
    body = handler.group(1)
    assert 'loadMoreBtn.classList.toggle("is-loading"' in body
    assert 'loadMoreBtn.classList.toggle("hidden", !hasMore)' in body
    assert "!hasMore || state.loading" not in body


def test_community_bundle_has_moderation_is_loading() -> None:
    """The moderate (approve/reject) buttons must get the is-loading class
    during the async moderation operation."""
    handler = re.search(
        r'actions\.addEventListener\("click", async \(event\) => \{(.*?)\n      \}\);',
        COMMUNITY_BUNDLE_JS,
        re.DOTALL,
    )
    assert handler is not None, "moderation click handler must exist"
    body = handler.group(1)
    assert 'target.classList.add("is-loading")' in body
    assert 'target.classList.remove("is-loading")' in body
    assert re.search(
        r"finally\s*\{[^}]*target\.classList\.remove\(\"is-loading\"\)", body
    ) is not None, "is-loading must be removed in the finally block"


def test_community_bundle_has_submit_is_loading() -> None:
    """The community submit button must get the is-loading class during the
    async submission operation."""
    submit_match = re.search(
        r'form\.addEventListener\("submit".*?submitBtn\.classList\.add\("is-loading"\)',
        COMMUNITY_BUNDLE_JS,
        re.DOTALL,
    )
    assert submit_match is not None, "submit button must get is-loading class"
    finally_match = re.search(
        r'finally\s*\{[^}]*submitBtn\.classList\.remove\("is-loading"\)',
        COMMUNITY_BUNDLE_JS,
        re.DOTALL,
    )
    assert finally_match is not None, "is-loading must be removed in finally block"


def test_dead_js_files_do_not_have_is_loading() -> None:
    """The standalone JS files (home-lightning.js, community-content.js,
    community-submissions.js) are NOT loaded by any template. Adding
    is-loading there would be dead code."""
    for filename in ("home-lightning.js", "community-content.js", "community-submissions.js"):
        path = Path(f"quarkus-app/src/main/resources/META-INF/resources/js/{filename}")
        content = path.read_text()
        assert "is-loading" not in content, f"{filename} should not have is-loading (dead code)"


def test_community_page_loads_community_bundle() -> None:
    """The community page template must load community-bundle.js (the file
    where is-loading is applied)."""
    assert "community-bundle.js" in COMMUNITY_HTML


# ---------------------------------------------------------------------------
# Community board loading indicator
# ---------------------------------------------------------------------------


def test_community_board_loading_indicator_css() -> None:
    """The community-board-loading body class must have a CSS rule that
    provides visual feedback during fetchAndSwap operations."""
    rule = re.search(
        r"body\.community-board-loading\s+[^\s{]*\s*\{([^}]*)\}",
        COMMUNITY_PAGE_CSS,
    )
    assert rule is not None, "body.community-board-loading rule must exist"
    rule_body = rule.group(1)
    assert "opacity" in rule_body
    assert "pointer-events: none" in rule_body
    assert "transition" in rule_body or "pointer-events" in rule_body


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
    en = Path("quarkus-app/src/main/resources/messages/i18n.properties").read_text()
    es = Path("quarkus-app/src/main/resources/messages/i18n_es.properties").read_text()
    assert "notifications_center_error=" not in en
    assert "notifications_center_error=" not in es


# ---------------------------------------------------------------------------
# Community page skeleton (already existed)
# ---------------------------------------------------------------------------


def test_community_page_has_skeleton() -> None:
    """The community page must have a skeleton loader.
    The community page already had a page-specific skeleton
    (``community-skeleton``)."""
    assert "community-skeleton" in COMMUNITY_HTML
    assert "community-skeleton-card" in COMMUNITY_HTML
