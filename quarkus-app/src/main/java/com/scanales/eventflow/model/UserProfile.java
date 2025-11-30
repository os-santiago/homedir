package com.scanales.eventflow.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserProfile {

  private String userId;
  private String name;
  private String email;
  private GithubAccount github;

  public UserProfile() {}

  @JsonCreator
  public UserProfile(
      @JsonProperty("userId") String userId,
      @JsonProperty("name") String name,
      @JsonProperty("email") String email,
      @JsonProperty("github") GithubAccount github) {
    this.userId = userId;
    this.name = name;
    this.email = email;
    this.github = github;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public GithubAccount getGithub() {
    return github;
  }

  public void setGithub(GithubAccount github) {
    this.github = github;
  }

  public boolean hasGithub() {
    return github != null && github.login != null && !github.login.isBlank();
  }

  public record GithubAccount(
      String login, String profileUrl, String avatarUrl, String id, Instant linkedAt) {
    @JsonCreator
    public GithubAccount(
        @JsonProperty("login") String login,
        @JsonProperty("profileUrl") String profileUrl,
        @JsonProperty("avatarUrl") String avatarUrl,
        @JsonProperty("id") String id,
        @JsonProperty("linkedAt") Instant linkedAt) {
      this.login = login;
      this.profileUrl = profileUrl;
      this.avatarUrl = avatarUrl;
      this.id = id;
      this.linkedAt = linkedAt;
    }
  }
}
