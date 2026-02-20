package com.scanales.eventflow.community;

import java.time.Instant;
import java.util.List;

public record CommunityContentItem(
    String id,
    String title,
    String url,
    String summary,
    String source,
    Instant createdAt,
    Instant publishedAt,
    List<String> tags,
    String author,
    String mediaType) {
}
