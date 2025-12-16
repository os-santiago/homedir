package com.scanales.eventflow.service;

import com.scanales.eventflow.model.UserProfile;
import com.scanales.eventflow.model.QuestClass;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
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

  public Optional<UserProfile> findByGithubLogin(String githubLogin) {
    if (githubLogin == null || githubLogin.isBlank()) {
      return Optional.empty();
    }
    return profiles.values().stream()
        .filter(p -> p.getGithub() != null && githubLogin.equalsIgnoreCase(p.getGithub().login()))
        .findFirst();
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
}
