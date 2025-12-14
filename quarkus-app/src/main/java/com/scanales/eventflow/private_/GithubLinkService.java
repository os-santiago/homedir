package com.scanales.eventflow.private_;

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

  @Inject
  UserProfileService profiles;

  @Inject
  com.scanales.eventflow.service.GithubService githubService;

  @Inject
  Config config;

  @ConfigProperty(name = "app.public-url", defaultValue = "http://localhost:8080")
  String publicUrl;

  public Response start(SecurityIdentity identity, String redirect) {
    if (!githubConfigured()) {
      return Response.seeOther(URI.create("/private/profile?githubConfig=missing")).build();
    }

    String state = UUID.randomUUID().toString();
    String callback = canonicalCallback();
    String target = (redirect != null && !redirect.isBlank()) ? redirect : "/private/profile";

    String authorize = "https://github.com/login/oauth/authorize?client_id="
        + url(getGithubClientId())
        + "&redirect_uri="
        + url(callback)
        + "&scope="
        + url("read:user user:email")
        + "&state="
        + url(state);

    return Response.seeOther(URI.create(authorize))
        .cookie(
            new jakarta.ws.rs.core.NewCookie.Builder("gh_state").value(state).path("/").maxAge(300).secure(true)
                .httpOnly(true).build())
        .cookie(
            new jakarta.ws.rs.core.NewCookie.Builder("gh_redirect").value(target).path("/").maxAge(300).secure(false)
                .httpOnly(false).build())
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
    // Handle anonymous users trying to "Login" instead of "Link"
    if (identity.isAnonymous()) {
      // We currently do not support creating a session via GitHub (only linking).
      // Redirect to login page with explanation.
      return Response.seeOther(URI.create("/private/profile?error=github_login_unsupported")).build();
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
      String accessToken = githubService.exchangeCode(code);
      com.scanales.eventflow.service.GithubService.GithubProfile profile = githubService.fetchUser(accessToken);

      String login = profile.login();
      String htmlUrl = profile.htmlUrl();
      String avatarUrl = profile.avatarUrl();
      String ghId = profile.id();

      String userId = currentUserId(identity);
      String name = AdminUtils.getClaim(identity, "name") != null
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
          .cookie(new jakarta.ws.rs.core.NewCookie.Builder("gh_state").value("").path("/").maxAge(0).secure(true)
              .httpOnly(true).build())
          .cookie(
              new jakarta.ws.rs.core.NewCookie.Builder("gh_redirect").value("").path("/").maxAge(0).secure(false)
                  .httpOnly(false).build())
          .build();
    } catch (Exception e) {
      LOG.error("GitHub OAuth callback failed", e);
      return redirectWithParams("/private/profile?githubError=unexpected");
    }
  }

  private Response redirectWithParams(String target) {
    return Response.seeOther(URI.create(target))
        .cookie(new jakarta.ws.rs.core.NewCookie.Builder("gh_state").value("").path("/").maxAge(0).secure(true)
            .httpOnly(true).build())
        .cookie(
            new jakarta.ws.rs.core.NewCookie.Builder("gh_redirect").value("").path("/").maxAge(0).secure(false)
                .httpOnly(false).build())
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
    return "https://homedir.opensourcesantiago.io/private/github/callback";
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
