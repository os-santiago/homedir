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

from html.parser import HTMLParser
from pathlib import Path
import re

JS_DIR = Path("quarkus-app/src/main/resources/META-INF/resources/js")
TEMPLATE_DIR = Path("quarkus-app/src/main/resources/templates")

# Scripts loaded globally from the layout head (or legacy top-level scripts).
# They intentionally expose globals instead of using an IIFE, so they are
# excluded from the page-script IIFE validation.
SHARED_GLOBAL_SCRIPTS = {"utils.js", "core-bundle.js", "retro-theme.js"}


def _read_js(name: str) -> str:
    """Read a JavaScript source file from the JS resource directory."""
    return (JS_DIR / name).read_text()


def _template_script_srcs() -> set[str]:
    """Collect basenames of every /js/ script referenced by templates.

    Parses ``<script src="...">`` attributes so quoting, whitespace, relative
    paths, and version query strings are all handled. Returns a set of script
    basenames with any query/version suffix stripped.
    """

    class ScriptSrcParser(HTMLParser):
        def __init__(self) -> None:
            super().__init__()
            self.srcs: list[str] = []

        def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
            if tag != "script":
                return
            for name, value in attrs:
                if name == "src" and value:
                    self.srcs.append(value)

    srcs: set[str] = set()
    for template_file in TEMPLATE_DIR.rglob("*.html"):
        parser = ScriptSrcParser()
        parser.feed(template_file.read_text())
        for src in parser.srcs:
            if src.startswith("/js/"):
                srcs.add(src.rsplit("/", 1)[-1].split("?", 1)[0])
    return srcs


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
    """admin-notifications.js (consolidated) must use IIFE pattern with
    root-element guard for the simulator section, not DOMContentLoaded
    (redundant with defer)."""
    admin = _read_js("admin-notifications.js")
    stripped = admin.strip()
    assert stripped.startswith("(async function") or stripped.startswith("(function") or stripped.startswith("(()"), \
        "admin-notifications.js must start with IIFE"
    # DOMContentLoaded must not be used as an event listener (comments are OK)
    assert "addEventListener('DOMContentLoaded'" not in admin and \
           'addEventListener("DOMContentLoaded"' not in admin, \
        "admin-notifications.js must not use DOMContentLoaded event listener (defer makes it redundant)"
    # Must have root-element guard for the simulator section
    assert "if (!eventId" in admin or "if (!eventId ||" in admin, \
        "admin-notifications.js must guard against missing simulator root elements"


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
    assert re.search(
        r"return\s+window\.HomeDirUtils\.escapeHtml\(value\)",
        body,
    ), "First escapeText must delegate to HomeDirUtils.escapeHtml"


def test_dead_js_files_not_referenced_by_templates() -> None:
    """Dead JS files must not be referenced by any template."""
    dead_files = {
        "homedir.js",
        "app.js",
        "community-content.js",
        "community-submissions.js",
        "home-lightning.js",
        "home-lta-preview.js",
    }
    referenced = _template_script_srcs()
    overlap = sorted(referenced & dead_files)
    assert not overlap, (
        f"templates reference dead JS files: {overlap}"
    )


def _js_skeleton(content: str) -> str:
    """Return JS content with strings, comments, and regex literals blanked.

    Used to validate file structure (paren/brace balance, IIFE framing) without
    being confused by parentheses that appear inside literals.
    """
    out = list(content)
    REGEX_PRECEDING_KEYWORDS = {
        "return", "typeof", "instanceof", "in", "of", "new", "delete",
        "void", "throw", "yield", "await", "case", "do", "else",
    }

    def is_ident_char(c: str) -> bool:
        return c.isalnum() or c in "_$"

    i = 0
    n = len(content)
    while i < n:
        ch = content[i]
        nxt = content[i + 1] if i + 1 < n else ""
        if ch == "/" and nxt == "/":
            while i < n and content[i] != "\n":
                out[i] = " "
                i += 1
            continue
        if ch == "/" and nxt == "*":
            while i + 1 < n and not (content[i] == "*" and content[i + 1] == "/"):
                if content[i] != "\n":
                    out[i] = " "
                i += 1
            if i + 1 < n:
                out[i] = " "
                out[i + 1] = " "
                i += 2
            continue
        if ch in "\"'`":
            quote = ch
            out[i] = " "
            i += 1
            if quote == "`":
                while i < n:
                    if content[i] == "\\":
                        out[i] = " "
                        if i + 1 < n:
                            out[i + 1] = " "
                        i += 2
                        continue
                    out[i] = " "
                    if content[i] == "`":
                        i += 1
                        break
                    i += 1
            else:
                while i < n:
                    if content[i] == "\\":
                        out[i] = " "
                        if i + 1 < n:
                            out[i + 1] = " "
                        i += 2
                        continue
                    out[i] = " "
                    if content[i] == quote:
                        i += 1
                        break
                    i += 1
            continue
        if ch == "/":
            j = i - 1
            while j >= 0 and content[j].isspace():
                j -= 1
            prev = content[j] if j >= 0 else ""
            if is_ident_char(prev):
                k = j
                while k >= 0 and is_ident_char(content[k]):
                    k -= 1
                is_regex = content[k + 1:j + 1] in REGEX_PRECEDING_KEYWORDS
            else:
                is_regex = prev != ")" and prev != "]" and prev != "}" and prev != ""
            if is_regex:
                out[i] = " "
                i += 1
                while i < n:
                    if content[i] == "\\":
                        out[i] = " "
                        if i + 1 < n:
                            out[i + 1] = " "
                        i += 2
                        continue
                    out[i] = " "
                    if content[i] == "/":
                        i += 1
                        break
                    i += 1
            else:
                i += 1
            continue
        i += 1
    return "".join(out)


def test_all_loaded_scripts_use_iife_or_async_iife() -> None:
    """All loaded page scripts must be a single IIFE (no bare top-level code).

    The script inventory is derived from template ``<script>`` src attributes
    (excluding the globally-loaded shared modules) so newly-added scripts are
    covered automatically. Each file must be a complete IIFE invocation: it
    starts with an IIFE wrapper, ends with an invocation ``)();``, and its
    parentheses and braces balance — with no statements outside the wrapper.
    """
    loaded_scripts = sorted(
        _template_script_srcs() - SHARED_GLOBAL_SCRIPTS
    )
    for name in loaded_scripts:
        content = _read_js(name)
        stripped = content.strip()
        skeleton = _js_skeleton(stripped)
        assert stripped.startswith("("), \
            f"{name} must start with an IIFE wrapper, got: {stripped[:50]}"
        assert skeleton.rstrip().endswith(")();"), \
            f"{name} must end with an IIFE invocation, got: {stripped[-30:]}"
        assert skeleton.count("(") == skeleton.count(")"), \
            f"{name} has unbalanced parentheses"
        assert skeleton.count("{") == skeleton.count("}"), \
            f"{name} has unbalanced braces"
