#!/usr/bin/env python3
import argparse
import datetime as dt
import hashlib
import pathlib
import re
import urllib.parse

VALID_MEDIA = {"video_story", "podcast", "article_blog"}


def slugify(text: str) -> str:
    value = re.sub(r"[^a-zA-Z0-9]+", "-", text).strip("-").lower()
    return value or "item"


def normalize_media(raw: str) -> str:
    value = (raw or "").strip().lower().replace("-", "_").replace(" ", "_")
    if value in VALID_MEDIA:
        return value
    return "article_blog"


def infer_media(url: str) -> str:
    host = (urllib.parse.urlparse(url).netloc or "").lower()
    if "youtube.com" in host or "youtu.be" in host or "vimeo.com" in host or "tiktok.com" in host:
        return "video_story"
    if "podcast" in host or "spotify.com" in host or "soundcloud.com" in host or "anchor.fm" in host:
        return "podcast"
    return "article_blog"


def build_item(url: str, forced_media=None) -> dict:
    parsed = urllib.parse.urlparse(url)
    host = parsed.netloc or "unknown-source"
    path_slug = slugify(parsed.path or "content")
    digest = hashlib.sha1(url.encode("utf-8")).hexdigest()[:10]
    now = dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
    media_type = normalize_media(forced_media) if forced_media else infer_media(url)
    return {
        "id": digest,
        "title": f"Curated content from {host}",
        "url": url,
        "summary": "Replace this placeholder summary with a curated 1-3 line description.",
        "source": host,
        "created_at": now,
        "media_type": media_type,
        "tags": [path_slug],
    }


def to_yaml(item: dict) -> str:
    lines = []
    for key in ("id", "title", "url", "summary", "source", "created_at", "media_type"):
        lines.append(f'{key}: "{item[key]}"')
    tags = item.get("tags") or []
    if tags:
        lines.append("tags:")
        for tag in tags:
            lines.append(f'  - "{tag}"')
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate curated content YAML stubs from URLs")
    parser.add_argument("--input", required=True, help="Text file with one URL per line")
    parser.add_argument("--output", required=True, help="Output directory for .yml files")
    parser.add_argument(
        "--media",
        default="auto",
        help="media type for all generated items: auto|video_story|podcast|article_blog",
    )
    args = parser.parse_args()

    input_path = pathlib.Path(args.input)
    output_dir = pathlib.Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    urls = [line.strip() for line in input_path.read_text(encoding="utf-8").splitlines() if line.strip()]
    today = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%d")

    forced_media = None if args.media == "auto" else normalize_media(args.media)

    for url in urls:
        item = build_item(url, forced_media)
        parsed = urllib.parse.urlparse(url)
        slug = slugify((parsed.netloc + "-" + parsed.path).strip("-"))
        filename = f"{today}-{slug}-{item['id']}.yml"
        (output_dir / filename).write_text(to_yaml(item), encoding="utf-8")

    print(f"Generated {len(urls)} curated YAML files in {output_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
