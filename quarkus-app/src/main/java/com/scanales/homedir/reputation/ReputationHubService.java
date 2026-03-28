package com.scanales.homedir.reputation;

import com.scanales.homedir.model.UserProfile;
import com.scanales.homedir.service.UserProfileService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToLongFunction;

@ApplicationScoped
public class ReputationHubService {

  private static final DateTimeFormatter HUB_SYNC_TIME_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

  @Inject ReputationEngineService reputationEngineService;
  @Inject UserProfileService userProfileService;

  public HubSnapshot snapshot(int limit, String viewerUserId) {
    int safeLimit = Math.max(3, Math.min(limit, 25));
    ReputationEngineService.EngineSnapshot engineSnapshot = reputationEngineService.snapshot();
    Map<String, UserReputationAggregate> aggregates = engineSnapshot.aggregatesByUser();
    GrowthGuidance viewerGuidance = growthGuidance(engineSnapshot, normalize(viewerUserId));
    return new HubSnapshot(
        HUB_SYNC_TIME_FMT.format(Instant.ofEpochMilli(engineSnapshot.generatedAtMillis())),
        aggregates.size(),
        engineSnapshot.eventsById().size(),
        viewerGuidance,
        featuredStandings(engineSnapshot, safeLimit),
        categoryLeaderboards(engineSnapshot, safeLimit),
        leaderboard(aggregates, UserReputationAggregate::weeklyScore, safeLimit),
        leaderboard(aggregates, UserReputationAggregate::monthlyScore, safeLimit),
        leaderboard(aggregates, UserReputationAggregate::risingDelta, safeLimit),
        recognizedContributions(engineSnapshot.eventsById(), safeLimit));
  }

  private List<FeaturedStanding> featuredStandings(
      ReputationEngineService.EngineSnapshot snapshot, int limit) {
    List<CategoryLeaderboard> categories = categoryLeaderboards(snapshot, limit);
    Map<String, List<LeaderboardEntry>> byCategory =
        categories.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    CategoryLeaderboard::categoryKey,
                    CategoryLeaderboard::entries,
                    (left, right) -> left,
                    java.util.LinkedHashMap::new));
    java.util.ArrayList<FeaturedStanding> out = new java.util.ArrayList<>();
    addFeaturedStanding(out, "builders", "month", firstEntry(byCategory.get("builders")));
    addFeaturedStanding(out, "helpers", "month", firstEntry(byCategory.get("helpers")));
    addFeaturedStanding(out, "learners", "month", firstEntry(byCategory.get("learners")));
    addFeaturedStanding(out, "speakers", "month", firstEntry(byCategory.get("speakers")));
    addFeaturedStanding(
        out,
        "rising",
        "week",
        firstEntry(leaderboard(snapshot.aggregatesByUser(), UserReputationAggregate::risingDelta, limit)));
    return List.copyOf(out);
  }

  private static LeaderboardEntry firstEntry(List<LeaderboardEntry> entries) {
    if (entries == null || entries.isEmpty()) {
      return null;
    }
    return entries.getFirst();
  }

  private static void addFeaturedStanding(
      List<FeaturedStanding> out, String cardKey, String periodKey, LeaderboardEntry leader) {
    if (out == null || cardKey == null || cardKey.isBlank() || leader == null) {
      return;
    }
    out.add(new FeaturedStanding(cardKey, periodKey, leader));
  }

  private GrowthGuidance growthGuidance(
      ReputationEngineService.EngineSnapshot snapshot, String viewerUserId) {
    if (snapshot == null
        || snapshot.aggregatesByUser() == null
        || viewerUserId == null
        || viewerUserId.isBlank()) {
      return null;
    }
    UserReputationAggregate aggregate = snapshot.aggregatesByUser().get(viewerUserId);
    if (aggregate == null || aggregate.totalScore() <= 0L) {
      return new GrowthGuidance("starter", "participation", 0L, 0L);
    }
    String role = ReputationProfileSummaryService.reputationRole(snapshot, viewerUserId, aggregate);
    String focusDimension = aggregate.scoresByDimension() == null
        ? "participation"
        : aggregate.scoresByDimension().entrySet().stream()
            .filter(entry -> entry.getValue() != null && entry.getValue() > 0L)
            .sorted(
                Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                    .thenComparing(Map.Entry::getKey))
            .map(Map.Entry::getKey)
            .map(ReputationProfileSummaryService::normalizeDimension)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse("participation");
    return new GrowthGuidance(role, focusDimension, aggregate.monthlyScore(), aggregate.risingDelta());
  }

  private List<CategoryLeaderboard> categoryLeaderboards(
      ReputationEngineService.EngineSnapshot snapshot, int limit) {
    if (snapshot == null || snapshot.aggregatesByUser() == null || snapshot.aggregatesByUser().isEmpty()) {
      return List.of();
    }
    return List.of(
        new CategoryLeaderboard(
            "builders",
            leaderboard(
                snapshot.aggregatesByUser(),
                aggregate -> scoreByDimension(aggregate, "contribution"),
                limit)),
        new CategoryLeaderboard(
            "helpers",
            leaderboard(
                snapshot.aggregatesByUser(),
                aggregate -> scoreByDimension(aggregate, "recognition"),
                limit)),
        new CategoryLeaderboard(
            "learners",
            leaderboard(
                snapshot.aggregatesByUser(),
                aggregate ->
                    scoreByDimension(aggregate, "participation")
                        + scoreByDimension(aggregate, "consistency"),
                limit)),
        new CategoryLeaderboard(
            "speakers",
            leaderboard(snapshot.aggregatesByUser(), aggregate -> speakerScore(snapshot, aggregate), limit)));
  }

  private List<LeaderboardEntry> leaderboard(
      Map<String, UserReputationAggregate> aggregates,
      ToLongFunction<UserReputationAggregate> scoreExtractor,
      int limit) {
    if (aggregates == null || aggregates.isEmpty()) {
      return List.of();
    }
    List<UserReputationAggregate> ranked =
        aggregates.values().stream()
            .filter(Objects::nonNull)
            .filter(aggregate -> scoreExtractor.applyAsLong(aggregate) > 0L)
            .sorted(
                Comparator.comparingLong(scoreExtractor)
                    .reversed()
                    .thenComparing(
                        Comparator.comparingLong(UserReputationAggregate::totalScore).reversed())
                    .thenComparing(
                        UserReputationAggregate::userId, Comparator.nullsLast(String::compareTo)))
            .limit(limit)
            .toList();
    java.util.ArrayList<LeaderboardEntry> out = new java.util.ArrayList<>(ranked.size());
    for (int i = 0; i < ranked.size(); i++) {
      UserReputationAggregate aggregate = ranked.get(i);
      MemberProjection member = resolveMember(aggregate.userId());
      out.add(
          new LeaderboardEntry(
              i + 1,
              aggregate.userId(),
              member.displayName(),
              member.handle(),
              member.avatarUrl(),
              member.profilePath(),
              scoreExtractor.applyAsLong(aggregate)));
    }
    return List.copyOf(out);
  }

  private List<RecognizedContribution> recognizedContributions(
      Map<String, ReputationEventRecord> eventsById, int limit) {
    if (eventsById == null || eventsById.isEmpty()) {
      return List.of();
    }
    return eventsById.values().stream()
        .filter(Objects::nonNull)
        .filter(event -> event.actorUserId() != null && !event.actorUserId().isBlank())
        .filter(event -> event.weightBase() > 0)
        .filter(this::isRecognitionEvent)
        .sorted(
            Comparator.comparing(
                    ReputationEventRecord::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(
                    Comparator.comparingInt(ReputationEventRecord::weightBase).reversed())
                .thenComparing(
                    ReputationEventRecord::eventId, Comparator.nullsLast(String::compareTo)))
        .limit(limit)
        .map(
            event -> {
              MemberProjection member = resolveMember(event.actorUserId());
              return new RecognizedContribution(
                  event.actorUserId(),
                  recognitionLabel(event),
                  recognitionEventKey(event.eventType()),
                  member.displayName(),
                  member.profilePath(),
                  member.avatarUrl(),
                  event.sourceObjectType(),
                  event.sourceObjectId(),
                  event.createdAt());
            })
        .toList();
  }

  private boolean isRecognitionEvent(ReputationEventRecord event) {
    if (event == null) {
      return false;
    }
    String eventType = normalize(event.eventType());
    String validationType = normalize(event.validationType());
    if ("recommended".equals(validationType)
        || "helpful".equals(validationType)
        || "standout".equals(validationType)) {
      return true;
    }
    return "content_recommended".equals(eventType)
        || "peer_help_acknowledged".equals(eventType)
        || "contribution_highlighted".equals(eventType);
  }

  private MemberProjection resolveMember(String userId) {
    if (userId == null || userId.isBlank()) {
      return new MemberProjection("Unknown member", null, null, null);
    }
    UserProfile profile = userProfileService.find(userId).orElse(null);
    if (profile == null) {
      return new MemberProjection(userId, null, null, null);
    }
    UserProfile.GithubAccount github = profile.getGithub();
    UserProfile.DiscordAccount discord = profile.getDiscord();
    String githubLogin = github != null ? normalize(github.login()) : null;
    String displayName = firstNonBlank(profile.getName(), githubLogin, userId);
    String handle = githubLogin != null ? "@" + githubLogin : null;
    String avatarUrl =
        firstNonBlank(
            github != null ? github.avatarUrl() : null, discord != null ? discord.avatarUrl() : null);
    String profilePath = null;
    if (githubLogin != null) {
      profilePath = "/u/" + githubLogin;
    } else if (userId.startsWith("hd-")) {
      profilePath = "/u/" + userId;
    }
    return new MemberProjection(displayName, handle, avatarUrl, profilePath);
  }

  private static String recognitionLabel(ReputationEventRecord event) {
    String validationType = normalize(event != null ? event.validationType() : null);
    if ("recommended".equals(validationType)
        || "helpful".equals(validationType)
        || "standout".equals(validationType)) {
      return validationType;
    }
    int weight = event == null ? 0 : event.weightBase();
    if (weight >= 15) {
      return "standout";
    }
    if (weight >= 10) {
      return "recommended";
    }
    return "helpful";
  }

  private static String recognitionEventKey(String eventType) {
    if (eventType == null || eventType.isBlank()) {
      return "activity_signal";
    }
    return switch (eventType) {
      case "content_recommended" -> "content_recommended";
      case "peer_help_acknowledged" -> "peer_help_acknowledged";
      case "contribution_highlighted" -> "contribution_highlighted";
      case "content_published" -> "content_published";
      case "event_speaker" -> "event_speaker";
      case "quest_completed" -> "quest_completed";
      case "event_attended" -> "event_attended";
      default -> "activity_signal";
    };
  }

  private static String firstNonBlank(String... values) {
    if (values == null) {
      return null;
    }
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

  private static String normalize(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return raw.trim().toLowerCase(java.util.Locale.ROOT);
  }

  private static long scoreByDimension(UserReputationAggregate aggregate, String dimension) {
    if (aggregate == null
        || aggregate.scoresByDimension() == null
        || dimension == null
        || dimension.isBlank()) {
      return 0L;
    }
    return Math.max(0L, aggregate.scoresByDimension().getOrDefault(dimension, 0L));
  }

  private static long speakerScore(
      ReputationEngineService.EngineSnapshot snapshot, UserReputationAggregate aggregate) {
    if (snapshot == null
        || snapshot.eventsById() == null
        || aggregate == null
        || aggregate.userId() == null
        || aggregate.userId().isBlank()) {
      return 0L;
    }
    return snapshot.eventsById().values().stream()
        .filter(Objects::nonNull)
        .filter(event -> aggregate.userId().equals(event.actorUserId()))
        .filter(event -> "event_speaker".equals(event.eventType()))
        .mapToLong(ReputationEventRecord::weightBase)
        .sum();
  }

  private record MemberProjection(
      String displayName, String handle, String avatarUrl, String profilePath) {}

  public record HubSnapshot(
      String generatedAtLabel,
      int contributors,
      int eventsCaptured,
      GrowthGuidance viewerGuidance,
      List<FeaturedStanding> featuredStandings,
      List<CategoryLeaderboard> categoryLeaderboards,
      List<LeaderboardEntry> weeklyLeaderboard,
      List<LeaderboardEntry> monthlyLeaderboard,
      List<LeaderboardEntry> risingLeaderboard,
      List<RecognizedContribution> recognizedContributions) {}

  public record GrowthGuidance(
      String roleKey, String focusDimension, long monthlyScore, long risingDelta) {}

  public record CategoryLeaderboard(String categoryKey, List<LeaderboardEntry> entries) {}

  public record FeaturedStanding(String cardKey, String periodKey, LeaderboardEntry leader) {}

  public record LeaderboardEntry(
      int rank,
      String userId,
      String displayName,
      String handle,
      String avatarUrl,
      String profilePath,
      long score) {}

  public record RecognizedContribution(
      String userId,
      String recognitionLabel,
      String eventKey,
      String displayName,
      String profilePath,
      String avatarUrl,
      String sourceObjectType,
      String sourceObjectId,
      Instant occurredAt) {}
}
