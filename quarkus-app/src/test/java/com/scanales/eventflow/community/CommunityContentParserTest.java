package com.scanales.eventflow.community;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

public class CommunityContentParserTest {

  @Test
  void parsesValidYamlItem() throws Exception {
    Path file = Files.createTempFile("community-valid", ".yml");
    Files.writeString(
        file,
        """
        id: item-1
        title: "Article One"
        url: "https://example.org/article"
        summary: "Short summary"
        source: "example.org"
        thumbnail_url: "https://cdn.example.org/cover.png"
        created_at: "2026-02-07T12:00:00Z"
        published_at: "2026-02-06T11:00:00Z"
        media_type: "video_story"
        tags:
          - java
          - quarkus
        author: "OSS Bot"
        """);

    CommunityContentParser parser = new CommunityContentParser();
    var parsed = parser.parse(file);

    assertTrue(parsed.isValid());
    assertNotNull(parsed.item());
    assertEquals("item-1", parsed.item().id());
    assertEquals("Article One", parsed.item().title());
    assertEquals("https://example.org/article", parsed.item().url());
    assertEquals("https://cdn.example.org/cover.png", parsed.item().thumbnailUrl());
    assertEquals("video_story", parsed.item().mediaType());
    assertEquals(2, parsed.item().tags().size());
  }

  @Test
  void rejectsInvalidOrIncompleteItem() throws Exception {
    Path file = Files.createTempFile("community-invalid", ".yml");
    Files.writeString(
        file,
        """
        id: item-2
        title: "Bad Item"
        summary: "Missing URL and created date"
        source: "example.org"
        """);

    CommunityContentParser parser = new CommunityContentParser();
    var parsed = parser.parse(file);

    assertFalse(parsed.isValid());
    assertNotNull(parsed.error());
  }

  @Test
  void defaultsMediaTypeToArticleBlogWhenMissing() throws Exception {
    Path file = Files.createTempFile("community-media-default", ".yml");
    Files.writeString(
        file,
        """
        id: item-4
        title: "Default media"
        url: "https://example.org/default-media"
        summary: "Media defaults to article blog."
        source: "example.org"
        created_at: "2026-02-07T12:00:00Z"
        """);

    CommunityContentParser parser = new CommunityContentParser();
    var parsed = parser.parse(file);

    assertTrue(parsed.isValid());
    assertNotNull(parsed.item());
    assertEquals("article_blog", parsed.item().mediaType());
  }
}
