#!/usr/bin/env python3
"""Generate a reproducible 10-item preview pack for Community Picks.

Distribution:
- 3 video_story
- 3 podcast
- 4 article_blog
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import html
import json
import pathlib
import re
import urllib.parse
import urllib.request
from typing import Dict, List

META_IMAGE_RE = re.compile(
    r"<meta[^>]+(?:name|property)=[\"'](?:og:image|twitter:image)[\"'][^>]+content=[\"']([^\"']+)[\"'][^>]*>",
    re.IGNORECASE,
)

PREVIEW_ITEMS: List[Dict[str, object]] = [
    {
        "title": "DEF CON IoT hacking beginner session",
        "url": "https://www.youtube.com/watch?v=YPcOwKtRuDQ",
        "summary": "Community-selected security video about practical IoT attack and defense basics.",
        "source": "youtube.com",
        "media_type": "video_story",
        "tags": ["security", "iot", "developer-experience"],
    },
    {
        "title": "Kubernetes intro spotlight",
        "url": "https://www.youtube.com/watch?v=X48VuDVv0do",
        "summary": "Short video primer for Kubernetes concepts used by platform teams.",
        "source": "youtube.com",
        "media_type": "video_story",
        "tags": ["kubernetes", "platform-engineering", "cloud-native"],
    },
    {
        "title": "Cloud-native operations visual guide",
        "url": "https://vimeo.com/76979871",
        "summary": "Video content focused on operational practices for modern cloud-native environments.",
        "source": "vimeo.com",
        "media_type": "video_story",
        "tags": ["cloud-native", "platform-engineering", "ai-engineering"],
    },
    {
        "title": "The Changelog: selling SDKs in the era of many Claudes",
        "url": "https://cdn.changelog.com/uploads/podcast/677/the-changelog-677.mp3",
        "summary": "Podcast episode about SDK strategy, AI-native developer experience, and platform decisions.",
        "source": "changelog.com",
        "media_type": "podcast",
        "tags": ["podcast", "developer-experience", "ai-engineering"],
    },
    {
        "title": "Changelog News: all the claw things",
        "url": "https://cdn.changelog.com/uploads/news/181/changelog-news-181.mp3",
        "summary": "Weekly engineering and open-source updates in audio format for busy teams.",
        "source": "changelog.com",
        "media_type": "podcast",
        "tags": ["podcast", "developer-experience", "trending-tech"],
    },
    {
        "title": "Changelog Friends: han shot first",
        "url": "https://cdn.changelog.com/uploads/friends/128/changelog--friends-128.mp3",
        "summary": "Audio conversation on engineering culture, tooling, and long-term software maintenance.",
        "source": "changelog.com",
        "media_type": "podcast",
        "tags": ["podcast", "developer-experience", "culture"],
    },
    {
        "title": "Kubernetes Blog",
        "url": "https://kubernetes.io/blog/",
        "summary": "Official Kubernetes release notes, architecture changes, and ecosystem guidance for operators.",
        "source": "kubernetes.io",
        "media_type": "article_blog",
        "tags": ["kubernetes", "cloud-native", "platform-engineering"],
    },
    {
        "title": "GitHub Engineering and product blog",
        "url": "https://github.blog/",
        "summary": "Articles about developer workflows, platform capabilities, and open-source ecosystem updates.",
        "source": "github.blog",
        "media_type": "article_blog",
        "tags": ["open-source", "developer-experience", "platform-engineering"],
    },
    {
        "title": "CNCF Blog",
        "url": "https://www.cncf.io/blog/",
        "summary": "Cloud-native project updates and community practices from maintainers and platform teams.",
        "source": "cncf.io",
        "media_type": "article_blog",
        "tags": ["cloud-native", "platform-engineering", "open-source"],
    },
    {
        "title": "OpenAI News",
        "url": "https://openai.com/news/",
        "summary": "Official announcements on model releases, platform features, and applied AI developments.",
        "source": "openai.com",
        "media_type": "article_blog",
        "tags": ["ai-engineering", "trending-tech", "platform-engineering"],
    },
]


def now_utc() -> dt.datetime:
    return dt.datetime.now(dt.timezone.utc).replace(microsecond=0)


def to_iso(value: dt.datetime) -> str:
    return value.isoformat().replace("+00:00", "Z")


def yaml_quote(text: str) -> str:
    return '"' + (text or "").replace("\\", "\\\\").replace('"', '\\"') + '"'


def slugify(text: str) -> str:
    value = re.sub(r"[^a-zA-Z0-9]+", "-", text).strip("-").lower()
    return value or "item"


def canonical_url(url: str) -> str:
    parsed = urllib.parse.urlparse(url)
    cleaned = parsed._replace(fragment="")
    return urllib.parse.urlunparse(cleaned)


def normalize_http_url(value: str) -> str:
    try:
        parsed = urllib.parse.urlparse(value)
        if parsed.scheme not in {"http", "https"} or not parsed.netloc:
            return ""
        return urllib.parse.urlunparse(parsed._replace(fragment=""))
    except Exception:
        return ""


def youtube_thumbnail(url: str) -> str:
    parsed = urllib.parse.urlparse(url)
    host = (parsed.netloc or "").lower()
    if "youtu.be" in host:
        video_id = parsed.path.strip("/")
        if video_id:
            return f"https://i.ytimg.com/vi/{video_id}/hqdefault.jpg"
    if "youtube.com" in host:
        query = urllib.parse.parse_qs(parsed.query)
        video_id = (query.get("v") or [None])[0]
        if video_id:
            return f"https://i.ytimg.com/vi/{video_id}/hqdefault.jpg"
    return ""


def fetch_og_image(url: str) -> str:
    try:
        req = urllib.request.Request(
            url,
            headers={
                "User-Agent": "homedir-community-curator/1.0",
                "Accept": "text/html,application/xhtml+xml",
            },
        )
        with urllib.request.urlopen(req, timeout=8) as response:
            body = response.read(200000).decode("utf-8", errors="replace")
    except Exception:
        return ""
    match = META_IMAGE_RE.search(body)
    if not match:
        return ""
    return normalize_http_url(html.unescape(match.group(1)))


def build_item(raw: Dict[str, object], created_at: dt.datetime) -> Dict[str, object]:
    url = canonical_url(str(raw["url"]))
    digest = hashlib.sha1(url.encode("utf-8")).hexdigest()[:12]
    media_type = str(raw["media_type"])
    thumbnail_url = normalize_http_url(str(raw.get("thumbnail_url") or ""))
    if not thumbnail_url and media_type == "video_story":
        thumbnail_url = youtube_thumbnail(url)
    if not thumbnail_url:
        thumbnail_url = fetch_og_image(url)
    return {
        "id": digest,
        "title": str(raw["title"]),
        "url": url,
        "summary": str(raw["summary"]),
        "source": str(raw["source"]),
        "created_at": to_iso(created_at),
        "media_type": media_type,
        "thumbnail_url": thumbnail_url or None,
        "tags": list(raw.get("tags") or []),
    }


def write_yaml(path: pathlib.Path, item: Dict[str, object]) -> None:
    lines = [
        f"id: {yaml_quote(str(item['id']))}",
        f"title: {yaml_quote(str(item['title']))}",
        f"url: {yaml_quote(str(item['url']))}",
        f"summary: {yaml_quote(str(item['summary']))}",
        f"source: {yaml_quote(str(item['source']))}",
        f"created_at: {yaml_quote(str(item['created_at']))}",
        f"media_type: {yaml_quote(str(item['media_type']))}",
    ]
    if item.get("thumbnail_url"):
        lines.append(f"thumbnail_url: {yaml_quote(str(item['thumbnail_url']))}")
    tags = [str(tag) for tag in (item.get("tags") or [])]
    if tags:
        lines.append("tags:")
        for tag in tags:
            lines.append(f"  - {yaml_quote(tag)}")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate a 10-item Community preview mix")
    parser.add_argument("--output-dir", required=True, help="Output directory for YAML files")
    args = parser.parse_args()

    output_dir = pathlib.Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    created = now_utc()
    date_prefix = created.strftime("%Y%m%d")
    manifest = {
        "generated_at": to_iso(created),
        "total": 0,
        "by_media_type": {"video_story": 0, "podcast": 0, "article_blog": 0},
        "items": [],
    }

    for raw in PREVIEW_ITEMS:
        item = build_item(raw, created)
        slug = slugify(str(item["title"]))[:64]
        filename = f"{date_prefix}-{slug}-{item['id']}.yml"
        write_yaml(output_dir / filename, item)
        media_type = str(item["media_type"])
        manifest["total"] += 1
        manifest["by_media_type"][media_type] = manifest["by_media_type"].get(media_type, 0) + 1
        manifest["items"].append(
            {
                "id": item["id"],
                "title": item["title"],
                "url": item["url"],
                "media_type": media_type,
                "thumbnail_url": item.get("thumbnail_url"),
                "file": filename,
            }
        )

    (output_dir / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(f"Generated {manifest['total']} preview items in {output_dir}")
    print("Distribution:", json.dumps(manifest["by_media_type"]))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
