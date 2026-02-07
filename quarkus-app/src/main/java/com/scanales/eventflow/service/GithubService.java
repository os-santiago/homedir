package com.scanales.eventflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.microprofile.config.Config;
import org.jboss.logging.Logger;

@ApplicationScoped
public class GithubService {

  private static final Logger LOG = Logger.getLogger(GithubService.class);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
  private static final int MAX_CONTRIBUTORS = 10;

  @Inject
  ObjectMapper objectMapper;

  @Inject
  Config config;

  private final HttpClient httpClient = HttpClient.newBuilder()
      .connectTimeout(REQUEST_TIMEOUT)
      .build();

  public String exchangeCode(String code) throws IOException, InterruptedException {
    HttpRequest tokenRequest = HttpRequest.newBuilder()
        .uri(URI.create("https://github.com/login/oauth/access_token"))
        .timeout(REQUEST_TIMEOUT)
        .header("Accept", "application/json")
        .POST(
            HttpRequest.BodyPublishers.ofString(
                "client_id="
                    + url(getGithubClientId())
                    + "&client_secret="
                    + url(getGithubClientSecret())
                    + "&code="
                    + url(code)))
        .build();
    HttpResponse<String> tokenResponse = httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
    if (tokenResponse.statusCode() >= 400) {
      LOG.warnf("GitHub token exchange failed: %s", tokenResponse.body());
      throw new IOException("GitHub token exchange failed");
    }
    JsonNode tokenJson = objectMapper.readTree(tokenResponse.body());
    String accessToken = tokenJson.path("access_token").asText();
    if (accessToken == null || accessToken.isBlank()) {
      LOG.warnf("GitHub token missing access_token field: %s", tokenResponse.body());
      throw new IOException("GitHub token missing access_token");
    }
    return accessToken;
  }

  public GithubProfile fetchUser(String accessToken) throws IOException, InterruptedException {
    HttpRequest meRequest = HttpRequest.newBuilder()
        .uri(URI.create("https://api.github.com/user"))
        .timeout(REQUEST_TIMEOUT)
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + accessToken)
        .build();
    HttpResponse<String> meResponse = httpClient.send(meRequest, HttpResponse.BodyHandlers.ofString());
    if (meResponse.statusCode() >= 400) {
      LOG.warnf("GitHub user fetch failed: %s", meResponse.body());
      throw new IOException("GitHub user fetch failed");
    }
    JsonNode userJson = objectMapper.readTree(meResponse.body());
    String login = userJson.path("login").asText(null);
    if (login == null || login.isBlank()) {
      throw new IOException("GitHub user missing login");
    }
    return new GithubProfile(
        login,
        userJson.path("html_url").asText(),
        userJson.path("avatar_url").asText(),
        userJson.path("id").asText(),
        userJson.path("email").asText(null));
  }

  public List<GithubContributor> fetchContributors(String owner, String repo) {
    try {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(
              URI.create(
                  "https://api.github.com/repos/"
                      + owner
                      + "/"
                      + repo
                      + "/contributors?per_page="
                      + MAX_CONTRIBUTORS))
          .timeout(REQUEST_TIMEOUT)
          .header("Accept", "application/json")
          .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        LOG.warnf("GitHub contributors fetch failed: %s", response.body());
        return List.of();
      }

      JsonNode json = objectMapper.readTree(response.body());
      if (!json.isArray()) {
        return List.of();
      }
      List<GithubContributor> contributors = new ArrayList<>();
      for (JsonNode node : json) {
        String login = node.path("login").asText("");
        if (login.isBlank()) {
          continue;
        }
        contributors.add(new GithubContributor(
            login,
            node.path("avatar_url").asText(),
            node.path("html_url").asText(),
            node.path("contributions").asInt()));
      }
      return contributors;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warn("GitHub contributors fetch interrupted", e);
      return List.of();
    } catch (Exception e) {
      LOG.warn("GitHub contributors fetch failed", e);
      return List.of();
    }
  }

  private String getGithubClientId() {
    return config.getOptionalValue("GH_CLIENT_ID", String.class).orElse("");
  }

  private String getGithubClientSecret() {
    return config.getOptionalValue("GH_CLIENT_SECRET", String.class).orElse("");
  }

  private String url(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  public record GithubProfile(String login, String htmlUrl, String avatarUrl, String id, String email) {
  }

  public record GithubContributor(String login, String avatarUrl, String htmlUrl, int contributions) {
  }
}
