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
import json
import pathlib
import re
import urllib.parse
from typing import Dict, List


PREVIEW_ITEMS: List[Dict[str, object]] = [
    {
        "title": "KubeCon talks and highlights",
        "url": "https://www.youtube.com/@KubeCon",
        "summary": "Conference video highlights focused on Kubernetes, cloud-native operations, and platform engineering.",
        "source": "youtube.com",
        "media_type": "video_story",
        "tags": ["kubernetes", "platform-engineering", "cloud-native"],
    },
    {
        "title": "Fireship quick engineering stories",
        "url": "https://www.youtube.com/@Fireship",
        "summary": "Short technology explainers and trend recaps for developers working with modern tools.",
        "source": "youtube.com",
        "media_type": "video_story",
        "tags": ["developers", "trending-tech", "ai"],
    },
    {
        "title": "Google Cloud Tech updates",
        "url": "https://www.youtube.com/@GoogleCloudTech",
        "summary": "Video stories about cloud platform features, AI infrastructure, and production architecture.",
        "source": "youtube.com",
        "media_type": "video_story",
        "tags": ["cloud", "platform-engineering", "ai"],
    },
    {
        "title": "The Changelog podcast",
        "url": "https://changelog.com/podcast",
        "summary": "Developer podcast with practical conversations on open-source, tooling, and software delivery.",
        "source": "changelog.com",
        "media_type": "podcast",
        "tags": ["podcast", "developers", "open-source"],
    },
    {
        "title": "Data Engineering Podcast",
        "url": "https://www.dataengineeringpodcast.com/",
        "summary": "Audio interviews about data platforms, architecture decisions, and engineering practices.",
        "source": "dataengineeringpodcast.com",
        "media_type": "podcast",
        "tags": ["podcast", "data", "platform-engineering"],
    },
    {
        "title": "Software Engineering Daily",
        "url": "https://softwareengineeringdaily.com/",
        "summary": "Podcast episodes with technical deep dives on systems, AI adoption, and developer platforms.",
        "source": "softwareengineeringdaily.com",
        "media_type": "podcast",
        "tags": ["podcast", "developers", "ai"],
    },
    {
        "title": "Kubernetes Blog",
        "url": "https://kubernetes.io/blog/",
        "summary": "Official Kubernetes release notes, architecture changes, and ecosystem guidance for operators.",
        "source": "kubernetes.io",
        "media_type": "article_blog",
        "tags": ["kubernetes", "devops", "platform-engineering"],
    },
    {
        "title": "GitHub Engineering and product blog",
        "url": "https://github.blog/",
        "summary": "Articles about developer workflows, platform capabilities, and open-source ecosystem updates.",
        "source": "github.blog",
        "media_type": "article_blog",
        "tags": ["open-source", "developers", "platform"],
    },
    {
        "title": "CNCF Blog",
        "url": "https://www.cncf.io/blog/",
        "summary": "Cloud-native project updates and community practices from maintainers and platform teams.",
        "source": "cncf.io",
        "media_type": "article_blog",
        "tags": ["cloud-native", "open-source", "devops"],
    },
    {
        "title": "OpenAI News",
        "url": "https://openai.com/news/",
        "summary": "Official announcements on model releases, platform features, and applied AI developments.",
        "source": "openai.com",
        "media_type": "article_blog",
        "tags": ["ai", "trending-tech", "platform"],
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


def build_item(raw: Dict[str, object], created_at: dt.datetime) -> Dict[str, object]:
    url = canonical_url(str(raw["url"]))
    digest = hashlib.sha1(url.encode("utf-8")).hexdigest()[:12]
    return {
        "id": digest,
        "title": str(raw["title"]),
        "url": url,
        "summary": str(raw["summary"]),
        "source": str(raw["source"]),
        "created_at": to_iso(created_at),
        "media_type": str(raw["media_type"]),
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
                "file": filename,
            }
        )

    (output_dir / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(f"Generated {manifest['total']} preview items in {output_dir}")
    print("Distribution:", json.dumps(manifest["by_media_type"]))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
