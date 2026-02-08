package com.scanales.eventflow.community;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

public record CommunitySubmission(
    String id,
    @JsonProperty("user_id") String userId,
    @JsonProperty("user_name") String userName,
    String title,
    String url,
    String summary,
    String source,
    List<String> tags,
    @JsonProperty("created_at") Instant createdAt,
    CommunitySubmissionStatus status,
    @JsonProperty("moderated_at") Instant moderatedAt,
    @JsonProperty("moderated_by") String moderatedBy,
    @JsonProperty("moderation_note") String moderationNote,
    @JsonProperty("content_id") String contentId,
    @JsonProperty("content_file") String contentFile) {}
