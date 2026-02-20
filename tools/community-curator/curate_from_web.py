#!/usr/bin/env python3
"""Curate community content from tech/open-source/AI feeds and trending signals.

This script:
- Fetches candidates from curated RSS/Atom feeds and Hacker News search API.
- Scores relevance for ADev themes (AI engineering, platform engineering, cloud native, security, developer experience).
- Reads existing published content + vote aggregates from Homedir API to bias future picks.
- Deduplicates with local history and current remote content.
- Emits one YAML item per selected link using Homedir content schema.
"""

from __future__ import annotations

import argparse
import datetime as dt
import email.utils
import hashlib
import html
import json
import pathlib
import re
import sys
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from typing import Any, Dict, Iterable, List, Optional, Tuple

FEED_SOURCES = [
    {"name": "GitHub Blog", "url": "https://github.blog/feed/", "source": "github.blog"},
    {"name": "CNCF Blog", "url": "https://www.cncf.io/feed/", "source": "cncf.io"},
    {"name": "Kubernetes Blog", "url": "https://kubernetes.io/feed.xml", "source": "kubernetes.io"},
    {"name": "Docker Blog", "url": "https://www.docker.com/blog/feed/", "source": "docker.com"},
    {"name": "AWS Open Source Blog", "url": "https://aws.amazon.com/blogs/opensource/feed/", "source": "aws.amazon.com"},
    {"name": "Stack Overflow Blog", "url": "https://stackoverflow.blog/feed/", "source": "stackoverflow.blog"},
    {"name": "Hugging Face Blog", "url": "https://huggingface.co/blog/feed.xml", "source": "huggingface.co"},
    {"name": "OpenAI News", "url": "https://openai.com/news/rss.xml", "source": "openai.com"},
]

HN_QUERIES = [
    "platform engineering",
    "open source ai",
    "developer tools",
    "kubernetes",
    "llmops",
    "agentic coding",
]

TRUSTED_SOURCE_BONUS = {
    "github.blog": 0.6,
    "openai.com": 0.6,
    "cncf.io": 0.5,
    "kubernetes.io": 0.5,
    "docker.com": 0.4,
    "aws.amazon.com": 0.4,
    "stackoverflow.blog": 0.3,
    "huggingface.co": 0.4,
}

TRUSTED_TRENDING_DOMAINS = {
    "github.blog",
    "openai.com",
    "cncf.io",
    "kubernetes.io",
    "docker.com",
    "aws.amazon.com",
    "huggingface.co",
    "stackoverflow.blog",
    "cloudflare.com",
    "microsoft.com",
    "googleblog.com",
    "research.google",
    "arxiv.org",
}

MEDIA_VIDEO_HOST_HINTS = {
    "youtube.com",
    "youtu.be",
    "vimeo.com",
    "tiktok.com",
    "x.com",
    "twitter.com",
}
MEDIA_PODCAST_HOST_HINTS = {
    "spotify.com",
    "open.spotify.com",
    "soundcloud.com",
    "podcasts.apple.com",
    "anchor.fm",
}

TOPIC_TERMS = {
    "ai-engineering": [
        "ai",
        "artificial intelligence",
        "llm",
        "llmops",
        "agent",
        "model",
        "inference",
        "genai",
        "machine learning",
    ],
    "platform-engineering": [
        "platform engineering",
        "platform team",
        "developer platform",
        "internal developer platform",
        "idp",
        "golden path",
        "backstage",
    ],
    "cloud-native": [
        "cloud native",
        "kubernetes",
        "k8s",
        "devops",
        "sre",
        "ci/cd",
        "observability",
        "terraform",
        "helm",
        "argo",
    ],
    "security": [
        "security",
        "secops",
        "appsec",
        "supply chain",
        "zero trust",
        "sbom",
        "slsa",
        "cve",
        "vulnerability",
    ],
    "developer-experience": [
        "developer",
        "developers",
        "developer experience",
        "devex",
        "programming",
        "coding",
        "sdk",
        "api",
        "framework",
        "tooling",
        "open source",
        "oss",
        "github",
        "gitlab",
        "maintainer",
    ],
    "trending-tech": [
        "release",
        "announcing",
        "launch",
        "state of",
        "benchmark",
        "roadmap",
        "new",
    ],
}

TAG_BIAS_ALIASES = {
    "ai-engineering": ["ai", "llmops", "genai"],
    "platform-engineering": ["platform-engineering", "platform", "open-source", "developers"],
    "cloud-native": ["cloud-native", "devops", "kubernetes", "sre"],
    "security": ["security", "security-supply-chain", "appsec"],
    "developer-experience": ["developer-experience", "developers", "open-source"],
}

HTML_TAG_RE = re.compile(r"<[^>]+>")
WHITESPACE_RE = re.compile(r"\s+")
META_DESC_RE = re.compile(
    r"<meta[^>]+(?:name|property)=[\"'](?:description|og:description)[\"'][^>]+content=[\"']([^\"']+)[\"'][^>]*>",
    re.IGNORECASE,
)
META_IMAGE_RE = re.compile(
    r"<meta[^>]+(?:name|property)=[\"'](?:og:image|twitter:image)[\"'][^>]+content=[\"']([^\"']+)[\"'][^>]*>",
    re.IGNORECASE,
)


def now_utc() -> dt.datetime:
    return dt.datetime.now(dt.timezone.utc).replace(microsecond=0)


def to_iso(value: dt.datetime) -> str:
    return value.isoformat().replace("+00:00", "Z")


def local_name(tag: str) -> str:
    return tag.split("}")[-1] if "}" in tag else tag


def fetch_bytes(url: str, timeout: int = 12) -> bytes:
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": "homedir-community-curator/1.0 (+https://homedir.opensourcesantiago.io)",
            "Accept": "application/json, application/xml, text/xml, text/html;q=0.9,*/*;q=0.8",
        },
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.read()


def fetch_json(url: str, timeout: int = 12) -> Any:
    return json.loads(fetch_bytes(url, timeout=timeout).decode("utf-8", errors="replace"))


def strip_html(text: str) -> str:
    cleaned = HTML_TAG_RE.sub(" ", text or "")
    cleaned = html.unescape(cleaned)
    return WHITESPACE_RE.sub(" ", cleaned).strip()


def normalize_url(raw: str) -> Optional[str]:
    if not raw:
        return None
    try:
        parsed = urllib.parse.urlparse(raw.strip())
        if parsed.scheme not in {"http", "https"} or not parsed.netloc:
            return None
        query = urllib.parse.parse_qsl(parsed.query, keep_blank_values=True)
        query = [
            (k, v)
            for (k, v) in query
            if not k.lower().startswith("utm_") and k.lower() not in {"fbclid", "gclid", "mc_cid", "mc_eid"}
        ]
        normalized = parsed._replace(
            netloc=parsed.netloc.lower(),
            query=urllib.parse.urlencode(query, doseq=True),
            fragment="",
        )
        return urllib.parse.urlunparse(normalized)
    except Exception:
        return None


def source_from_url(url: str) -> str:
    host = urllib.parse.urlparse(url).netloc.lower()
    if host.startswith("www."):
        host = host[4:]
    return host


def parse_datetime(raw: Optional[str]) -> Optional[dt.datetime]:
    if not raw:
        return None
    candidate = raw.strip()
    if not candidate:
        return None
    try:
        return email.utils.parsedate_to_datetime(candidate).astimezone(dt.timezone.utc)
    except Exception:
        pass
    try:
        normalized = candidate.replace("Z", "+00:00")
        parsed = dt.datetime.fromisoformat(normalized)
        if parsed.tzinfo is None:
            parsed = parsed.replace(tzinfo=dt.timezone.utc)
        return parsed.astimezone(dt.timezone.utc)
    except Exception:
        return None


def get_text(elem: Optional[ET.Element]) -> str:
    if elem is None:
        return ""
    return strip_html("".join(elem.itertext()))


def parse_feed(source: Dict[str, str], max_items: int) -> List[Dict[str, Any]]:
    out: List[Dict[str, Any]] = []
    raw = fetch_bytes(source["url"])
    root = ET.fromstring(raw)

    items = [e for e in root.iter() if local_name(e.tag) == "item"]
    if items:
        for item in items[:max_items]:
            title = get_text(next((c for c in item if local_name(c.tag) == "title"), None))
            link = get_text(next((c for c in item if local_name(c.tag) == "link"), None))
            description = get_text(next((c for c in item if local_name(c.tag) == "description"), None))
            pub_raw = get_text(next((c for c in item if local_name(c.tag) in {"pubDate", "published", "updated"}), None))
            categories = [
                get_text(c)
                for c in item
                if local_name(c.tag) in {"category", "tag"} and get_text(c)
            ]
            url = normalize_url(link)
            if not title or not url:
                continue
            out.append(
                {
                    "title": title,
                    "url": url,
                    "summary": description,
                    "published_at": to_iso(parse_datetime(pub_raw)) if parse_datetime(pub_raw) else None,
                    "source": source.get("source") or source_from_url(url),
                    "tags_seed": categories,
                    "origin": source["name"],
                }
            )
        return out

    entries = [e for e in root.iter() if local_name(e.tag) == "entry"]
    for entry in entries[:max_items]:
        title = get_text(next((c for c in entry if local_name(c.tag) == "title"), None))
        link_elem = next(
            (
                c
                for c in entry
                if local_name(c.tag) == "link"
                and (c.attrib.get("rel") in {None, "", "alternate"})
                and c.attrib.get("href")
            ),
            None,
        )
        link = link_elem.attrib.get("href", "") if link_elem is not None else ""
        summary = get_text(next((c for c in entry if local_name(c.tag) in {"summary", "content"}), None))
        pub_raw = get_text(next((c for c in entry if local_name(c.tag) in {"published", "updated"}), None))
        categories = [
            (c.attrib.get("term") or get_text(c)).strip()
            for c in entry
            if local_name(c.tag) in {"category", "tag"}
        ]
        url = normalize_url(link)
        if not title or not url:
            continue
        out.append(
            {
                "title": title,
                "url": url,
                "summary": summary,
                "published_at": to_iso(parse_datetime(pub_raw)) if parse_datetime(pub_raw) else None,
                "source": source.get("source") or source_from_url(url),
                "tags_seed": [t for t in categories if t],
                "origin": source["name"],
            }
        )
    return out


def parse_hn(max_per_query: int) -> List[Dict[str, Any]]:
    out: List[Dict[str, Any]] = []
    for query in HN_QUERIES:
        api_url = (
            "https://hn.algolia.com/api/v1/search_by_date?"
            + urllib.parse.urlencode({"query": query, "tags": "story", "hitsPerPage": max_per_query})
        )
        payload = fetch_json(api_url)
        for hit in payload.get("hits", []):
            raw_url = normalize_url(hit.get("url") or "")
            title = strip_html(hit.get("title") or "")
            if not raw_url or not title:
                continue
            points = hit.get("points") or 0
            out.append(
                {
                    "title": title,
                    "url": raw_url,
                    "summary": f"Trending on Hacker News ({points} points).",
                    "published_at": hit.get("created_at"),
                    "source": source_from_url(raw_url),
                    "tags_seed": ["trending-tech", "hn"],
                    "origin": "Hacker News",
                    "hn_points": float(points),
                }
            )
    return out


def infer_topic_hits(text: str) -> Dict[str, float]:
    lowered = text.lower()
    scores: Dict[str, float] = {}
    for topic, terms in TOPIC_TERMS.items():
        hits = 0
        for term in terms:
            if term in lowered:
                hits += 1
        if hits:
            scores[topic] = float(hits)
    return scores


def fetch_meta_preview(url: str) -> Tuple[str, str]:
    try:
        content = fetch_bytes(url, timeout=8)[:220000].decode("utf-8", errors="replace")
    except Exception:
        return "", ""
    match_desc = META_DESC_RE.search(content)
    description = strip_html(match_desc.group(1)) if match_desc else ""
    match_image = META_IMAGE_RE.search(content)
    image_raw = normalize_url(html.unescape(match_image.group(1))) if match_image else None
    return description, (image_raw or "")


def slugify(text: str) -> str:
    value = re.sub(r"[^a-zA-Z0-9]+", "-", text).strip("-").lower()
    return value or "item"


def yaml_quote(text: str) -> str:
    return '"' + (text or "").replace("\\", "\\\\").replace('"', '\\"') + '"'


def load_history(history_file: pathlib.Path) -> Dict[str, Any]:
    if not history_file.exists():
        return {"urls": [], "records": []}
    try:
        return json.loads(history_file.read_text(encoding="utf-8"))
    except Exception:
        return {"urls": [], "records": []}


def fetch_existing_remote(api_base: str) -> Tuple[set[str], Dict[str, float]]:
    existing_urls: set[str] = set()
    tag_scores: Dict[str, List[float]] = {}
    offset = 0
    page_size = 100
    for _ in range(15):
        url = f"{api_base.rstrip('/')}/api/community/content?view=new&limit={page_size}&offset={offset}"
        payload = fetch_json(url)
        items = payload.get("items") or []
        if not items:
            break
        for item in items:
            raw_url = normalize_url(item.get("url") or "")
            if raw_url:
                existing_urls.add(raw_url)
            counts = item.get("vote_counts") or {}
            score_base = (
                3.0 * float(counts.get("must_see") or 0)
                + float(counts.get("recommended") or 0)
                - 0.5 * float(counts.get("not_for_me") or 0)
            )
            for tag in item.get("tags") or []:
                key = strip_html(str(tag)).strip().lower()
                if not key:
                    continue
                tag_scores.setdefault(key, []).append(score_base)
        if len(items) < page_size:
            break
        offset += page_size

    tag_bias: Dict[str, float] = {}
    for tag, values in tag_scores.items():
        if not values:
            continue
        avg = sum(values) / len(values)
        # Normalized bias in [-1, 1] for future ranking.
        tag_bias[tag] = max(-1.0, min(1.0, avg / 5.0))
    return existing_urls, tag_bias


def score_candidate(candidate: Dict[str, Any], tag_bias: Dict[str, float]) -> Tuple[float, List[str]]:
    text = " ".join(
        [
            candidate.get("title") or "",
            candidate.get("summary") or "",
            candidate.get("source") or "",
            " ".join(candidate.get("tags_seed") or []),
        ]
    )
    topic_hits = infer_topic_hits(text)
    tags = sorted(topic_hits.keys(), key=lambda t: topic_hits[t], reverse=True)
    base = sum(topic_hits.values())

    published = parse_datetime(candidate.get("published_at") or "")
    recency_bonus = 0.0
    if published:
        days = max(0.0, (now_utc() - published).total_seconds() / 86400.0)
        recency_bonus = max(0.0, (30.0 - days) / 30.0) * 1.2

    source_bonus = TRUSTED_SOURCE_BONUS.get(candidate.get("source") or "", 0.0)
    hn_bonus = min(1.0, float(candidate.get("hn_points") or 0.0) / 100.0)

    matched_bias_values: List[float] = []
    for tag in tags:
        aliases = [tag] + TAG_BIAS_ALIASES.get(tag, [])
        alias_scores = [tag_bias.get(alias, 0.0) for alias in aliases]
        matched_bias_values.append(max(alias_scores) if alias_scores else 0.0)
    eval_bonus = (sum(matched_bias_values) / len(matched_bias_values)) if matched_bias_values else 0.0

    total = base + recency_bonus + source_bonus + hn_bonus + eval_bonus
    return total, tags


def infer_media_type(candidate: Dict[str, Any], inferred_tags: List[str]) -> str:
    url = normalize_url(candidate.get("url") or "") or ""
    host = source_from_url(url)
    text = " ".join(
        [
            candidate.get("title") or "",
            candidate.get("summary") or "",
            " ".join(candidate.get("tags_seed") or []),
            " ".join(inferred_tags or []),
        ]
    ).lower()

    if any(hint in host for hint in MEDIA_VIDEO_HOST_HINTS):
        return "video_story"
    if any(hint in host for hint in MEDIA_PODCAST_HOST_HINTS):
        return "podcast"
    if "video" in text or "livestream" in text or "shorts" in text:
        return "video_story"
    if "podcast" in text or "episode" in text or "audio" in text:
        return "podcast"
    return "article_blog"


def youtube_thumbnail(url: str) -> str:
    parsed = urllib.parse.urlparse(url)
    host = parsed.netloc.lower()
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


def build_item(candidate: Dict[str, Any], inferred_tags: List[str], created_at: dt.datetime) -> Dict[str, Any]:
    url = candidate["url"]
    item_id = hashlib.sha1(url.encode("utf-8")).hexdigest()[:12]
    media_type = infer_media_type(candidate, inferred_tags)
    summary = strip_html(candidate.get("summary") or "")
    thumbnail_url = normalize_url(candidate.get("thumbnail_url") or "") or ""
    if len(summary) < 60 or not thumbnail_url:
        meta_desc, meta_image = fetch_meta_preview(url)
        if len(meta_desc) >= 40:
            if len(summary) < 60:
                summary = meta_desc
        if not thumbnail_url and meta_image:
            thumbnail_url = meta_image
    if not thumbnail_url and media_type == "video_story":
        thumbnail_url = youtube_thumbnail(url)
    if len(summary) > 320:
        summary = summary[:317].rstrip() + "..."
    if not summary:
        summary = "Curated technology content relevant for modern software teams."

    tags = []
    for tag in inferred_tags + ["technology", "community-curated"]:
        cleaned = strip_html(str(tag)).lower().strip()
        if cleaned and cleaned not in tags:
            tags.append(cleaned)
    tags = tags[:6]

    published = parse_datetime(candidate.get("published_at") or "")
    return {
        "id": item_id,
        "title": strip_html(candidate.get("title") or "Untitled content"),
        "url": url,
        "summary": summary,
        "source": candidate.get("source") or source_from_url(url),
        "published_at": to_iso(published) if published else None,
        "created_at": to_iso(created_at),
        "media_type": media_type,
        "thumbnail_url": thumbnail_url or None,
        "tags": tags,
        "author": None,
    }


def write_yaml_item(path: pathlib.Path, item: Dict[str, Any]) -> None:
    lines = [
        f"id: {yaml_quote(item['id'])}",
        f"title: {yaml_quote(item['title'])}",
        f"url: {yaml_quote(item['url'])}",
        f"summary: {yaml_quote(item['summary'])}",
        f"source: {yaml_quote(item['source'])}",
    ]
    if item.get("published_at"):
        lines.append(f"published_at: {yaml_quote(item['published_at'])}")
    lines.append(f"created_at: {yaml_quote(item['created_at'])}")
    lines.append(f"media_type: {yaml_quote(item['media_type'])}")
    if item.get("thumbnail_url"):
        lines.append(f"thumbnail_url: {yaml_quote(item['thumbnail_url'])}")
    tags = item.get("tags") or []
    if tags:
        lines.append("tags:")
        for tag in tags:
            lines.append(f"  - {yaml_quote(tag)}")
    if item.get("author"):
        lines.append(f"author: {yaml_quote(item['author'])}")

    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Curate community YAML content from web feeds and trending signals")
    parser.add_argument("--output-dir", required=True, help="Directory where YAML files will be written")
    parser.add_argument(
        "--state-file",
        default="tools/community-curator/state/history.json",
        help="JSON file with previously published URLs and metadata",
    )
    parser.add_argument(
        "--api-base",
        default="https://homedir.opensourcesantiago.io",
        help="Homedir base URL used to read existing content and vote aggregates",
    )
    parser.add_argument("--limit", type=int, default=15, help="Maximum number of new items to generate")
    parser.add_argument("--max-per-feed", type=int, default=12, help="Maximum entries fetched per feed/query")
    parser.add_argument("--min-score", type=float, default=2.0, help="Minimum relevance score to keep a candidate")
    parser.add_argument("--dry-run", action="store_true", help="Do not write files, only print selection")
    return parser


def main() -> int:
    args = build_parser().parse_args()

    output_dir = pathlib.Path(args.output_dir)
    state_file = pathlib.Path(args.state_file)
    output_dir.mkdir(parents=True, exist_ok=True)
    state_file.parent.mkdir(parents=True, exist_ok=True)

    history = load_history(state_file)
    history_urls = {normalize_url(u) for u in history.get("urls", [])}
    history_urls.discard(None)

    try:
        remote_urls, tag_bias = fetch_existing_remote(args.api_base)
    except Exception as exc:
        print(f"WARN: unable to fetch existing remote content from {args.api_base}: {exc}", file=sys.stderr)
        remote_urls, tag_bias = set(), {}

    print(f"Existing remote items: {len(remote_urls)}")
    print(f"Existing history URLs: {len(history_urls)}")
    if tag_bias:
        top_bias = sorted(tag_bias.items(), key=lambda kv: kv[1], reverse=True)[:8]
        print("Historical tag bias:", ", ".join(f"{k}={v:+.2f}" for k, v in top_bias))
    else:
        print("Historical tag bias: none (cold start)")

    candidates: List[Dict[str, Any]] = []

    for source in FEED_SOURCES:
        try:
            entries = parse_feed(source, max_items=args.max_per_feed)
            candidates.extend(entries)
            print(f"Feed {source['name']}: {len(entries)} candidates")
        except Exception as exc:
            print(f"WARN: feed failed {source['name']} ({source['url']}): {exc}", file=sys.stderr)

    try:
        hn_entries = parse_hn(max_per_query=args.max_per_feed)
        candidates.extend(hn_entries)
        print(f"Hacker News queries: {len(hn_entries)} candidates")
    except Exception as exc:
        print(f"WARN: Hacker News fetch failed: {exc}", file=sys.stderr)

    # Deduplicate by canonical URL while keeping the highest scoring candidate.
    scored_map: Dict[str, Dict[str, Any]] = {}
    for candidate in candidates:
        url = normalize_url(candidate.get("url") or "")
        if not url:
            continue
        if url in history_urls or url in remote_urls:
            continue
        candidate["url"] = url
        score, inferred_tags = score_candidate(candidate, tag_bias)
        if score < args.min_score:
            continue
        source = candidate.get("source") or source_from_url(url)
        if candidate.get("origin") == "Hacker News":
            if source not in TRUSTED_TRENDING_DOMAINS and score < 8.0:
                # Avoid low-signal/random links from generic trending streams.
                continue
        existing = scored_map.get(url)
        payload = dict(candidate)
        payload["score"] = score
        payload["inferred_tags"] = inferred_tags
        if existing is None or payload["score"] > existing["score"]:
            scored_map[url] = payload

    ranked = sorted(scored_map.values(), key=lambda c: c["score"], reverse=True)
    selected = ranked[: max(1, args.limit)]

    print(f"Selected new items: {len(selected)}")

    created = now_utc()
    manifest: Dict[str, Any] = {
        "generated_at": to_iso(created),
        "api_base": args.api_base,
        "limit": args.limit,
        "min_score": args.min_score,
        "selected": [],
    }

    for candidate in selected:
        item = build_item(candidate, candidate.get("inferred_tags") or [], created)
        slug = slugify(item["title"])[:64]
        date_prefix = created.strftime("%Y%m%d")
        filename = f"{date_prefix}-{slug}-{item['id']}.yml"
        out_path = output_dir / filename

        if not args.dry_run:
            write_yaml_item(out_path, item)

        manifest["selected"].append(
            {
                "id": item["id"],
                "title": item["title"],
                "url": item["url"],
                "source": item["source"],
                "media_type": item["media_type"],
                "thumbnail_url": item.get("thumbnail_url"),
                "score": round(float(candidate["score"]), 3),
                "tags": item["tags"],
                "file": filename,
            }
        )

    manifest_path = output_dir / "manifest.json"
    if not args.dry_run:
        manifest_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False), encoding="utf-8")

        urls_next = sorted(
            set(history.get("urls", []))
            | {entry["url"] for entry in manifest["selected"] if entry.get("url")}
        )
        records_next = list(history.get("records", []))
        for entry in manifest["selected"]:
            records_next.append(
                {
                    "id": entry["id"],
                    "title": entry["title"],
                    "url": entry["url"],
                    "source": entry["source"],
                    "tags": entry["tags"],
                    "score": entry["score"],
                    "added_at": to_iso(created),
                }
            )
        history_payload = {
            "updated_at": to_iso(created),
            "urls": urls_next,
            "records": records_next[-2000:],
        }
        state_file.write_text(json.dumps(history_payload, indent=2, ensure_ascii=False), encoding="utf-8")

    for entry in manifest["selected"]:
        print(f"- {entry['score']:>5} | {entry['source']:<24} | {entry['title']}")

    if not manifest["selected"]:
        print("No new items passed filters; nothing generated.")
    else:
        print(f"Wrote {len(manifest['selected'])} items to: {output_dir}")
        print(f"Manifest: {manifest_path}")
        print(f"History: {state_file}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
