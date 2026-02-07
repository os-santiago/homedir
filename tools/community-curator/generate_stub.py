#!/usr/bin/env python3
import argparse
import datetime as dt
import hashlib
import pathlib
import re
import urllib.parse


def slugify(text: str) -> str:
    value = re.sub(r"[^a-zA-Z0-9]+", "-", text).strip("-").lower()
    return value or "item"


def build_item(url: str) -> dict:
    parsed = urllib.parse.urlparse(url)
    host = parsed.netloc or "unknown-source"
    path_slug = slugify(parsed.path or "content")
    digest = hashlib.sha1(url.encode("utf-8")).hexdigest()[:10]
    now = dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
    return {
        "id": digest,
        "title": f"Curated content from {host}",
        "url": url,
        "summary": "Replace this placeholder summary with a curated 1-3 line description.",
        "source": host,
        "created_at": now,
        "tags": [path_slug],
    }


def to_yaml(item: dict) -> str:
    lines = []
    for key in ("id", "title", "url", "summary", "source", "created_at"):
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
    args = parser.parse_args()

    input_path = pathlib.Path(args.input)
    output_dir = pathlib.Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    urls = [line.strip() for line in input_path.read_text(encoding="utf-8").splitlines() if line.strip()]
    today = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%d")

    for url in urls:
        item = build_item(url)
        parsed = urllib.parse.urlparse(url)
        slug = slugify((parsed.netloc + "-" + parsed.path).strip("-"))
        filename = f"{today}-{slug}-{item['id']}.yml"
        (output_dir / filename).write_text(to_yaml(item), encoding="utf-8")

    print(f"Generated {len(urls)} curated YAML files in {output_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

