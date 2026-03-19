package com.scanales.homedir.volunteers;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.time.Instant;

@RegisterForReflection
public record VolunteerLoungeMessage(
    String id,
    @JsonProperty("event_id") String eventId,
    @JsonProperty("message_type") String messageType,
    @JsonProperty("parent_id") String parentId,
    @JsonProperty("user_id") String userId,
    @JsonProperty("user_name") String userName,
    String body,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("updated_at") Instant updatedAt) {

  public String normalizedMessageType() {
    String raw = messageType == null ? "" : messageType.trim().toLowerCase(java.util.Locale.ROOT);
    return raw.isBlank() ? VolunteerLoungeMessageType.POST.apiValue() : raw;
  }
}
