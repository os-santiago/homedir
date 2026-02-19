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
  private String preferredLocale;
  private GithubAccount github;
  private DiscordAccount discord;

  private QuestClass questClass;
  private int currentXp;
  private java.util.List<String> activeQuests = new java.util.ArrayList<>();
  private java.util.List<QuestHistoryItem> history = new java.util.ArrayList<>();

  public UserProfile() {
  }

  @JsonCreator
  public UserProfile(
      @JsonProperty("userId") String userId,
      @JsonProperty("name") String name,
      @JsonProperty("email") String email,
      @JsonProperty("github") GithubAccount github,
      @JsonProperty("discord") DiscordAccount discord,
      @JsonProperty("questClass") QuestClass questClass,
      @JsonProperty("currentXp") int currentXp,
      @JsonProperty("activeQuests") java.util.List<String> activeQuests,
      @JsonProperty("history") java.util.List<QuestHistoryItem> history) {
    this.userId = userId;
    this.name = name;
    this.email = email;
    this.preferredLocale = null;
    this.github = github;
    this.discord = discord;
    this.questClass = questClass;
    this.currentXp = currentXp;
    this.activeQuests = activeQuests != null ? activeQuests : new java.util.ArrayList<>();
    this.history = history != null ? history : new java.util.ArrayList<>();
  }

  public UserProfile(String userId, String name, String email, GithubAccount github) {
    this(userId, name, email, github, null, null, 0, null, null);
  }

  public String getPreferredLocale() {
    return preferredLocale;
  }

  public void setPreferredLocale(String preferredLocale) {
    this.preferredLocale = preferredLocale;
  }

  public QuestClass getQuestClass() {
    return questClass;
  }

  public void setQuestClass(QuestClass questClass) {
    this.questClass = questClass;
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

  public DiscordAccount getDiscord() {
    return discord;
  }

  public void setDiscord(DiscordAccount discord) {
    this.discord = discord;
  }

  public int getCurrentXp() {
    return currentXp;
  }

  public void setCurrentXp(int currentXp) {
    this.currentXp = currentXp;
  }

  public java.util.List<String> getActiveQuests() {
    return activeQuests;
  }

  public void setActiveQuests(java.util.List<String> activeQuests) {
    this.activeQuests = activeQuests;
  }

  public java.util.List<QuestHistoryItem> getHistory() {
    return history;
  }

  public void setHistory(java.util.List<QuestHistoryItem> history) {
    this.history = history;
  }

  public void addHistoryItem(QuestHistoryItem item) {
    if (this.history == null) {
      this.history = new java.util.ArrayList<>();
    }
    this.history.add(item);
  }

  public boolean hasGithub() {
    return github != null && github.login != null && !github.login.isBlank();
  }

  public boolean hasDiscord() {
    return discord != null && discord.id != null && !discord.id.isBlank();
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

  public record DiscordAccount(
      String id, String handle, String profileUrl, String avatarUrl, Instant linkedAt) {
    @JsonCreator
    public DiscordAccount(
        @JsonProperty("id") String id,
        @JsonProperty("handle") String handle,
        @JsonProperty("profileUrl") String profileUrl,
        @JsonProperty("avatarUrl") String avatarUrl,
        @JsonProperty("linkedAt") Instant linkedAt) {
      this.id = id;
      this.handle = handle;
      this.profileUrl = profileUrl;
      this.avatarUrl = avatarUrl;
      this.linkedAt = linkedAt;
    }
  }

  public record QuestHistoryItem(String title, int xp, String date) {
    @JsonCreator
    public QuestHistoryItem(
        @JsonProperty("title") String title,
        @JsonProperty("xp") int xp,
        @JsonProperty("date") String date) {
      this.title = title;
      this.xp = xp;
      this.date = date;
    }
  }
}
