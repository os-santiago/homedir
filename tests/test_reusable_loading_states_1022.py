"""Tests for issue #1022: reusable loading states for async button operations.

Validates that:
1. homedir.css defines a reusable .is-loading class for buttons with a spinner
2. The spinner animation respects prefers-reduced-motion
3. community-bundle.js applies is-loading to buttons during async operations
   (lightning submit, community submit, load-more, approve/reject moderation)
4. community-page.css defines a visual loading state for community board
   fetchAndSwap operations
"""

from pathlib import Path
import re

CSS_DIR = Path("quarkus-app/src/main/resources/META-INF/resources/css")
JS_DIR = Path("quarkus-app/src/main/resources/META-INF/resources/js")


def test_homedir_css_has_reusable_is_loading_class() -> None:
    """homedir.css must define a reusable .is-loading class for buttons."""
    css = (CSS_DIR / "homedir.css").read_text()
    # Must have the is-loading class targeting buttons
    assert re.search(r'button\.is-loading|\.btn\.is-loading|\.btn--primary\.is-loading', css), \
        "homedir.css must define .is-loading for buttons"
    # Must set cursor to wait and disable pointer events
    is_loading_block = re.search(r'is-loading\s*\{[^}]*cursor:\s*wait[^}]*\}', css, re.DOTALL)
    assert is_loading_block, "is-loading must set cursor: wait"
    assert "pointer-events: none" in css, "is-loading must disable pointer events"


def test_is_loading_has_spinner_animation() -> None:
    """The is-loading class must show a CSS spinner via ::before."""
    css = (CSS_DIR / "homedir.css").read_text()
    # Must have ::before with spinner
    assert re.search(r'is-loading::before\s*\{[^}]*border-radius:\s*50%', css, re.DOTALL), \
        "is-loading::before must define a circular spinner"
    assert "hd-btn-spin" in css, "is-loading must reference the spin keyframe animation"
    assert re.search(r'@keyframes\s+hd-btn-spin\s*\{[^}]*rotate\(360deg\)', css, re.DOTALL), \
        "hd-btn-spin keyframe must rotate 360deg"


def test_is_loading_respects_prefers_reduced_motion() -> None:
    """The spinner animation must be disabled under prefers-reduced-motion."""
    css = (CSS_DIR / "homedir.css").read_text()
    reduced_motion_block = re.search(
        r'@media\s*\(prefers-reduced-motion:\s*reduce\)\s*\{[^}]*is-loading[^}]*\}',
        css,
        re.DOTALL,
    )
    assert reduced_motion_block, \
        "is-loading spinner must be disabled under prefers-reduced-motion"
    assert "animation: none" in reduced_motion_block.group(0), \
        "reduced-motion block must set animation: none"


def test_community_bundle_lightning_submit_has_is_loading() -> None:
    """Lightning submit button must get is-loading class during async."""
    js = (JS_DIR / "community-bundle.js").read_text()
    # Find the postThread function
    post_thread = re.search(r'async function postThread.*?finally\s*\{[^}]*\}', js, re.DOTALL)
    assert post_thread, "postThread function not found"
    body = post_thread.group(0)
    assert 'classList.add("is-loading")' in body, \
        "postThread must add is-loading class to submit button"
    assert 'classList.remove("is-loading")' in body, \
        "postThread must remove is-loading class in finally block"


def test_community_bundle_submit_form_has_is_loading() -> None:
    """Community submission form button must get is-loading class during async."""
    js = (JS_DIR / "community-bundle.js").read_text()
    # Find the community submission form handler (uses /api/community/submissions)
    form_handler = re.search(
        r'form\.addEventListener\("submit",\s*async.*?/api/community/submissions.*?finally\s*\{[^}]*\}',
        js,
        re.DOTALL,
    )
    assert form_handler, "Community submission form handler not found"
    body = form_handler.group(0)
    assert 'classList.add("is-loading")' in body, \
        "Form submit must add is-loading class to submit button"
    assert 'classList.remove("is-loading")' in body, \
        "Form submit must remove is-loading class in finally block"


def test_community_bundle_load_more_has_is_loading() -> None:
    """Load-more button must toggle is-loading based on state.loading."""
    js = (JS_DIR / "community-bundle.js").read_text()
    update_fn = re.search(r'function updateLoadMoreState\(\)\s*\{.*?\}', js, re.DOTALL)
    assert update_fn, "updateLoadMoreState function not found"
    body = update_fn.group(0)
    assert 'is-loading' in body, \
        "updateLoadMoreState must toggle is-loading class based on state.loading"


def test_community_bundle_moderation_has_is_loading() -> None:
    """Moderation approve/reject buttons must get is-loading during async."""
    js = (JS_DIR / "community-bundle.js").read_text()
    # Find the moderation click handler
    moderation_handler = re.search(
        r'actions\.addEventListener\("click".*?finally\s*\{[^}]*\}',
        js,
        re.DOTALL,
    )
    assert moderation_handler, "Moderation click handler not found"
    body = moderation_handler.group(0)
    assert 'classList.add("is-loading")' in body, \
        "Moderation handler must add is-loading to clicked button"
    assert 'classList.remove("is-loading")' in body, \
        "Moderation handler must remove is-loading in finally block"


def test_community_page_css_has_board_loading_state() -> None:
    """community-page.css must define a visual loading state for board navigation."""
    css = (CSS_DIR / "community-page.css").read_text()
    assert "community-board-loading" in css, \
        "community-page.css must define community-board-loading state"
    assert re.search(r'body\.community-board-loading\s+#main-content', css), \
        "community-board-loading must target #main-content"
    assert "opacity" in css, \
        "community-board-loading must change opacity for visual feedback"
