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

  public HubSnapshot snapshot(int limit) {
    int safeLimit = Math.max(3, Math.min(limit, 25));
    ReputationEngineService.EngineSnapshot engineSnapshot = reputationEngineService.snapshot();
    Map<String, UserReputationAggregate> aggregates = engineSnapshot.aggregatesByUser();
    return new HubSnapshot(
        HUB_SYNC_TIME_FMT.format(Instant.ofEpochMilli(engineSnapshot.generatedAtMillis())),
        aggregates.size(),
        engineSnapshot.eventsById().size(),
        leaderboard(aggregates, UserReputationAggregate::weeklyScore, safeLimit),
        leaderboard(aggregates, UserReputationAggregate::monthlyScore, safeLimit),
        leaderboard(aggregates, UserReputationAggregate::risingDelta, safeLimit),
        recognizedContributions(engineSnapshot.eventsById(), safeLimit));
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
                  recognitionLabel(event.weightBase()),
                  recognitionEventKey(event.eventType()),
                  member.displayName(),
                  member.profilePath(),
                  member.avatarUrl(),
                  event.sourceObjectId(),
                  event.createdAt());
            })
        .toList();
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

  private static String recognitionLabel(int weight) {
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

  private record MemberProjection(
      String displayName, String handle, String avatarUrl, String profilePath) {}

  public record HubSnapshot(
      String generatedAtLabel,
      int contributors,
      int eventsCaptured,
      List<LeaderboardEntry> weeklyLeaderboard,
      List<LeaderboardEntry> monthlyLeaderboard,
      List<LeaderboardEntry> risingLeaderboard,
      List<RecognizedContribution> recognizedContributions) {}

  public record LeaderboardEntry(
      int rank,
      String userId,
      String displayName,
      String handle,
      String avatarUrl,
      String profilePath,
      long score) {}

  public record RecognizedContribution(
      String recognitionLabel,
      String eventKey,
      String displayName,
      String profilePath,
      String avatarUrl,
      String sourceObjectId,
      Instant occurredAt) {}
}
