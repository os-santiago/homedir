Task: generate a candidate batch for HomeDir Community Picks.

Input:
- Time window: last 14 days preferred.
- Desired volume: 20 candidates.
- Desired output mix:
  - 30% video_story
  - 30% podcast
  - 40% article_blog

Selection criteria:
- Relevance to at least one target domain:
  - AI engineering
  - Platform Engineering
  - Cloud Native
  - Security
  - Developer Experience
- Prefer sources with clear technical depth and practical outcomes.
- Exclude duplicates by URL and title intent.
- Exclude paywalled links unless summary value is still clear and source is strong.

Return JSON array with this shape:

```json
[
  {
    "title": "string",
    "url": "https://...",
    "summary": "1-3 lines",
    "source": "domain or source name",
    "published_at": "ISO-8601 or null",
    "media_type": "video_story|podcast|article_blog",
    "tags": ["ai-engineering", "platform-engineering"]
  }
]
```

Additional rules:
- Keep summary under 320 chars.
- Keep max 6 tags.
- Use lowercase kebab-case tags.
