package com.scanales.eventflow.private_;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanales.eventflow.model.UserProfile;
import com.scanales.eventflow.service.UserProfileService;
import com.scanales.eventflow.util.AdminUtils;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class GithubLinkService {
  private static final Logger LOG = Logger.getLogger(GithubLinkService.class);

  @Inject UserProfileService profiles;

  @Inject ObjectMapper objectMapper;

  @Inject Config config;

  @ConfigProperty(name = "app.public-url", defaultValue = "http://localhost:8080")
  String publicUrl;

  public Response start(SecurityIdentity identity, String redirect) {
    if (!githubConfigured()) {
      return Response.seeOther(URI.create("/private/profile?githubConfig=missing")).build();
    }

    String state = UUID.randomUUID().toString();
    String callback = canonicalCallback();
    String target = (redirect != null && !redirect.isBlank()) ? redirect : "/private/profile";

    String authorize =
        "https://github.com/login/oauth/authorize?client_id="
            + url(getGithubClientId())
            + "&redirect_uri="
            + url(callback)
            + "&scope="
            + url("read:user user:email")
            + "&state="
            + url(state);

    return Response.seeOther(URI.create(authorize))
        .cookie(new jakarta.ws.rs.core.NewCookie("gh_state", state, "/", null, null, 300, true, true))
        .cookie(
            new jakarta.ws.rs.core.NewCookie(
                "gh_redirect", target, "/", null, null, 300, false, false))
        .build();
  }

  public Response handleCallback(
      String code,
      String state,
      String error,
      Cookie stateCookie,
      Cookie redirectCookie,
      SecurityIdentity identity) {
    if (error != null) {
      LOG.warnf("GitHub OAuth returned error: %s", error);
      return redirectWithParams("/private/profile?githubError=denied");
    }
    if (code == null || code.isBlank()) {
      return redirectWithParams("/private/profile?githubError=missingCode");
    }
    if (stateCookie == null || state == null || !state.equals(stateCookie.getValue())) {
      return redirectWithParams("/private/profile?githubError=invalidState");
    }
    if (!githubConfigured()) {
      return redirectWithParams("/private/profile?githubConfig=missing");
    }

    try {
      HttpClient client = HttpClient.newHttpClient();
      HttpRequest tokenRequest =
          HttpRequest.newBuilder()
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
      HttpResponse<String> tokenResponse =
          client.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
      if (tokenResponse.statusCode() >= 400) {
        LOG.warnf("GitHub token exchange failed: %s", tokenResponse.body());
        return redirectWithParams("/private/profile?githubError=token");
      }
      JsonNode tokenJson = objectMapper.readTree(tokenResponse.body());
      String accessToken = tokenJson.path("access_token").asText();
      if (accessToken == null || accessToken.isBlank()) {
        LOG.warnf("GitHub token missing access_token field: %s", tokenResponse.body());
        return redirectWithParams("/private/profile?githubError=token");
      }
      HttpRequest meRequest =
          HttpRequest.newBuilder()
              .uri(URI.create("https://api.github.com/user"))
              .header("Accept", "application/json")
              .header("Authorization", "Bearer " + accessToken)
              .build();
      HttpResponse<String> meResponse =
          client.send(meRequest, HttpResponse.BodyHandlers.ofString());
      if (meResponse.statusCode() >= 400) {
        LOG.warnf("GitHub user fetch failed: %s", meResponse.body());
        return redirectWithParams("/private/profile?githubError=user");
      }
      JsonNode userJson = objectMapper.readTree(meResponse.body());
      String login = userJson.path("login").asText();
      if (login == null || login.isBlank()) {
        return redirectWithParams("/private/profile?githubError=user");
      }
      String htmlUrl = userJson.path("html_url").asText();
      String avatarUrl = userJson.path("avatar_url").asText();
      String ghId = userJson.path("id").asText();

      String userId = currentUserId(identity);
      String name =
          AdminUtils.getClaim(identity, "name") != null
              ? AdminUtils.getClaim(identity, "name")
              : identity.getPrincipal().getName();
      String email = AdminUtils.getClaim(identity, "email");

      profiles.linkGithub(
          userId,
          name,
          email,
          new UserProfile.GithubAccount(login, htmlUrl, avatarUrl, ghId, Instant.now()));

      String target = redirectCookie != null ? redirectCookie.getValue() : "/private/profile";
      String sep = target.contains("?") ? "&" : "?";
      return Response.seeOther(URI.create(target + sep + "githubLinked=1"))
          .cookie(new jakarta.ws.rs.core.NewCookie("gh_state", "", "/", null, null, 0, true, true))
          .cookie(
              new jakarta.ws.rs.core.NewCookie("gh_redirect", "", "/", null, null, 0, false, false))
          .build();
    } catch (Exception e) {
      LOG.error("GitHub OAuth callback failed", e);
      return redirectWithParams("/private/profile?githubError=unexpected");
    }
  }

  private Response redirectWithParams(String target) {
    return Response.seeOther(URI.create(target))
        .cookie(new jakarta.ws.rs.core.NewCookie("gh_state", "", "/", null, null, 0, true, true))
        .cookie(
            new jakarta.ws.rs.core.NewCookie("gh_redirect", "", "/", null, null, 0, false, false))
        .build();
  }

  public boolean githubConfigured() {
    return !getGithubClientId().isBlank() && !getGithubClientSecret().isBlank();
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

  private String canonicalCallback() {
    String normalized = publicUrl;
    if (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized + "/auth/post-login";
  }

  private String currentUserId(SecurityIdentity identity) {
    String email = AdminUtils.getClaim(identity, "email");
    if (email != null && !email.isBlank()) {
      return email.toLowerCase(Locale.ROOT);
    }
    String sub = AdminUtils.getClaim(identity, "sub");
    return sub != null ? sub : identity.getPrincipal().getName();
  }
}
