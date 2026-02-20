package com.scanales.eventflow.community;

import java.util.Locale;

public final class CommunityContentMedia {
  public static final String ALL = "all";
  public static final String VIDEO_STORY = "video_story";
  public static final String PODCAST = "podcast";
  public static final String ARTICLE_BLOG = "article_blog";

  private CommunityContentMedia() {}

  public static String normalizeItemType(String raw) {
    if (raw == null || raw.isBlank()) {
      return ARTICLE_BLOG;
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    return switch (normalized) {
      case "video_story", "videostory", "video", "story", "short_video" -> VIDEO_STORY;
      case "podcast", "audio", "audio_story" -> PODCAST;
      case "article_blog", "article", "blog", "post", "text" -> ARTICLE_BLOG;
      default -> ARTICLE_BLOG;
    };
  }

  public static String normalizeFilter(String raw) {
    if (raw == null || raw.isBlank()) {
      return ALL;
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    return switch (normalized) {
      case VIDEO_STORY -> VIDEO_STORY;
      case PODCAST -> PODCAST;
      case ARTICLE_BLOG -> ARTICLE_BLOG;
      default -> ALL;
    };
  }

  public static boolean matchesFilter(String itemType, String filterType) {
    String normalizedFilter = normalizeFilter(filterType);
    if (ALL.equals(normalizedFilter)) {
      return true;
    }
    return normalizeItemType(itemType).equals(normalizedFilter);
  }
}

