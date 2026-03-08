package com.scanales.eventflow.volunteers;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record VolunteerApplication(
    String id,
    @JsonProperty("event_id") String eventId,
    @JsonProperty("applicant_user_id") String applicantUserId,
    @JsonProperty("applicant_name") String applicantName,
    @JsonProperty("about_me") String aboutMe,
    @JsonProperty("join_reason") String joinReason,
    String differentiator,
    VolunteerApplicationStatus status,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("updated_at") Instant updatedAt,
    @JsonProperty("moderated_at") Instant moderatedAt,
    @JsonProperty("moderated_by") String moderatedBy,
    @JsonProperty("moderation_note") String moderationNote,
    @JsonProperty("rating_profile") Integer ratingProfile,
    @JsonProperty("rating_motivation") Integer ratingMotivation,
    @JsonProperty("rating_differentiator") Integer ratingDifferentiator) {
}