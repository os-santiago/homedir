package com.scanales.eventflow.public_;

import com.scanales.eventflow.community.CommunityBoardGroup;
import com.scanales.eventflow.community.CommunityBoardMemberView;
import com.scanales.eventflow.community.CommunityBoardService;
import com.scanales.eventflow.config.AppMessages;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import org.jboss.logging.Logger;

@Path("/comunidad/board")
public class CommunityBoardResource {
  private static final Logger LOG = Logger.getLogger(CommunityBoardResource.class);
  private static final int PAGE_SIZE = 10;
  private static final DateTimeFormatter BOARD_SYNC_TIME_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

  @Inject SecurityIdentity identity;
  @Inject CommunityBoardService boardService;
  @Inject AppMessages messages;

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance board(
        boolean isAuthenticated,
        int homedirUsers,
        int githubUsers,
        int discordUsers,
        int discordListedUsers,
        int discordLinkedProfiles,
        int discordCoveragePercent,
        Integer discordOnlineUsers,
        String discordSourceLabel,
        String discordLastSyncLabel);

    static native TemplateInstance detail(
        boolean isAuthenticated,
        String groupPath,
        String groupTitle,
        String groupDescription,
        String searchQuery,
        int total,
        int limit,
        int offset,
        int pageStart,
        int pageEnd,
        boolean hasPreviousPage,
        boolean hasNextPage,
        String previousPageUrl,
        String nextPageUrl,
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
            summary.discordUsers(),
            summary.discordListedUsers(),
            summary.discordLinkedProfiles(),
            summary.discordCoveragePercent(),
            summary.discordOnlineUsers(),
            discordSourceLabel(summary.discordDataSource()),
            formatSyncTime(summary.discordLastSyncAt()));
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
    int shown = slice.items().size();
    int pageStart = shown == 0 ? 0 : slice.offset() + 1;
    int pageEnd = shown == 0 ? 0 : slice.offset() + shown;
    boolean hasPreviousPage = slice.offset() > 0;
    boolean hasNextPage = pageEnd < slice.total();
    int previousOffset = Math.max(0, slice.offset() - slice.limit());
    int nextOffset = slice.offset() + slice.limit();
    String previousPageUrl =
        hasPreviousPage ? detailUrl(group.path(), normalizedQuery, slice.limit(), previousOffset) : null;
    String nextPageUrl =
        hasNextPage ? detailUrl(group.path(), normalizedQuery, slice.limit(), nextOffset) : null;
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
            pageStart,
            pageEnd,
            hasPreviousPage,
            hasNextPage,
            previousPageUrl,
            nextPageUrl,
            normalizeHighlightedMember(highlightedMember),
            slice.items());
    return withLayoutData(template, "board").data("ultraLiteMode", group == CommunityBoardGroup.DISCORD_USERS);
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

  private int normalizeLimit(Integer ignoredLimitParam) {
    return PAGE_SIZE;
  }

  private String normalizeHighlightedMember(String member) {
    if (member == null) {
      return "";
    }
    String normalized = member.trim().toLowerCase();
    return normalized;
  }

  private String detailUrl(String groupPath, String query, int limit, int offset) {
    StringBuilder out =
        new StringBuilder("/comunidad/board/")
            .append(groupPath)
            .append("?limit=")
            .append(limit)
            .append("&offset=")
            .append(Math.max(0, offset));
    if (query != null && !query.isBlank()) {
      out.append("&q=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));
    }
    return out.toString();
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

  private String discordSourceLabel(String sourceCode) {
    if (sourceCode == null || sourceCode.isBlank()) {
      return messages.community_board_discord_source_unavailable();
    }
    return switch (sourceCode) {
      case "bot_api" -> messages.community_board_discord_source_bot_api();
      case "preview_api" -> messages.community_board_discord_source_preview_api();
      case "widget_api" -> messages.community_board_discord_source_widget_api();
      case "file" -> messages.community_board_discord_source_file();
      case "misconfigured" -> messages.community_board_discord_source_misconfigured();
      case "disabled" -> messages.community_board_discord_source_disabled();
      default -> messages.community_board_discord_source_unavailable();
    };
  }

  private String formatSyncTime(java.time.Instant instant) {
    if (instant == null) {
      return null;
    }
    return BOARD_SYNC_TIME_FMT.format(instant);
  }
}
