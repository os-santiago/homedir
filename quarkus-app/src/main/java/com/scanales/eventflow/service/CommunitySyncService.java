package com.scanales.eventflow.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
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
  Config config;

  private final HttpClient client = HttpClient.newBuilder().build();
  private final ObjectMapper jsonMapper = new ObjectMapper();
  private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
  private final AtomicReference<MembersPayload> cache = new AtomicReference<>();

  @PostConstruct
  void init() {
    yamlMapper.findAndRegisterModules();
    yamlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    githubToken = config.getOptionalValue("GH_TOKEN", String.class).orElse("");
  }

  public List<CommunityMember> fetchMembers() {
    try {
      MembersPayload payload = loadMembers();
      cache.set(payload);
      if (payload.members != null) {
        payload.members.forEach(CommunityMember::calculateGamification);
        return payload.members;
      }
      return Collections.emptyList();
    } catch (Exception e) {
      LOG.warn("Unable to load community members", e);
      MembersPayload cached = cache.get();
      return cached != null && cached.members != null ? cached.members : Collections.emptyList();
    }
  }

  public Optional<String> createMemberPullRequest(CommunityMember member) {
    if (githubToken == null || githubToken.isBlank()) {
      LOG.warn("Github token for community sync is missing");
      return Optional.empty();
    }
    try {
      MembersPayload payload = loadMembers();
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
    return yamlMapper.writeValueAsString(data);
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

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private record MembersPayload(List<CommunityMember> members, String sha) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class JsonFileResponse {
    public String content;
    public String sha;
  }

  private static class CommunityData {
    public List<CommunityMember> members;

    CommunityData(List<CommunityMember> members) {
      this.members = members;
    }
  }
}
