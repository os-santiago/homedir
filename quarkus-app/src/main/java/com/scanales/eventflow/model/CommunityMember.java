package com.scanales.eventflow.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
@io.quarkus.runtime.annotations.RegisterForReflection
public class CommunityMember {

  private String userId;
  private String displayName;
  private String github;
  private String role;
  private String profileUrl;
  private String avatarUrl;
  @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
  private Instant joinedAt;

  public CommunityMember() {
  }

  @JsonCreator
  public CommunityMember(
      @JsonProperty("userId") String userId,
      @JsonProperty("displayName") String displayName,
      @JsonProperty("github") String github,
      @JsonProperty("role") String role,
      @JsonProperty("profileUrl") String profileUrl,
      @JsonProperty("avatarUrl") String avatarUrl,
      @JsonProperty("joinedAt") Instant joinedAt) {
    this.userId = userId;
    this.displayName = displayName;
    this.github = github;
    this.role = role;
    this.profileUrl = profileUrl;
    this.avatarUrl = avatarUrl;
    this.joinedAt = joinedAt;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getGithub() {
    return github;
  }

  public void setGithub(String github) {
    this.github = github;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public String getProfileUrl() {
    return profileUrl;
  }

  public void setProfileUrl(String profileUrl) {
    this.profileUrl = profileUrl;
  }

  public String getAvatarUrl() {
    return avatarUrl;
  }

  public void setAvatarUrl(String avatarUrl) {
    this.avatarUrl = avatarUrl;
  }

  public Instant getJoinedAt() {
    return joinedAt;
  }

  public void setJoinedAt(Instant joinedAt) {
    this.joinedAt = joinedAt;
  }

  public String roleLabel() {
    if (role == null) {
      return "Colaborador";
    }
    return "administrador".equalsIgnoreCase(role) ? "Administrador" : "Colaborador";
  }

  // Gamification Fields
  private int level;
  private int xp;
  private int contributions;
  private List<String> badges;
  private List<String> skills = new ArrayList<>();
  private QuestClass questClass;

  public QuestClass getQuestClass() {
    return questClass;
  }

  public void setQuestClass(QuestClass questClass) {
    this.questClass = questClass;
  }

  public List<String> getSkills() {
    return skills;
  }

  public void setSkills(List<String> skills) {
    this.skills = skills;
  }

  public int getLevel() {
    return level;
  }

  public void setLevel(int level) {
    this.level = level;
  }

  public int getXp() {
    return xp;
  }

  public void setXp(int xp) {
    this.xp = xp;
  }

  public int getContributions() {
    return contributions;
  }

  public void setContributions(int contributions) {
    this.contributions = contributions;
  }

  public List<String> getBadges() {
    return badges;
  }

  public void setBadges(List<String> badges) {
    this.badges = badges;
  }

  public void calculateGamification() {
    if (this.github == null) {
      this.level = 1;
      this.xp = 0;
      this.contributions = 0;
      this.badges = List.of();
      return;
    }

    // Deterministic simulation based on username hash
    int hash = Math.abs(this.github.toLowerCase(Locale.ROOT).hashCode());
    this.contributions = (hash % 500) + 12; // Min 12 contribs

    // Level calc: simplified
    this.level = 1 + (this.contributions / 50);
    this.xp = (this.contributions % 50) * 2; // 0-100 scale approx

    this.badges = new ArrayList<>();
    if (this.joinedAt != null && this.joinedAt.isBefore(Instant.parse("2024-01-01T00:00:00Z"))) {
      this.badges.add("Pionero");
    }
    if (this.contributions > 300) {
      this.badges.add("Top Contributor");
    }
    if (hash % 10 < 2) { // 20% chance
      this.badges.add("Bug Hunter");
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (!(o instanceof CommunityMember that))
      return false;
    return Objects.equals(github, that.github) || Objects.equals(userId, that.userId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(github, userId);
  }
}
