package com.scanales.eventflow.private_;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanales.eventflow.model.UserProfile;
import com.scanales.eventflow.security.RedirectSanitizer;
import com.scanales.eventflow.service.UserProfileService;
import com.scanales.eventflow.util.AdminUtils;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class DiscordLinkService {
  private static final Logger LOG = Logger.getLogger(DiscordLinkService.class);
  private static final String DISCORD_AUTHORIZE_URL = "https://discord.com/api/oauth2/authorize";
  private static final String DISCORD_TOKEN_URL = "https://discord.com/api/oauth2/token";
  private static final String DISCORD_USER_URL = "https://discord.com/api/users/@me";
  private static final String DISCORD_USER_GUILDS_URL = "https://discord.com/api/users/@me/guilds";

  @Inject UserProfileService profiles;
  @Inject com.scanales.eventflow.community.CommunityBoardService boardService;
  @Inject ObjectMapper objectMapper;

  @ConfigProperty(name = "app.public-url", defaultValue = "http://localhost:8080")
  String publicUrl;

  @ConfigProperty(name = "discord.oauth.client-id", defaultValue = "")
  Optional<String> clientId;

  @ConfigProperty(name = "discord.oauth.client-secret", defaultValue = "")
  Optional<String> clientSecret;

  @ConfigProperty(name = "discord.oauth.callback-path", defaultValue = "/auth/discord/callback")
  String callbackPath;

  @ConfigProperty(name = "discord.oauth.timeout", defaultValue = "PT8S")
  Duration timeout;

  @ConfigProperty(name = "community.board.discord.guild-id", defaultValue = "")
  Optional<String> guildId;

  public Response start(SecurityIdentity identity, String redirect) {
    String target = RedirectSanitizer.sanitizeInternalRedirect(redirect, "/private/profile");
    if (identity == null || identity.isAnonymous()) {
      return redirectWithParams(target, "discordError", "auth_required");
    }
    if (!discordConfigured()) {
      return redirectWithParams(target, "discordError", "config_missing");
    }

    String state = UUID.randomUUID().toString();
    String callback = canonicalCallback();

    String authorize = DISCORD_AUTHORIZE_URL
        + "?client_id=" + url(clientId())
        + "&response_type=code"
        + "&redirect_uri=" + url(callback)
        + "&scope=" + url("identify guilds")
        + "&state=" + url(state);

    boolean secure = isHttpsPublicUrl();
    return Response.seeOther(URI.create(authorize))
        .cookie(
            new jakarta.ws.rs.core.NewCookie.Builder("discord_state")
                .value(state)
                .path("/")
                .maxAge(300)
                .secure(secure)
                .httpOnly(true)
                .build())
        .cookie(
            new jakarta.ws.rs.core.NewCookie.Builder("discord_redirect")
                .value(target)
                .path("/")
                .maxAge(300)
                .secure(secure)
                .httpOnly(false)
                .build())
        .build();
  }

  public Response handleCallback(
      String code,
      String state,
      String error,
      Cookie stateCookie,
      Cookie redirectCookie,
      SecurityIdentity identity) {
    String target = redirectCookie != null
        ? RedirectSanitizer.sanitizeInternalRedirect(redirectCookie.getValue(), "/private/profile")
        : "/private/profile";

    if (error != null) {
      LOG.warnf("Discord OAuth returned error: %s", error);
      return redirectWithParams(target, "discordError", "denied");
    }
    if (identity == null || identity.isAnonymous()) {
      return redirectWithParams(target, "discordError", "auth_required");
    }
    if (code == null || code.isBlank()) {
      return redirectWithParams(target, "discordError", "missing_code");
    }
    if (stateCookie == null || state == null || !state.equals(stateCookie.getValue())) {
      return redirectWithParams(target, "discordError", "invalid_state");
    }
    if (!discordConfigured()) {
      return redirectWithParams(target, "discordError", "config_missing");
    }

    try {
      String accessToken = exchangeCode(code);
      DiscordUser user = fetchUser(accessToken);
      if (user.id() == null || user.id().isBlank()) {
        return redirectWithParams(target, "discordError", "invalid_member");
      }
      if (!isMemberOfGuild(accessToken, guildId())) {
        return redirectWithParams(target, "discordError", "not_in_guild");
      }

      String userId = currentUserId(identity);
      var existingClaim = profiles.findByDiscordId(user.id());
      if (existingClaim.isPresent()
          && existingClaim.get().getUserId() != null
          && !existingClaim.get().getUserId().equalsIgnoreCase(userId)) {
        return redirectWithParams(target, "discordError", "already_claimed");
      }

      String name = AdminUtils.getClaim(identity, "name");
      if (name == null || name.isBlank()) {
        name = userId;
      }
      String email = AdminUtils.getClaim(identity, "email");
      if (email == null || email.isBlank()) {
        email = userId;
      }

      profiles.linkDiscord(
          userId,
          name,
          email,
          new UserProfile.DiscordAccount(
              user.id(), user.handle(), "https://discord.com/users/" + url(user.id()), user.avatarUrl(), Instant.now()));
      boardService.requestRefresh("discord-oauth-claim");
      return redirectWithParams(target, "discordLinked", "1");
    } catch (Exception e) {
      LOG.warnf("Discord OAuth callback failed: %s", e.getMessage());
      return redirectWithParams(target, "discordError", "unexpected");
    }
  }

  private String exchangeCode(String code) throws Exception {
    java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
        .connectTimeout(timeout)
        .build();
    String payload = "client_id=" + url(clientId())
        + "&client_secret=" + url(clientSecret())
        + "&grant_type=authorization_code"
        + "&code=" + url(code)
        + "&redirect_uri=" + url(canonicalCallback());

    java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
        .uri(URI.create(DISCORD_TOKEN_URL))
        .timeout(timeout)
        .header("Content-Type", "application/x-www-form-urlencoded")
        .header("Accept", "application/json")
        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(payload))
        .build();
    java.net.http.HttpResponse<String> response =
        httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IllegalStateException("token_exchange_failed");
    }
    JsonNode root = objectMapper.readTree(response.body());
    String accessToken = text(root, "access_token");
    if (accessToken == null || accessToken.isBlank()) {
      throw new IllegalStateException("access_token_missing");
    }
    return accessToken;
  }

  private DiscordUser fetchUser(String accessToken) throws Exception {
    java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
        .connectTimeout(timeout)
        .build();
    java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
        .uri(URI.create(DISCORD_USER_URL))
        .timeout(timeout)
        .header("Authorization", "Bearer " + accessToken)
        .header("Accept", "application/json")
        .GET()
        .build();
    java.net.http.HttpResponse<String> response =
        httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IllegalStateException("fetch_user_failed");
    }
    JsonNode root = objectMapper.readTree(response.body());
    String id = text(root, "id");
    String username = text(root, "username");
    String discriminator = text(root, "discriminator");
    String handle = username;
    if (username != null && discriminator != null && !discriminator.isBlank() && !"0".equals(discriminator)) {
      handle = username + "#" + discriminator;
    }
    if (handle == null || handle.isBlank()) {
      handle = id;
    }
    String avatarUrl = buildAvatarUrl(id, text(root, "avatar"));
    return new DiscordUser(id, handle, avatarUrl);
  }

  private boolean isMemberOfGuild(String accessToken, String expectedGuildId) throws Exception {
    java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
        .connectTimeout(timeout)
        .build();
    java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
        .uri(URI.create(DISCORD_USER_GUILDS_URL))
        .timeout(timeout)
        .header("Authorization", "Bearer " + accessToken)
        .header("Accept", "application/json")
        .GET()
        .build();
    java.net.http.HttpResponse<String> response =
        httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IllegalStateException("fetch_guilds_failed");
    }
    JsonNode root = objectMapper.readTree(response.body());
    if (root == null || !root.isArray()) {
      return false;
    }
    for (JsonNode guild : root) {
      if (expectedGuildId.equals(text(guild, "id"))) {
        return true;
      }
    }
    return false;
  }

  private Response redirectWithParams(String target, String key, String value) {
    String separator = target.contains("?") ? "&" : "?";
    boolean secure = isHttpsPublicUrl();
    return Response.seeOther(URI.create(target + separator + key + "=" + value))
        .cookie(
            new jakarta.ws.rs.core.NewCookie.Builder("discord_state")
                .value("")
                .path("/")
                .maxAge(0)
                .secure(secure)
                .httpOnly(true)
                .build())
        .cookie(
            new jakarta.ws.rs.core.NewCookie.Builder("discord_redirect")
                .value("")
                .path("/")
                .maxAge(0)
                .secure(secure)
                .httpOnly(false)
                .build())
        .build();
  }

  private boolean discordConfigured() {
    return !clientId().isBlank() && !clientSecret().isBlank() && !guildId().isBlank();
  }

  private String canonicalCallback() {
    String base = publicUrl == null ? "" : publicUrl.trim();
    if (base.isBlank()) {
      base = "http://localhost:8080";
    }
    if (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    String path = callbackPath == null ? "/auth/discord/callback" : callbackPath.trim();
    if (path.isBlank()) {
      path = "/auth/discord/callback";
    }
    if (!path.startsWith("/")) {
      path = "/" + path;
    }
    return base + path;
  }

  private boolean isHttpsPublicUrl() {
    return publicUrl != null && publicUrl.toLowerCase(Locale.ROOT).startsWith("https://");
  }

  private String clientId() {
    return clientId == null ? "" : clientId.orElse("");
  }

  private String clientSecret() {
    return clientSecret == null ? "" : clientSecret.orElse("");
  }

  private String guildId() {
    return guildId == null ? "" : guildId.orElse("");
  }

  private static String text(JsonNode node, String field) {
    if (node == null || field == null || field.isBlank()) {
      return null;
    }
    JsonNode value = node.path(field);
    if (value.isMissingNode() || value.isNull()) {
      return null;
    }
    String out = value.asText(null);
    return out == null || out.isBlank() ? null : out.trim();
  }

  private static String buildAvatarUrl(String userId, String avatarHash) {
    if (userId == null || userId.isBlank() || avatarHash == null || avatarHash.isBlank()) {
      return null;
    }
    return "https://cdn.discordapp.com/avatars/" + url(userId) + "/" + url(avatarHash) + ".png?size=128";
  }

  private static String url(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static String currentUserId(SecurityIdentity identity) {
    String email = AdminUtils.getClaim(identity, "email");
    if (email != null && !email.isBlank()) {
      return email.toLowerCase(Locale.ROOT);
    }
    String sub = AdminUtils.getClaim(identity, "sub");
    return sub != null ? sub : identity.getPrincipal().getName();
  }

  private record DiscordUser(String id, String handle, String avatarUrl) {}
}
