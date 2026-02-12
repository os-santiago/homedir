package com.scanales.eventflow.cfp;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

public record CfpSubmission(
    String id,
    @JsonProperty("event_id") String eventId,
    @JsonProperty("proposer_user_id") String proposerUserId,
    @JsonProperty("proposer_name") String proposerName,
    String title,
    String summary,
    @JsonProperty("abstract_text") String abstractText,
    String level,
    String format,
    @JsonProperty("duration_min") Integer durationMin,
    String language,
    String track,
    List<String> tags,
    List<String> links,
    CfpSubmissionStatus status,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("updated_at") Instant updatedAt,
    @JsonProperty("moderated_at") Instant moderatedAt,
    @JsonProperty("moderated_by") String moderatedBy,
    @JsonProperty("moderation_note") String moderationNote) {
}