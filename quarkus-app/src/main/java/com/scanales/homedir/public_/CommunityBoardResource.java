package com.scanales.homedir.public_;

import com.scanales.homedir.community.CommunityBoardGroup;
import com.scanales.homedir.community.CommunityBoardMemberView;
import com.scanales.homedir.community.CommunityBoardService;
import com.scanales.homedir.config.AppMessages;
import com.scanales.homedir.model.GamificationActivity;
import com.scanales.homedir.reputation.ReputationFeatureFlags;
import com.scanales.homedir.service.GamificationService;
import com.scanales.homedir.service.UsageMetricsService;
import com.scanales.homedir.service.UserProfileService;
import com.scanales.homedir.util.AdminUtils;
import com.scanales.homedir.util.PaginationGuardrails;
import com.scanales.homedir.util.TemplateLocaleUtil;
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
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.jboss.logging.Logger;

@Path("/comunidad/board")
public class CommunityBoardResource {
  private static final Logger LOG = Logger.getLogger(CommunityBoardResource.class);
  private static final int PAGE_SIZE = 10;
  private static final int MAX_OFFSET = PaginationGuardrails.MAX_OFFSET;
  private static final DateTimeFormatter BOARD_SYNC_TIME_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

  @Inject SecurityIdentity identity;
  @Inject CommunityBoardService boardService;
  @Inject AppMessages messages;
  @Inject GamificationService gamificationService;
  @Inject UsageMetricsService metrics;
  @Inject UserProfileService userProfiles;
  @Inject ReputationFeatureFlags reputationFeatureFlags;

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance board(
        boolean isAuthenticated,
        boolean discordLinked,
        int homedirUsers,
        int githubUsers,
        int discordUsers,
        int discordListedUsers,
        int discordLinkedProfiles,
        int discordCoveragePercent,
        Integer discordOnlineUsers,
        String discordSourceLabel,
        String discordLastSyncLabel,
        String publicProfilePath,
        String myHomedirSearchUrl,
        String myGithubSearchUrl,
        String myDiscordSearchUrl);

    static native TemplateInstance detail(
        boolean isAuthenticated,
        boolean discordLinked,
        String groupPath,
        String groupTitle,
        String groupDescription,
        int discordGuildMembers,
        int discordListedUsers,
        int discordLinkedProfiles,
        int discordCoveragePercent,
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
        boolean hasSearchQuery,
        String publicProfilePath,
        String myMemberSearchUrl,
        List<CommunityBoardMemberView> members);
  }

  @GET
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance board(
      @jakarta.ws.rs.CookieParam("QP_LOCALE") String localeCookie,
      @jakarta.ws.rs.core.Context HttpHeaders headers,
      @jakarta.ws.rs.core.Context io.vertx.ext.web.RoutingContext context) {
    metrics.recordPageView("/comunidad/board", headers, context);
    currentUserId()
        .ifPresent(userId -> gamificationService.award(userId, GamificationActivity.COMMUNITY_BOARD_VIEW));
    var summary = boardService.summary();
    BoardIdentity boardIdentity = resolveBoardIdentity();
    TemplateInstance template =
        Templates.board(
            isAuthenticated(),
            boardIdentity.discordLinked(),
            summary.homedirUsers(),
            summary.githubUsers(),
            summary.discordUsers(),
            summary.discordListedUsers(),
            summary.discordLinkedProfiles(),
            summary.discordCoveragePercent(),
            summary.discordOnlineUsers(),
            discordSourceLabel(summary.discordDataSource()),
            formatSyncTime(summary.discordLastSyncAt()),
            boardIdentity.publicProfilePath(),
            boardIdentity.homedirSearchUrl(),
            boardIdentity.githubSearchUrl(),
            boardIdentity.discordSearchUrl());
    return withLayoutData(template, "board", localeCookie);
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
      @QueryParam("member") String highlightedMember,
      @jakarta.ws.rs.CookieParam("QP_LOCALE") String localeCookie,
      @jakarta.ws.rs.core.Context HttpHeaders headers,
      @jakarta.ws.rs.core.Context io.vertx.ext.web.RoutingContext context) {
    CommunityBoardGroup group =
        CommunityBoardGroup.fromPath(groupPath).orElseThrow(() -> new NotFoundException("group_not_found"));
    metrics.recordPageView("/comunidad/board/" + group.path(), headers, context);
    currentUserId()
        .ifPresent(
            userId ->
                gamificationService.award(
                    userId, GamificationActivity.COMMUNITY_BOARD_MEMBERS_VIEW, group.path()));
    int limit = normalizeLimit(limitParam);
    int offset = PaginationGuardrails.clampOffset(offsetParam, MAX_OFFSET);
    CommunityBoardService.BoardSlice slice = boardService.list(group, query, limit, offset);
    String normalizedQuery = query == null ? "" : query.trim();
    int shown = slice.items().size();
    int pageStart = shown == 0 ? 0 : slice.offset() + 1;
    int pageEnd = shown == 0 ? 0 : slice.offset() + shown;
    boolean hasPreviousPage = slice.offset() > 0;
    boolean hasNextPage = pageEnd < slice.total();
    int previousOffset = Math.max(0, slice.offset() - slice.limit());
    int nextOffset = slice.offset() + slice.limit();
    var summary = boardService.summary();
    String previousPageUrl =
        hasPreviousPage ? detailUrl(group.path(), normalizedQuery, slice.limit(), previousOffset) : null;
    String nextPageUrl =
        hasNextPage ? detailUrl(group.path(), normalizedQuery, slice.limit(), nextOffset) : null;
    BoardIdentity boardIdentity = resolveBoardIdentity();
    TemplateInstance template =
        Templates.detail(
            isAuthenticated(),
            boardIdentity.discordLinked(),
            group.path(),
            titleFor(group),
            descriptionFor(group),
            summary.discordUsers(),
            summary.discordListedUsers(),
            summary.discordLinkedProfiles(),
            summary.discordCoveragePercent(),
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
            normalizedQuery != null && !normalizedQuery.isBlank(),
            boardIdentity.publicProfilePath(),
            boardIdentity.searchUrlFor(group),
            slice.items());
    return withLayoutData(template, "board", localeCookie)
        .data("ultraLiteMode", group == CommunityBoardGroup.DISCORD_USERS);
  }

  private TemplateInstance withLayoutData(
      TemplateInstance template, String activeCommunitySubmenu, String localeCookie) {
    boolean authenticated = isAuthenticated();
    boolean isAdmin = AdminUtils.isAdmin(identity);
    String name = currentUserName().orElse(null);
    ReputationFeatureFlags.Flags flags = reputationFeatureFlags.snapshot();
    boolean showReputationHub =
        flags.hubUiEnabled() && (isAdmin || flags.hubNavPublicEnabled());
    return TemplateLocaleUtil.apply(template, localeCookie)
        .data("activePage", "comunidad")
        .data("mainClass", "community-ultra-lite")
        .data("noLoginModal", true)
        .data("activeCommunitySubmenu", activeCommunitySubmenu)
        .data("userAuthenticated", authenticated)
        .data("isAdmin", isAdmin)
        .data("showReputationHub", showReputationHub)
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

  private String searchUrlForMember(CommunityBoardGroup group, String memberId) {
    if (memberId == null || memberId.isBlank()) {
      return null;
    }
    String groupPath = group.path();
    return "/comunidad/board/"
        + groupPath
        + "?limit="
        + PAGE_SIZE
        + "&offset=0&q="
        + URLEncoder.encode(memberId, StandardCharsets.UTF_8);
  }

  private BoardIdentity resolveBoardIdentity() {
    if (!isAuthenticated()) {
      return BoardIdentity.empty();
    }
    String email = currentUserId().orElse(null);
    if (email == null || email.isBlank()) {
      return BoardIdentity.empty();
    }
    var profile = userProfiles.find(email).orElse(null);
    String githubLogin = null;
    String discordId = null;
    if (profile != null) {
      githubLogin = normalizeId(profile.getGithub() != null ? profile.getGithub().login() : null);
      discordId = normalizeDiscordId(profile.getDiscord() != null ? profile.getDiscord().id() : null);
    }
    String publicHandle = resolvePublicProfileHandle(email, githubLogin);
    String publicProfilePath =
        publicHandle == null ? null : "/u/" + URLEncoder.encode(publicHandle, StandardCharsets.UTF_8);
    String homedirMemberId = resolveHomedirMemberId(email, githubLogin);
    return new BoardIdentity(
        publicProfilePath,
        searchUrlForMember(CommunityBoardGroup.HOMEDIR_USERS, homedirMemberId),
        searchUrlForMember(CommunityBoardGroup.GITHUB_USERS, githubLogin),
        searchUrlForMember(CommunityBoardGroup.DISCORD_USERS, discordId),
        discordId != null);
  }

  private String resolvePublicProfileHandle(String userId, String githubLogin) {
    String github = normalizeId(githubLogin);
    if (github != null) {
      return github;
    }
    String seed = normalizeId(userId);
    if (seed == null) {
      return null;
    }
    return "hd-" + shortHash(seed, 16);
  }

  private String resolveHomedirMemberId(String userId, String githubLogin) {
    String github = normalizeId(githubLogin);
    if (github != null) {
      return "gh-" + github;
    }
    String seed = normalizeId(userId);
    if (seed == null) {
      return null;
    }
    return "hd-" + shortHash(seed, 16);
  }

  private String normalizeId(String raw) {
    if (raw == null) {
      return null;
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    return normalized.isBlank() ? null : normalized;
  }

  private String normalizeDiscordId(String raw) {
    String normalized = normalizeId(raw);
    if (normalized == null) {
      return null;
    }
    for (int i = 0; i < normalized.length(); i++) {
      if (!Character.isDigit(normalized.charAt(i))) {
        return null;
      }
    }
    return normalized;
  }

  private String shortHash(String value, int maxLength) {
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

  private Optional<String> currentUserId() {
    if (!isAuthenticated()) {
      return Optional.empty();
    }
    String email = AdminUtils.getClaim(identity, "email");
    if (email != null && !email.isBlank()) {
      return Optional.of(email.toLowerCase(Locale.ROOT));
    }
    String principal = identity.getPrincipal() != null ? identity.getPrincipal().getName() : null;
    if (principal == null || principal.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(principal.toLowerCase(Locale.ROOT));
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

  private record BoardIdentity(
      String publicProfilePath,
      String homedirSearchUrl,
      String githubSearchUrl,
      String discordSearchUrl,
      boolean discordLinked) {
    static BoardIdentity empty() {
      return new BoardIdentity(null, null, null, null, false);
    }

    String searchUrlFor(CommunityBoardGroup group) {
      return switch (group) {
        case HOMEDIR_USERS -> homedirSearchUrl;
        case GITHUB_USERS -> githubSearchUrl;
        case DISCORD_USERS -> discordSearchUrl;
      };
    }
  }
}
