#!/usr/bin/env python3
"""Test for issue #1360: notification center CTA text/link consistency.

Verifies that the empty-state CTA button in the notification center
has a link that matches its i18n text in both EN and ES.
"""
import re
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "quarkus-app" / "src" / "main" / "resources"
TEMPLATE = RESOURCES / "templates" / "notifications" / "center.html"
I18N_EN = RESOURCES / "i18n.properties"
I18N_EN_EXPLICIT = RESOURCES / "i18n_en.properties"
I18N_ES = RESOURCES / "i18n_es.properties"
APP_MSGS_EN = RESOURCES / "com" / "scanales" / "homedir" / "config" / "AppMessages.properties"
APP_MSGS_ES = RESOURCES / "com" / "scanales" / "homedir" / "config" / "AppMessages_es.properties"

KEY = "notifications_center_empty_cta_board"


def _get_property(filepath, key):
    """Read a .properties file and return the value for the given key."""
    text = filepath.read_text(encoding="utf-8")
    match = re.search(rf"^{re.escape(key)}=(.*)$", text, re.MULTILINE)
    if not match:
        pytest.fail(f"Key '{key}' not found in {filepath.name}")
    return match.group(1).strip()


def test_template_cta_href_points_to_reputation_hub():
    """The CTA href must point to /comunidad/reputation-hub, not /comunidad/board."""
    content = TEMPLATE.read_text(encoding="utf-8")
    match = re.search(
        r'<a\s+href="([^"]+)"[^>]*>\{i18n:notifications_center_empty_cta_board\}</a>',
        content,
    )
    assert match, "CTA link for notifications_center_empty_cta_board not found in template"
    href = match.group(1)
    assert href == "/comunidad/reputation-hub", (
        f"CTA href should be '/comunidad/reputation-hub' but got '{href}'"
    )


def test_en_cta_text_matches_reputation_hub():
    """EN CTA text must say 'View Reputation Hub', not 'Open Community Board'."""
    for filepath in [I18N_EN, I18N_EN_EXPLICIT, APP_MSGS_EN]:
        value = _get_property(filepath, KEY)
        assert "Reputation Hub" in value, (
            f"EN value in {filepath.name} should contain 'Reputation Hub' but got '{value}'"
        )
        assert "Community Board" not in value, (
            f"EN value in {filepath.name} should not say 'Community Board' but got '{value}'"
        )


def test_es_cta_text_matches_reputation_hub():
    """ES CTA text must say 'Ver Hub de Reputación'."""
    for filepath in [I18N_ES, APP_MSGS_ES]:
        value = _get_property(filepath, KEY)
        assert "Hub de Reputación" in value, (
            f"ES value in {filepath.name} should contain 'Hub de Reputación' but got '{value}'"
        )


def test_nav_reputation_hub_es_is_shortened():
    """nav_reputation_hub ES must be 'Reputación' (shortened per PR #1353/#1290)."""
    value = _get_property(APP_MSGS_ES, "nav_reputation_hub")
    assert value == "Reputación", (
        f"nav_reputation_hub ES should be 'Reputación' but got '{value}'"
    )
