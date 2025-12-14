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
import org.eclipse.microprofile.config.Config;
import org.jboss.logging.Logger;

@ApplicationScoped
public class GithubService {

    private static final Logger LOG = Logger.getLogger(GithubService.class);

    @Inject
    ObjectMapper objectMapper;

    @Inject
    Config config;

    public String exchangeCode(String code) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest tokenRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://github.com/login/oauth/access_token"))
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
        HttpResponse<String> tokenResponse = client.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
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
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest meRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/user"))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .build();
        HttpResponse<String> meResponse = client.send(meRequest, HttpResponse.BodyHandlers.ofString());
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
}
