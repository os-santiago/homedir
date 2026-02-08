package com.scanales.eventflow.public_;

import com.scanales.eventflow.community.CommunityBoardGroup;
import com.scanales.eventflow.community.CommunityBoardMemberView;
import com.scanales.eventflow.community.CommunityBoardService;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Optional;
import org.jboss.logging.Logger;

@Path("/comunidad/board")
public class CommunityBoardResource {
  private static final Logger LOG = Logger.getLogger(CommunityBoardResource.class);

  @Inject SecurityIdentity identity;
  @Inject CommunityBoardService boardService;

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance board(
        boolean isAuthenticated, int homedirUsers, int githubUsers, int discordUsers);

    static native TemplateInstance detail(
        boolean isAuthenticated,
        String groupPath,
        String groupTitle,
        String groupDescription,
        String searchQuery,
        int total,
        int limit,
        int offset,
        String highlightedMember,
        List<CommunityBoardMemberView> members);
  }

  @GET
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance board() {
    var summary = boardService.summary();
    TemplateInstance template =
        Templates.board(
            isAuthenticated(),
            summary.homedirUsers(),
            summary.githubUsers(),
            summary.discordUsers());
    return withLayoutData(template, "board");
  }

  @GET
  @Path("/{group}")
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance detail(
      @PathParam("group") String groupPath,
      @QueryParam("q") String query,
      @QueryParam("limit") Integer limitParam,
      @QueryParam("offset") Integer offsetParam,
      @QueryParam("member") String highlightedMember) {
    CommunityBoardGroup group =
        CommunityBoardGroup.fromPath(groupPath).orElseThrow(() -> new NotFoundException("group_not_found"));
    int limit = normalizeLimit(limitParam);
    int offset = Math.max(0, offsetParam == null ? 0 : offsetParam);
    CommunityBoardService.BoardSlice slice = boardService.list(group, query, limit, offset);
    String normalizedQuery = query == null ? "" : query.trim();
    TemplateInstance template =
        Templates.detail(
            isAuthenticated(),
            group.path(),
            titleFor(group),
            descriptionFor(group),
            normalizedQuery,
            slice.total(),
            slice.limit(),
            slice.offset(),
            normalizeHighlightedMember(highlightedMember),
            slice.items());
    return withLayoutData(template, "board");
  }

  private TemplateInstance withLayoutData(TemplateInstance template, String activeCommunitySubmenu) {
    boolean authenticated = isAuthenticated();
    String name = currentUserName().orElse(null);
    return template
        .data("activePage", "comunidad")
        .data("activeCommunitySubmenu", activeCommunitySubmenu)
        .data("userAuthenticated", authenticated)
        .data("userName", name)
        .data("userInitial", initialFrom(name));
  }

  private int normalizeLimit(Integer limitParam) {
    if (limitParam == null || limitParam <= 0) {
      return 50;
    }
    return Math.min(limitParam, 100);
  }

  private String normalizeHighlightedMember(String member) {
    if (member == null) {
      return "";
    }
    String normalized = member.trim().toLowerCase();
    return normalized;
  }

  private String titleFor(CommunityBoardGroup group) {
    return switch (group) {
      case HOMEDIR_USERS -> "HomeDir users";
      case GITHUB_USERS -> "GitHub users";
      case DISCORD_USERS -> "Discord users";
    };
  }

  private String descriptionFor(CommunityBoardGroup group) {
    return switch (group) {
      case HOMEDIR_USERS -> "People who signed in with Google and created a Homedir account.";
      case GITHUB_USERS -> "Contributors with a linked GitHub account in the OSSantiago ecosystem.";
      case DISCORD_USERS -> "People who joined our official OSSantiago Discord server.";
    };
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
}
