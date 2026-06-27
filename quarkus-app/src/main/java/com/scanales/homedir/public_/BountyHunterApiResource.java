package com.scanales.homedir.public_;

import com.scanales.homedir.reputation.bounty.BountyHunterConfigService;
import com.scanales.homedir.reputation.bounty.BountyHunterEvent;
import com.scanales.homedir.reputation.bounty.BountyHunterLevel;
import com.scanales.homedir.reputation.bounty.BountyHunterScore;
import com.scanales.homedir.reputation.bounty.BountyHunterService;
import com.scanales.homedir.reputation.bounty.IssueImpactLabel;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Path("/api/bounty-hunters")
@Produces(MediaType.APPLICATION_JSON)
public class BountyHunterApiResource {

  @Inject BountyHunterService service;
  @Inject BountyHunterConfigService configService;

  @GET
  @Path("/leaderboard")
  public Response getLeaderboard(@QueryParam("limit") Integer limit) {
    int effectiveLimit = limit == null ? 50 : Math.max(1, limit);
    List<Map<String, Object>> leaderboard =
        service.getLeaderboard(effectiveLimit).stream().map(this::toLeaderboardEntry).toList();
    return Response.ok(leaderboard).build();
  }

  @GET
  @Path("/profile/{userId}")
  public Response getProfile(@PathParam("userId") String userId) {
    Optional<BountyHunterScore> score = service.getUserScore(userId);
    if (score.isEmpty()) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(Map.of("error", "not_found"))
          .build();
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    BountyHunterScore resolvedScore = score.get();
    payload.put("userId", resolvedScore.userId());
    payload.put("totalPoints", resolvedScore.totalPoints());
    payload.put("issueCreationPoints", resolvedScore.issueCreationPoints());
    payload.put("issueResolutionPoints", resolvedScore.issueResolutionPoints());
    payload.put("currentLevel", resolvedScore.currentLevel().getDisplayName());
    payload.put("currentLevelThreshold", resolvedScore.currentLevel().getRequiredPoints());
    payload.put("issuesCreatedCount", resolvedScore.issuesCreatedCount());
    payload.put("issuesResolvedCount", resolvedScore.issuesResolvedCount());
    payload.put("rank", service.getUserRank(userId).orElse(null));
    payload.put("totalHunters", service.getTotalHuntersCount());
    payload.put("history", service.getUserHistory(userId).stream().map(this::toEvent).toList());
    return Response.ok(payload).build();
  }

  @POST
  @Path("/validate-issue")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response validateIssue(ValidateIssueRequest request) {
    Optional<BountyHunterScore> score =
        service.validateIssue(
            request.userId(), request.issueNumber(), request.labelName(), request.validatedBy());
    if (score.isEmpty()) {
      return Response.status(Response.Status.FORBIDDEN)
          .entity(Map.of("success", false, "error", "Validation failed"))
          .build();
    }
    return Response.ok(Map.of("success", true, "score", toProfile(score.get()))).build();
  }

  @POST
  @Path("/resolve-issue")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response resolveIssue(ResolveIssueRequest request) {
    BountyHunterScore score =
        service.recordIssueResolution(
            request.userId(), request.issueNumber(), request.prNumber(), request.labelName());
    return Response.ok(Map.of("success", true, "score", toProfile(score))).build();
  }

  @GET
  @Path("/config/labels")
  public List<Map<String, Object>> getEligibleLabels() {
    return configService.getAllEligibleLabels().stream().map(this::toLabel).toList();
  }

  @GET
  @Path("/config/levels")
  public List<Map<String, Object>> getLevels() {
    return configService.getAllLevels().stream().map(this::toLevel).toList();
  }

  private Map<String, Object> toLeaderboardEntry(BountyHunterScore score) {
    return Map.of(
        "userId", score.userId(),
        "totalPoints", score.totalPoints(),
        "level", score.currentLevel().getDisplayName(),
        "updatedAt", score.updatedAt());
  }

  private Map<String, Object> toProfile(BountyHunterScore score) {
    return Map.of(
        "userId", score.userId(),
        "totalPoints", score.totalPoints(),
        "issueCreationPoints", score.issueCreationPoints(),
        "issueResolutionPoints", score.issueResolutionPoints(),
        "currentLevel", score.currentLevel().getDisplayName(),
        "currentLevelThreshold", score.currentLevel().getRequiredPoints(),
        "issuesCreatedCount", score.issuesCreatedCount(),
        "issuesResolvedCount", score.issuesResolvedCount(),
        "updatedAt", score.updatedAt());
  }

  private Map<String, Object> toEvent(BountyHunterEvent event) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("eventId", event.eventId());
    payload.put("userId", event.userId());
    payload.put("eventType", event.eventType().name());
    payload.put("issueNumber", event.issueNumber());
    payload.put("prNumber", event.prNumber());
    payload.put("pointsAwarded", event.pointsAwarded());
    payload.put("label", event.label());
    payload.put("validatedByUserId", event.validatedByUserId());
    payload.put("timestamp", event.timestamp());
    return payload;
  }

  private Map<String, Object> toLabel(IssueImpactLabel label) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("labelName", label.labelName());
    payload.put("points", label.points());
    payload.put("enabled", label.enabled());
    payload.put("description", label.description());
    return payload;
  }

  private Map<String, Object> toLevel(BountyHunterLevel level) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("name", level.name());
    payload.put("requiredPoints", level.getRequiredPoints());
    payload.put("displayName", level.getDisplayName());
    payload.put("rewardFrameId", level.getRewardFrameId());
    return payload;
  }

  public record ValidateIssueRequest(
      String userId, String issueNumber, String labelName, String validatedBy) {}

  public record ResolveIssueRequest(
      String userId, String issueNumber, String prNumber, String labelName) {}
}
