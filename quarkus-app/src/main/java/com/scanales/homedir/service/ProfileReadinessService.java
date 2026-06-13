package com.scanales.homedir.service;

import com.scanales.homedir.model.UserProfile;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class ProfileReadinessService {

  public static final String FIELD_NAME = "name";
  public static final String FIELD_DESCRIPTION = "description";
  public static final String FIELD_AVATAR = "avatar";
  public static final String FIELD_ROLE_OR_INTERESTS = "role_or_interests";

  public Readiness evaluate(UserProfile profile, String displayName, String avatarUrl) {
    List<String> missingFields = new ArrayList<>();
    UserProfile.SpeakerProfile speakerProfile = profile != null ? profile.getSpeakerProfile() : null;

    if (isBlank(displayName)) {
      missingFields.add(FIELD_NAME);
    }
    if (isBlank(avatarUrl)) {
      missingFields.add(FIELD_AVATAR);
    }
    if (speakerProfile == null || isBlank(speakerProfile.bio())) {
      missingFields.add(FIELD_DESCRIPTION);
    }
    if (speakerProfile == null
        || (isBlank(speakerProfile.headline()) && isBlank(speakerProfile.topicsCsv()))) {
      missingFields.add(FIELD_ROLE_OR_INTERESTS);
    }

    return new Readiness(missingFields.isEmpty(), List.copyOf(missingFields));
  }

  private boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  public record Readiness(boolean ready, List<String> missingFields) {}
}
