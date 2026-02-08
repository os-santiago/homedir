package com.scanales.eventflow.community;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class CommunityContentParser {

  private final ObjectMapper yamlMapper = baseMapper(new ObjectMapper(new YAMLFactory()));

  public ParseOutcome parse(Path file) {
    try {
      RawItem raw = yamlMapper.readValue(file.toFile(), RawItem.class);
      return validate(raw);
    } catch (Exception e) {
      return ParseOutcome.invalid("failed_to_parse: " + e.getMessage());
    }
  }

  private ParseOutcome validate(RawItem raw) {
    if (raw == null) {
      return ParseOutcome.invalid("empty_payload");
    }
    String id = sanitizeText(raw.id());
    if (id == null) {
      return ParseOutcome.invalid("missing_id");
    }
    String title = sanitizeText(raw.title());
    if (title == null) {
      return ParseOutcome.invalid("missing_title");
    }
    String summary = sanitizeText(raw.summary());
    if (summary == null) {
      return ParseOutcome.invalid("missing_summary");
    }
    String source = sanitizeText(raw.source());
    if (source == null) {
      return ParseOutcome.invalid("missing_source");
    }
    String url = sanitizeUrl(raw.url());
    if (url == null) {
      return ParseOutcome.invalid("invalid_url");
    }
    Instant createdAt = parseInstant(raw.createdAt());
    if (createdAt == null) {
      return ParseOutcome.invalid("invalid_created_at");
    }
    Instant publishedAt = parseInstant(raw.publishedAt());
    List<String> tags = sanitizeTags(raw.tags());
    String author = sanitizeText(raw.author());
    return ParseOutcome.valid(
        new CommunityContentItem(
            id,
            title,
            url,
            summary,
            source,
            createdAt,
            publishedAt,
            tags,
            author));
  }

  private static ObjectMapper baseMapper(ObjectMapper mapper) {
    return mapper
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .registerModule(new JavaTimeModule());
  }

  private static Instant parseInstant(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return Instant.parse(raw.trim());
    } catch (Exception e) {
      return null;
    }
  }

  private static String sanitizeText(String raw) {
    if (raw == null) {
      return null;
    }
    String cleaned = raw.replaceAll("[\\p{Cntrl}&&[^\n\t]]", "").trim();
    if (cleaned.isEmpty()) {
      return null;
    }
    return cleaned;
  }

  private static String sanitizeUrl(String raw) {
    return CommunityUrlNormalizer.normalize(raw);
  }

  private static List<String> sanitizeTags(List<String> rawTags) {
    if (rawTags == null || rawTags.isEmpty()) {
      return List.of();
    }
    Set<String> normalized = new LinkedHashSet<>();
    for (String tag : rawTags) {
      String cleaned = sanitizeText(tag);
      if (cleaned != null) {
        normalized.add(cleaned);
      }
    }
    if (normalized.isEmpty()) {
      return List.of();
    }
    return new ArrayList<>(normalized);
  }

  public record ParseOutcome(CommunityContentItem item, String error) {
    public static ParseOutcome valid(CommunityContentItem item) {
      return new ParseOutcome(item, null);
    }

    public static ParseOutcome invalid(String error) {
      return new ParseOutcome(null, error);
    }

    public boolean isValid() {
      return item != null;
    }
  }

  public record RawItem(
      String id,
      String title,
      String url,
      String summary,
      String source,
      @JsonProperty("published_at") String publishedAt,
      @JsonProperty("created_at") String createdAt,
      List<String> tags,
      String author) {
  }
}
