package com.scanales.eventflow.public_;

import com.scanales.eventflow.model.CommunityMember;
import com.scanales.eventflow.service.CommunityService;
import com.scanales.eventflow.service.UserProfileService;
import com.scanales.eventflow.util.AdminUtils;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jboss.logging.Logger;

@Path("/comunidad")
public class CommunityResource {

  private static final Logger LOG = Logger.getLogger(CommunityResource.class);

  @Inject
  CommunityService communityService;

  @Inject
  UserProfileService userProfileService;

  @Inject
  SecurityIdentity identity;

  @Inject
  com.scanales.eventflow.service.SystemErrorService systemErrorService;

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance community(
        List<MemberView> members,
        String query,
        boolean joined,
        boolean missingGithub,
        boolean alreadyMember,
        boolean needsGithubLink,
        boolean isAuthenticated,
        MemberView current,
        long totalMembers,
        String prUrl,
        String prError,
        List<MemberView> leaderboard,
        boolean githubLinked);
  }

  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.of("es", "ES"))
      .withZone(ZoneId.systemDefault());

  @GET
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance view(
      @QueryParam("q") String query,
      @QueryParam("joined") boolean joined,
      @QueryParam("missingGithub") boolean missingGithub,
      @QueryParam("already") boolean already,
      @QueryParam("prUrl") String prUrl,
      @QueryParam("prError") String prError,
      @QueryParam("githubLinked") boolean githubLinked) {

    List<MemberView> filtered = communityService.search(query).stream().map(this::toView).toList();

    String userId = currentUserId().orElse(null);
    MemberView current = userId != null
        ? communityService.findByUserId(userId).map(this::toView).orElse(null)
        : null;
    boolean needsGithub = isAuthenticated() && userProfileService.find(userId).map(p -> !p.hasGithub()).orElse(true);

    List<MemberView> leaderboard = communityService.listMembers().stream()
        .filter(m -> m.getContributions() > 0)
        .sorted((a, b) -> Integer.compare(b.getContributions(), a.getContributions()))
        .limit(3)
        .map(this::toView)
        .toList();

    TemplateInstance template = Templates.community(
        filtered,
        query,
        joined,
        missingGithub,
        (current != null) || already,
        needsGithub,
        isAuthenticated(),
        current,
        communityService.countMembers(),
        decode(prUrl),
        prError,
        leaderboard,
        githubLinked);

    return withLayoutData(template, "comunidad");
  }

  @POST
  @Path("/unirse")
  @Authenticated
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response join(@FormParam("redirect") String redirect) {
    LOG.info("CommunityResource.join() called");
    String userId = currentUserId().orElse(null);
    if (userId == null) {
      LOG.warn("join: userId is null");
      return Response.seeOther(URI.create("/private/profile")).build();
    }
    var profile = userProfileService.upsert(userId, currentUserName().orElse(null), userEmail());
    if (!profile.hasGithub()) {
      LOG.info("join: profile has no github");
      // ... redirect logic ...
      String target = "/private/profile?linkGithub=1&redirect=/comunidad";
      if (redirect != null && !redirect.isBlank()) {
        target = "/private/profile?linkGithub=1&redirect=" + redirect;
      }
      return Response.seeOther(URI.create(target)).build();
    }
    var gh = profile.getGithub();
    LOG.infov("join: profile github={0}", gh.login());

    // ... existing checks ...
    if (communityService.findByUserId(userId).isPresent()
        || communityService.findByGithub(gh.login()).isPresent()) {
      LOG.info("join: already member");
      return Response.seeOther(URI.create("/comunidad?already=true")).build();
    }

    CommunityMember newMember = buildMember(profile, userId);
    String role = AdminUtils.isAdmin(identity) ? "administrador" : "colaborador";
    newMember.setRole(role);

    LOG.info("join: calling requestJoin");
    Optional<String> prUrl = communityService.requestJoin(newMember);
    LOG.infov("join: requestJoin result present={0}", prUrl.isPresent());

    if (prUrl.isPresent()) {
      String encoded = URLEncoder.encode(prUrl.get(), StandardCharsets.UTF_8);
      return Response.seeOther(URI.create("/comunidad?joined=true&prUrl=" + encoded)).build();
    }
    LOG.error("join: requestJoin failed (Optional.empty)");
    systemErrorService.logError("ERROR", "CommunityResource", "Join request returned empty Optional", null, userId);
    return Response.seeOther(URI.create("/comunidad?prError=github")).build();
  }

  private Optional<String> currentUserId() {
    if (!isAuthenticated()) {
      return Optional.empty();
    }
    String email = AdminUtils.getClaim(identity, "email");
    if (email != null && !email.isBlank()) {
      return Optional.of(email.toLowerCase(Locale.ROOT));
    }
    String sub = AdminUtils.getClaim(identity, "sub");
    return Optional.ofNullable(sub);
  }

  private String userEmail() {
    return currentUserId().orElse("");
  }

  private Optional<String> currentUserName() {
    if (!isAuthenticated()) {
      return Optional.empty();
    }
    String name = AdminUtils.getClaim(identity, "name");
    if (name == null || name.isBlank()) {
      name = identity.getPrincipal().getName();
    }
    return Optional.ofNullable(name);
  }

  private boolean isAuthenticated() {
    return identity != null && !identity.isAnonymous();
  }

  private MemberView toView(CommunityMember member) {
    String since = member.getJoinedAt() != null ? DATE_FMT.format(member.getJoinedAt()) : "Recién llegado";
    return new MemberView(
        member.getDisplayName(),
        member.getGithub(),
        member.roleLabel(),
        member.getProfileUrl(),
        member.getAvatarUrl(),
        since,
        member.getLevel(),
        member.getXp(),
        member.getContributions(),
        member.getBadges());
  }

  private CommunityMember buildMember(
      com.scanales.eventflow.model.UserProfile profile, String userId) {
    CommunityMember member = new CommunityMember();
    member.setUserId(userId);
    member.setDisplayName(profile.getName());
    if (profile.getGithub() != null) {
      member.setGithub(profile.getGithub().login());
      member.setProfileUrl(profile.getGithub().profileUrl());
      member.setAvatarUrl(profile.getGithub().avatarUrl());
    }
    return member;
  }

  private String decode(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return URLDecoder.decode(value, StandardCharsets.UTF_8);
    } catch (Exception e) {
      return null;
    }
  }

  public record MemberView(
      String name, String github, String role, String profileUrl, String avatarUrl, String since,
      int level, int xp, int contributions, List<String> badges) {
  }

  private TemplateInstance withLayoutData(TemplateInstance templateInstance, String activePage) {
    String name = currentUserName().orElse(null);
    return templateInstance
        .data("activePage", activePage)
        .data("userAuthenticated", isAuthenticated())
        .data("userName", name)
        .data("userInitial", initialFrom(name));
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
}
