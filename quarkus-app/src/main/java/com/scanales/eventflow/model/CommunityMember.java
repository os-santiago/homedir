package com.scanales.eventflow.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommunityMember {

  private String userId;
  private String displayName;
  private String github;
  private String role;
  private String profileUrl;
  private String avatarUrl;
  private Instant joinedAt;

  public CommunityMember() {}

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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CommunityMember that)) return false;
    return Objects.equals(github, that.github) || Objects.equals(userId, that.userId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(github, userId);
  }
}
