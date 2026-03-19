package com.scanales.homedir.service;

import com.scanales.homedir.model.UserProfile;
import com.scanales.homedir.model.QuestClass;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.logging.Logger;

@ApplicationScoped
public class UserProfileService {

  private static final Logger LOG = Logger.getLogger(UserProfileService.class);

  @Inject
  PersistenceService persistence;

  private final Map<String, UserProfile> profiles = new ConcurrentHashMap<>();

  @PostConstruct
  void init() {
    try {
      profiles.putAll(persistence.loadUserProfiles());
    } catch (Exception e) {
      LOG.warn("Unable to load user profiles; starting empty", e);
    }
  }

  public Map<String, UserProfile> allProfiles() {
    return Collections.unmodifiableMap(profiles);
  }

  public Optional<UserProfile> find(String userId) {
    if (userId == null || userId.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(profiles.get(normalize(userId)));
  }

  public UserProfile upsert(String userId, String name, String email) {
    String key = normalize(userId);
    UserProfile profile = profiles.computeIfAbsent(key, k -> new UserProfile(k, name, email, null));
    if (name != null && !name.isBlank()) {
      profile.setName(name);
    }
    if (email != null && !email.isBlank()) {
      profile.setEmail(email);
    }
    persist();
    return profile;
  }

  public void update(UserProfile profile) {
    if (profile != null && profile.getUserId() != null) {
      profiles.put(normalize(profile.getUserId()), profile);
      persist();
    }
  }

  public UserProfile updateQuestClass(String userId, QuestClass questClass) {
    if (userId == null)
      return null;
    UserProfile profile = find(userId).orElse(null);
    if (profile != null) {
      profile.setQuestClass(questClass);
      persist();
    }
    return profile;
  }

  public UserProfile addXp(String userId, int amount, String reason) {
    return addXp(userId, amount, reason, null);
  }

  public UserProfile addXp(String userId, int amount, String reason, QuestClass questClass) {
    if (userId == null)
      return null;
    UserProfile profile = find(userId).orElseGet(() -> upsert(userId, userId, userId));
    if (profile != null && amount != 0) {
      profile.setCurrentXp(Math.max(0, profile.getCurrentXp() + amount));
      if (amount > 0 && questClass != null) {
        profile.addClassXp(questClass, amount);
      }
      profile.addHistoryItem(
          new UserProfile.QuestHistoryItem(reason, amount, java.time.LocalDate.now().toString()));
      persist();
    }
    return profile;
  }

  public UserProfile linkGithub(
      String userId, String name, String email, UserProfile.GithubAccount githubAccount) {

    UserProfile profile = upsert(userId, name, email);
    UserProfile.GithubAccount updated = new UserProfile.GithubAccount(
        githubAccount.login(),
        githubAccount.profileUrl(),
        githubAccount.avatarUrl(),
        githubAccount.id(),
        githubAccount.linkedAt() != null ? githubAccount.linkedAt() : Instant.now());
    profile.setGithub(updated);
    persist();
    return profile;
  }

  public UserProfile linkDiscord(
      String userId, String name, String email, UserProfile.DiscordAccount discordAccount) {
    UserProfile profile = upsert(userId, name, email);
    UserProfile.DiscordAccount updated = new UserProfile.DiscordAccount(
        normalizeDiscordId(discordAccount.id()),
        discordAccount.handle(),
        discordAccount.profileUrl(),
        discordAccount.avatarUrl(),
        discordAccount.linkedAt() != null ? discordAccount.linkedAt() : Instant.now());
    profile.setDiscord(updated);
    persist();
    return profile;
  }

  public UserProfile unlinkDiscord(String userId) {
    if (userId == null) {
      return null;
    }
    UserProfile profile = find(userId).orElse(null);
    if (profile != null) {
      profile.setDiscord(null);
      persist();
    }
    return profile;
  }

  public UserProfile updateLocale(String userId, String locale) {
    if (userId == null)
      return null;
    UserProfile profile = find(userId).orElse(null);
    if (profile != null) {
      profile.setPreferredLocale(locale);
      persist();
    }
    return profile;
  }

  public Optional<UserProfile> findByGithubLogin(String githubLogin) {
    if (githubLogin == null || githubLogin.isBlank()) {
      return Optional.empty();
    }
    return profiles.values().stream()
        .filter(p -> p.getGithub() != null && githubLogin.equalsIgnoreCase(p.getGithub().login()))
        .findFirst();
  }

  public Optional<UserProfile> findByDiscordId(String discordId) {
    String normalized = normalizeDiscordId(discordId);
    if (normalized == null) {
      return Optional.empty();
    }
    return profiles.values().stream()
        .filter(p -> p.getDiscord() != null)
        .filter(p -> normalized.equals(normalizeDiscordId(p.getDiscord().id())))
        .findFirst();
  }

  public Optional<UserProfile> findByEmail(String email) {
    if (email == null || email.isBlank()) {
      return Optional.empty();
    }
    String normalized = normalize(email);
    return profiles.values().stream()
        .filter(p -> p.getEmail() != null)
        .filter(p -> normalized.equals(normalize(p.getEmail())))
        .findFirst();
  }

  public UserProfile activateSpeakerProfile(String userId, String name, String email) {
    UserProfile profile = upsert(userId, name, email);
    Instant now = Instant.now();
    UserProfile.SpeakerProfile current = profile.getSpeakerProfile();
    Instant activatedAt = current != null && current.activatedAt() != null ? current.activatedAt() : now;
    profile.setSpeakerProfile(
        new UserProfile.SpeakerProfile(
            true,
            current != null ? current.headline() : null,
            current != null ? current.bio() : null,
            current != null ? current.organization() : null,
            current != null ? current.website() : null,
            current != null ? current.linkedin() : null,
            current != null ? current.topicsCsv() : null,
            activatedAt,
            now));
    persist();
    return profile;
  }

  public UserProfile updateSpeakerProfile(
      String userId,
      String headline,
      String bio,
      String organization,
      String website,
      String linkedin,
      List<String> topics) {
    if (userId == null || userId.isBlank()) {
      return null;
    }
    UserProfile profile = find(userId).orElseGet(() -> upsert(userId, userId, userId));
    Instant now = Instant.now();
    UserProfile.SpeakerProfile current = profile.getSpeakerProfile();
    Instant activatedAt = current != null ? current.activatedAt() : null;
    boolean active = current != null && current.active();
    profile.setSpeakerProfile(
        new UserProfile.SpeakerProfile(
            active,
            sanitizeText(headline, 120),
            sanitizeText(bio, 2000),
            sanitizeText(organization, 120),
            sanitizeUrl(website),
            sanitizeUrl(linkedin),
            normalizeTopicsCsv(topics),
            activatedAt,
            now));
    persist();
    return profile;
  }

  private void persist() {
    try {
      persistence.saveUserProfiles(new HashMap<>(profiles));
    } catch (Exception e) {
      LOG.error("Failed to persist user profiles", e);
    }
  }

  private String normalize(String id) {
    return id == null ? null : id.trim().toLowerCase();
  }

  private String normalizeDiscordId(String id) {
    if (id == null) {
      return null;
    }
    String normalized = id.trim().toLowerCase();
    return normalized.isBlank() ? null : normalized;
  }

  private static String sanitizeText(String raw, int maxLength) {
    if (raw == null) {
      return null;
    }
    String normalized = raw.trim().replaceAll("\\s+", " ");
    if (normalized.isBlank()) {
      return null;
    }
    if (normalized.length() > maxLength) {
      normalized = normalized.substring(0, maxLength).trim();
    }
    return normalized.isBlank() ? null : normalized;
  }

  private static String sanitizeUrl(String raw) {
    String value = sanitizeText(raw, 500);
    if (value == null) {
      return null;
    }
    String lower = value.toLowerCase();
    if (!(lower.startsWith("https://") || lower.startsWith("http://"))) {
      return null;
    }
    return value;
  }

  private static String normalizeTopicsCsv(List<String> topics) {
    if (topics == null || topics.isEmpty()) {
      return null;
    }
    LinkedHashSet<String> normalized = new LinkedHashSet<>();
    for (String topic : topics) {
      String item = sanitizeText(topic, 40);
      if (item != null) {
        normalized.add(item);
      }
      if (normalized.size() >= 8) {
        break;
      }
    }
    if (normalized.isEmpty()) {
      return null;
    }
    return String.join(", ", new ArrayList<>(normalized));
  }
}
