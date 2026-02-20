Normalize the following candidate into Homedir Community YAML.

Input object:

```json
{
  "title": "...",
  "url": "...",
  "summary": "...",
  "source": "...",
  "published_at": "...",
  "media_type": "...",
  "tags": ["..."]
}
```

Rules:
- Ensure URL is canonical `http/https`.
- Keep summary concise and factual (<= 320 chars).
- Keep tags lowercase kebab-case.
- Keep at most 6 tags.
- Allowed `media_type`: `video_story`, `podcast`, `article_blog`.
- Add `created_at` in UTC ISO-8601.
- Add deterministic id as first 12 chars of SHA-1(url).
- Optionally include `thumbnail_url` if known and valid.

Return YAML with this exact structure:

```yaml
id: "<id>"
title: "<title>"
url: "<url>"
summary: "<summary>"
source: "<source>"
published_at: "<iso-or-omit>"
created_at: "<iso-utc>"
media_type: "<video_story|podcast|article_blog>"
thumbnail_url: "<optional>"
tags:
  - "<tag1>"
  - "<tag2>"
author: "<optional>"
```
