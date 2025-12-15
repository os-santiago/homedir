package com.scanales.eventflow.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.scanales.eventflow.model.CommunityMember;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Map;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import io.quarkus.scheduler.Scheduled;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CommunitySyncService {

  private static final Logger LOG = Logger.getLogger(CommunitySyncService.class);

  @Inject
  @ConfigProperty(name = "community.github.repo-owner", defaultValue = "os-santiago")
  String repoOwner;

  @Inject
  @ConfigProperty(name = "community.github.repo-name", defaultValue = "os-santiago.github.io")
  String repoName;

  @Inject
  @ConfigProperty(name = "community.github.members-path", defaultValue = "community/members.yaml")
  String membersPath;

  private String githubToken;

  @Inject
  @ConfigProperty(name = "community.github.default-branch", defaultValue = "main")
  String defaultBranch;

  @Inject
  @ConfigProperty(name = "community.sync.max-members", defaultValue = "500")
  int maxMembers;

  @Inject
  @ConfigProperty(name = "community.sync.max-prs", defaultValue = "100")
  int maxPrs;

  @Inject
  Config config;

  @Inject
  SystemErrorService systemErrorService;

  private final HttpClient client = HttpClient.newBuilder().build();
  private final ObjectMapper jsonMapper = new ObjectMapper();
  private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

  // Cache
  private final AtomicReference<List<CommunityMember>> cachedMembers = new AtomicReference<>(Collections.emptyList());
  private final AtomicReference<List<Object>> cachedPrs = new AtomicReference<>(Collections.emptyList()); // Storing raw
                                                                                                          // PR data or
                                                                                                          // simplified
                                                                                                          // objects
  private final AtomicReference<Instant> lastSync = new AtomicReference<>();
  private final AtomicReference<String> syncStatus = new AtomicReference<>("IDLE");
  private final AtomicBoolean paused = new AtomicBoolean(false);
  private final AtomicReference<MembersPayload> cache = new AtomicReference<>(); // Keeping legacy cache just in case
                                                                                 // for internal loadMembers logic
                                                                                 // sharing

  @PostConstruct
  void init() {
    com.fasterxml.jackson.datatype.jsr310.JavaTimeModule module = new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule();
    module.addSerializer(java.time.Instant.class, com.fasterxml.jackson.databind.ser.std.ToStringSerializer.instance);
    yamlMapper.registerModule(module);
    yamlMapper.findAndRegisterModules();
    yamlMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    githubToken = config.getOptionalValue("GH_TOKEN", String.class).orElse("");
    // Trigger initial sync async
    new Thread(this::sync).start();
  }

  @Scheduled(every = "5m")
  public void sync() {
    if (paused.get()) {
      LOG.warn("Sync PAUSED due to limits reached.");
      syncStatus.set("PAUSED");
      return;
    }
    syncStatus.set("SYNCING");
    try {
      LOG.info("Starting scheduled community sync...");
      // 1. Fetch Members
      MembersPayload payload = loadMembers();
      if (payload.members != null) {
        if (payload.members.size() > maxMembers) {
          LOG.errorf("Max members limit reached (%d > %d). Pausing sync.", payload.members.size(), maxMembers);
          paused.set(true);
          syncStatus.set("LIMIT_REACHED");
          return;
        }
        payload.members.forEach(CommunityMember::calculateGamification);
        cachedMembers.set(payload.members);
        // Update legacy cache for PR logic
        cache.set(payload);
      }

      // 2. Fetch PRs
      List<Object> prs = fetchOpenPullRequests();
      if (prs.size() > maxPrs) {
        LOG.errorf("Max PRs limit reached (%d > %d). Pausing sync.", prs.size(), maxPrs);
        paused.set(true);
        syncStatus.set("LIMIT_REACHED");
        return;
      }
      cachedPrs.set(prs);

      lastSync.set(Instant.now());
      syncStatus.set("OK");
      LOG.info("Community sync complete. Members: " + cachedMembers.get().size() + ", PRs: " + cachedPrs.get().size());

    } catch (Exception e) {
      LOG.error("Community sync failed", e);
      syncStatus.set("ERROR: " + e.getMessage());
      systemErrorService.logError("ERROR", "CommunitySync", "Failed to sync: " + e.getMessage(), null, "SYSTEM");
    }
  }

  public List<CommunityMember> fetchMembers() {
    // Return cached immediately
    return cachedMembers.get();
  }

  public boolean hasPendingJoinRequest(String githubLogin) {
    if (githubLogin == null)
      return false;
    // Basic check in cached PRs titles/bodies
    String q = githubLogin.toLowerCase();
    return cachedPrs.get().stream().anyMatch(pr -> {
      // Assuming PR object is a Map or JsonNode. Let's make fetchOpenPullRequests
      // return List<Map>
      if (pr instanceof Map<?, ?> m) {
        String title = (String) m.get("title");
        String body = (String) m.get("body");
        return (title != null && title.toLowerCase().contains(q)) || (body != null && body.toLowerCase().contains(q));
      }
      return false;
    });
  }

  public Map<String, Object> getSyncStatus() {
    Map<String, Object> status = new java.util.HashMap<>();
    status.put("status", syncStatus.get());
    status.put("members", cachedMembers.get().size());
    status.put("prs", cachedPrs.get().size());
    status.put("lastSync", lastSync.get());
    status.put("paused", paused.get());
    return status;
  }

  public Optional<String> createMemberPullRequest(CommunityMember member) {
    if (githubToken == null || githubToken.isBlank()) {
      LOG.error("Github token for community sync is missing or empty");
      return Optional.empty();
    }
    LOG.info("Using GitHub Token: "
        + (githubToken.length() > 4 ? "..." + githubToken.substring(githubToken.length() - 4) : "INVALID"));
    try {
      MembersPayload payload = loadMembers();
      // ...
      List<CommunityMember> members = payload.members != null ? new ArrayList<>(payload.members) : new ArrayList<>();
      if (members.stream().anyMatch(m -> memberEqual(m, member))) {
        LOG.infov("Member already registered: {0}", member.getGithub());
        return Optional.empty();
      }
      members.add(memberWithJoinedAt(member));
      String branch = "community/add-" + UUID.randomUUID();
      String baseSha = branchSha(defaultBranch);
      createBranch(branch, baseSha);
      String yaml = serializeMembers(new CommunityData(members));
      LOG.infov("Serialized YAML (len={0}): {1}", yaml.length(), yaml);
      String content = Base64.getEncoder().encodeToString(yaml.getBytes(StandardCharsets.UTF_8));
      String message = "Add " + member.getDisplayName() + " to Comunidad";
      updateFile(branch, message, payload.sha, content);
      Optional<String> prUrl = createPullRequest(branch, message, defaultBranch);
      cache.set(new MembersPayload(members, payload.sha));
      return prUrl;
    } catch (Exception e) {
      LOG.error("Failed to submit community member PR", e);
      return Optional.empty();
    }
  }

  private boolean memberEqual(CommunityMember existing, CommunityMember candidate) {
    if (existing == null || candidate == null) {
      return false;
    }
    if (existing.getGithub() != null && candidate.getGithub() != null) {
      return existing.getGithub().equalsIgnoreCase(candidate.getGithub());
    }
    return existing.getUserId() != null
        && candidate.getUserId() != null
        && existing.getUserId().equalsIgnoreCase(candidate.getUserId());
  }

  private CommunityMember memberWithJoinedAt(CommunityMember member) {
    CommunityMember clone = new CommunityMember();
    clone.setUserId(member.getUserId());
    clone.setDisplayName(member.getDisplayName());
    clone.setGithub(member.getGithub());
    clone.setRole(member.getRole());
    clone.setProfileUrl(member.getProfileUrl());
    clone.setAvatarUrl(member.getAvatarUrl());
    clone.setJoinedAt(Instant.now());
    return clone;
  }

  private MembersPayload loadMembers() throws Exception {
    HttpRequest request = contentRequest(defaultBranch);
    HttpResponse<String> response = send(request);
    if (response.statusCode() == 404) {
      return new MembersPayload(new ArrayList<>(), null);
    }
    if (response.statusCode() >= 400) {
      throw new IllegalStateException("GitHub responded with " + response.statusCode());
    }
    JsonFileResponse json = jsonMapper.readValue(response.body(), JsonFileResponse.class);
    byte[] decoded = Base64.getDecoder().decode(json.content.replaceAll("\n", ""));
    CommunityData data = yamlMapper.readValue(decoded, CommunityData.class);
    List<CommunityMember> list = data.members != null ? data.members : new ArrayList<>();
    return new MembersPayload(list, json.sha);
  }

  private String serializeMembers(CommunityData data) throws JsonProcessingException {
    // Manual Map construction to avoid Native Reflection issues with POJOs
    Map<String, Object> root = new java.util.HashMap<>();
    List<Map<String, Object>> membersList = new ArrayList<>();

    if (data.members != null) {
      for (CommunityMember m : data.members) {
        Map<String, Object> map = new java.util.LinkedHashMap<>(); // Preserve order
        map.put("userId", m.getUserId());
        map.put("displayName", m.getDisplayName());
        map.put("github", m.getGithub());
        map.put("role", m.getRole());
        map.put("profileUrl", m.getProfileUrl());
        map.put("avatarUrl", m.getAvatarUrl());
        // Explicit String conversion for Instant
        map.put("joinedAt", m.getJoinedAt() != null ? m.getJoinedAt().toString() : null);

        // Add gamification if present/needed, or skip if transient
        // map.put("level", m.getLevel()); // etc

        membersList.add(map);
      }
    }
    root.put("members", membersList);

    LOG.info("Serializing Map structure...");
    return yamlMapper.writeValueAsString(root);
  }

  private HttpRequest contentRequest(String ref) {
    return requestBuilder()
        .uri(
            URI.create(
                String.format(
                    "https://api.github.com/repos/%s/%s/contents/%s?ref=%s",
                    repoOwner, repoName, encode(membersPath), ref)))
        .GET()
        .build();
  }

  private void updateFile(String branch, String message, String sha, String content)
      throws Exception {
    HttpRequest.Builder builder = requestBuilder();
    builder.uri(
        URI.create(
            String.format(
                "https://api.github.com/repos/%s/%s/contents/%s",
                repoOwner, repoName, encode(membersPath))));
    var payload = new java.util.HashMap<String, Object>();
    payload.put("message", message);
    payload.put("content", content);
    payload.put("branch", branch);
    if (sha != null) {
      payload.put("sha", sha);
    }
    String body = jsonMapper.writeValueAsString(payload);
    HttpRequest request = builder.PUT(HttpRequest.BodyPublishers.ofString(body)).build();
    HttpResponse<String> response = send(request);
    if (response.statusCode() >= 400) {
      LOG.errorf("File update failed. Status: %d, Body: %s", response.statusCode(), response.body());
      throw new IllegalStateException("File update failed: " + response.body());
    }
  }

  private Optional<String> createPullRequest(String head, String title, String base)
      throws Exception {
    HttpRequest.Builder builder = requestBuilder();
    builder.uri(
        URI.create(String.format("https://api.github.com/repos/%s/%s/pulls", repoOwner, repoName)));
    var payload = new java.util.HashMap<String, String>();
    payload.put("title", title);
    payload.put("head", head);
    payload.put("base", base);
    payload.put("body", "Añadir miembro vía Homedir");
    String body = jsonMapper.writeValueAsString(payload);
    HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(body)).build();
    HttpResponse<String> response = send(request);
    if (response.statusCode() >= 400) {
      throw new IllegalStateException("Create PR failed: " + response.body());
    }
    JsonNode node = jsonMapper.readTree(response.body());
    String url = node.path("html_url").asText(null);
    return Optional.ofNullable(url);
  }

  private void createBranch(String branch, String sha) throws Exception {
    HttpRequest.Builder builder = requestBuilder();
    builder.uri(
        URI.create(
            String.format("https://api.github.com/repos/%s/%s/git/refs", repoOwner, repoName)));
    var payload = new java.util.HashMap<String, String>();
    payload.put("ref", "refs/heads/" + branch);
    payload.put("sha", sha);
    String body = jsonMapper.writeValueAsString(payload);
    HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(body)).build();
    HttpResponse<String> response = send(request);
    if (response.statusCode() >= 400) {
      throw new IllegalStateException("Create branch failed: " + response.body());
    }
  }

  private String branchSha(String branch) throws Exception {
    HttpRequest request = requestBuilder()
        .uri(
            URI.create(
                String.format(
                    "https://api.github.com/repos/%s/%s/git/ref/heads/%s",
                    repoOwner, repoName, branch)))
        .GET()
        .build();
    HttpResponse<String> response = send(request);
    if (response.statusCode() >= 400) {
      throw new IllegalStateException("Unable to fetch branch sha: " + response.body());
    }
    JsonNode node = jsonMapper.readTree(response.body());
    return node.path("object").path("sha").asText();
  }

  private HttpResponse<String> send(HttpRequest request) throws Exception {
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private HttpRequest.Builder requestBuilder() {
    HttpRequest.Builder builder = HttpRequest.newBuilder();
    builder.header("Accept", "application/vnd.github+json");
    if (githubToken != null && !githubToken.isBlank()) {
      builder.header("Authorization", "Bearer " + githubToken);
    }
    return builder;
  }

  private List<Object> fetchOpenPullRequests() {
    try {
      HttpRequest request = requestBuilder()
          .uri(URI.create(
              String.format("https://api.github.com/repos/%s/%s/pulls?state=open&per_page=100", repoOwner, repoName)))
          .GET()
          .build();
      HttpResponse<String> response = send(request);
      if (response.statusCode() == 200) {
        return jsonMapper.readValue(response.body(), new com.fasterxml.jackson.core.type.TypeReference<List<Object>>() {
        });
      }
      LOG.warn("Failed to fetch PRs: " + response.statusCode());
      return Collections.emptyList();
    } catch (Exception e) {
      LOG.warn("Error fetching PRs", e);
      return Collections.emptyList();
    }
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  @io.quarkus.runtime.annotations.RegisterForReflection
  public record MembersPayload(List<CommunityMember> members, String sha) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  @io.quarkus.runtime.annotations.RegisterForReflection
  public static class JsonFileResponse {
    public String content;
    public String sha;
  }

  @io.quarkus.runtime.annotations.RegisterForReflection
  public static class CommunityData {
    public List<CommunityMember> members;

    public CommunityData() {
    }

    public CommunityData(List<CommunityMember> members) {
      this.members = members;
    }
  }
}
