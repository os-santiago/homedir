package com.scanales.eventflow.public_;

import com.scanales.eventflow.community.CommunityBoardGroup;
import com.scanales.eventflow.community.CommunityBoardMemberView;
import com.scanales.eventflow.community.CommunityBoardService;
import com.scanales.eventflow.service.UserProfileService;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import org.jboss.logging.Logger;

@Path("/community/member")
public class CommunityMemberResource {
  private static final Logger LOG = Logger.getLogger(CommunityMemberResource.class);

  @Inject SecurityIdentity identity;
  @Inject CommunityBoardService boardService;
  @Inject UserProfileService userProfileService;

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance profile(
        boolean isAuthenticated,
        String groupPath,
        String groupTitle,
        String displayName,
        String handle,
        String avatarUrl,
        String memberSince,
        String profileLink,
        String boardLink);
  }

  @GET
  @Path("/{group}/{id}")
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public Response profile(@PathParam("group") String groupPath, @PathParam("id") String id) {
    Optional<CommunityBoardGroup> groupOpt = CommunityBoardGroup.fromPath(groupPath);
    if (groupOpt.isEmpty()) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    CommunityBoardGroup group = groupOpt.get();
    Optional<CommunityBoardMemberView> memberOpt = boardService.findMember(group, id);
    if (memberOpt.isEmpty()) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    CommunityBoardMemberView member = memberOpt.get();
    TemplateInstance template =
        Templates.profile(
            isAuthenticated(),
            group.path(),
            titleFor(group),
            member.displayName(),
            member.handle(),
            member.avatarUrl(),
            member.memberSince(),
            profileLinkFor(group, member),
            "/comunidad/board/" + group.path());
    return Response.ok(withLayoutData(template, "board")).build();
  }

  private String profileLinkFor(CommunityBoardGroup group, CommunityBoardMemberView member) {
    if (group == CommunityBoardGroup.DISCORD_USERS) {
      Optional<com.scanales.eventflow.model.UserProfile> profileOpt = userProfileService.findByDiscordId(member.id());
      if (profileOpt.isPresent()) {
        String canonical = canonicalProfilePath(profileOpt.get());
        if (canonical != null) {
          return canonical;
        }
      }
    }
    if (group == CommunityBoardGroup.GITHUB_USERS) {
      return "/u/" + member.id();
    }
    if (group == CommunityBoardGroup.HOMEDIR_USERS
        && member.handle() != null
        && member.handle().startsWith("@")
        && member.handle().length() > 1) {
      return "/u/" + member.handle().substring(1);
    }
    if (group == CommunityBoardGroup.HOMEDIR_USERS && member.id() != null && member.id().startsWith("hd-")) {
      return "/u/" + member.id();
    }
    return "/comunidad/board/" + group.path() + "?member=" + member.id();
  }

  private String canonicalProfilePath(com.scanales.eventflow.model.UserProfile profile) {
    String githubLogin = normalizeId(profile.getGithub() != null ? profile.getGithub().login() : null);
    if (githubLogin != null) {
      return "/u/" + urlEncode(githubLogin);
    }
    String homedirId = homedirMemberId(firstNonBlank(profile.getUserId(), profile.getEmail()), null);
    if (homedirId == null) {
      return null;
    }
    return "/u/" + urlEncode(homedirId);
  }

  private String titleFor(CommunityBoardGroup group) {
    return switch (group) {
      case HOMEDIR_USERS -> "HomeDir users";
      case GITHUB_USERS -> "GitHub users";
      case DISCORD_USERS -> "Discord users";
    };
  }

  private TemplateInstance withLayoutData(TemplateInstance template, String activeCommunitySubmenu) {
    boolean authenticated = isAuthenticated();
    String name = currentUserName().orElse(null);
    return template
        .data("activePage", "comunidad")
        .data("mainClass", "community-ultra-lite")
        .data("activeCommunitySubmenu", activeCommunitySubmenu)
        .data("userAuthenticated", authenticated)
        .data("userName", name)
        .data("userInitial", initialFrom(name));
  }

  private Optional<String> currentUserName() {
    if (!isAuthenticated()) {
      return Optional.empty();
    }
    String name = identity.getAttribute("name");
    if (name == null || name.isBlank()) {
      name = identity.getPrincipal() != null ? identity.getPrincipal().getName() : null;
    }
    return Optional.ofNullable(name);
  }

  private boolean isAuthenticated() {
    try {
      return identity != null && !identity.isAnonymous();
    } catch (Exception e) {
      LOG.warn("Security identity check failed (treating as anonymous): " + e.getMessage());
      return false;
    }
  }

  private String initialFrom(String name) {
    if (name == null) {
      return null;
    }
    String trimmed = name.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    return trimmed.substring(0, 1).toUpperCase();
  }

  private static String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static String normalizeId(String raw) {
    if (raw == null) {
      return null;
    }
    String normalized = raw.trim().toLowerCase();
    return normalized.isBlank() ? null : normalized;
  }

  private static String firstNonBlank(String... values) {
    if (values == null) {
      return null;
    }
    for (String value : values) {
      if (value == null) {
        continue;
      }
      String trimmed = value.trim();
      if (!trimmed.isEmpty()) {
        return trimmed;
      }
    }
    return null;
  }

  private static String homedirMemberId(String identitySeed, String githubLogin) {
    String github = normalizeId(githubLogin);
    if (github != null) {
      return "gh-" + github;
    }
    String seed = normalizeId(identitySeed);
    if (seed == null) {
      return null;
    }
    return "hd-" + shortHash(seed, 16);
  }

  private static String shortHash(String value, int maxLength) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(hashed.length * 2);
      for (byte b : hashed) {
        hex.append(String.format("%02x", b));
      }
      int end = Math.min(hex.length(), Math.max(6, maxLength));
      return hex.substring(0, end);
    } catch (Exception e) {
      return Integer.toHexString(value.hashCode());
    }
  }
}
