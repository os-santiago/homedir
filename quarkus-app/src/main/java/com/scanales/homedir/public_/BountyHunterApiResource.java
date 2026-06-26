package com.scanales.homedir.public_;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scanales.homedir.reputation.bounty.*;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Public API for Bounty Hunter program.
 * Provides leaderboard, user profiles, and administrative validation endpoints.
 */
@Path("/api/bounty-hunters")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BountyHunterApiResource {

  @Inject BountyHunterService bountyHunterService;
  @Inject SecurityIdentity securityIdentity;

  @GET
  @Path("/leaderboard")
  public Response getLeaderboard(@QueryParam("limit") @DefaultValue("100") int limit) {
    if (limit < 1 || limit > 500) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(new ErrorResponse("Limit must be between 1 and 500"))
          .build();
    }

    List<BountyHunterScore> scores = bountyHunterService.getLeaderboard(limit);
    List<LeaderboardEntry> leaderboard =
        scores.stream()
            .map(
                score ->
                    new LeaderboardEntry(
                        score.userId(),
                        score.totalPoints(),
                        score.currentLevel().displayName(),
                        score.issuesCreatedCount(),
                        score.issuesResolvedCount()))
            .toList();

    return Response.ok(new LeaderboardResponse(leaderboard)).build();
  }

  @GET
  @Path("/profile/{userId}")
  public Response getProfile(@PathParam("userId") String userId) {
    Optional<BountyHunterScore> scoreOpt = bountyHunterService.getScoreForUser(userId);
    if (scoreOpt.isEmpty()) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    BountyHunterScore score = scoreOpt.get();
    List<BountyHunterEvent> events = bountyHunterService.getEventsForUser(userId);
    List<ContributionEntry> contributions =
        events.stream()
            .map(
                event ->
                    new ContributionEntry(
                        event.eventType(),
                        event.issueNumber(),
                        event.prNumber(),
                        event.pointsAwarded(),
                        event.labelName(),
                        event.timestamp()))
            .toList();

    ProfileResponse profile =
        new ProfileResponse(
            score.userId(),
            score.totalPoints(),
            score.issueCreationPoints(),
            score.issueResolutionPoints(),
            score.currentLevel().displayName(),
            score.currentLevel().rewardFrameId(),
            score.issuesCreatedCount(),
            score.issuesResolvedCount(),
            contributions);

    return Response.ok(profile).build();
  }

  @POST
  @Path("/validate-issue")
  @RolesAllowed({"admin"})
  public Response validateIssue(ValidateIssueRequest request) {
    if (request.userId == null
        || request.issueNumber == null
        || request.label == null
        || request.userId.isBlank()
        || request.issueNumber.isBlank()
        || request.label.isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(new ErrorResponse("invalid_payload"))
          .build();
    }

    String validatedBy = extractUserIdFromSecurityIdentity();

    try {
      BountyHunterScore updated =
          bountyHunterService.awardIssueCreationPoints(
              request.userId, request.issueNumber, request.label, validatedBy);
      return Response.ok(
              new ValidationResponse(
                  "ok",
                  updated.userId(),
                  updated.totalPoints(),
                  updated.currentLevel().displayName()))
          .build();
    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(new ErrorResponse(e.getMessage()))
          .build();
    }
  }

  @POST
  @Path("/resolve-issue")
  @RolesAllowed({"admin", "user"})
  public Response resolveIssue(ResolveIssueRequest request) {
    if (request.userId == null
        || request.issueNumber == null
        || request.prNumber == null
        || request.label == null
        || request.userId.isBlank()
        || request.issueNumber.isBlank()
        || request.prNumber.isBlank()
        || request.label.isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(new ErrorResponse("invalid_payload"))
          .build();
    }

    try {
      BountyHunterScore updated =
          bountyHunterService.awardIssueResolutionPoints(
              request.userId, request.issueNumber, request.prNumber, request.label);
      return Response.ok(
              new ValidationResponse(
                  "ok",
                  updated.userId(),
                  updated.totalPoints(),
                  updated.currentLevel().displayName()))
          .build();
    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(new ErrorResponse(e.getMessage()))
          .build();
    }
  }

  private String extractUserIdFromSecurityIdentity() {
    if (securityIdentity == null || securityIdentity.isAnonymous()) {
      return "anonymous";
    }
    String principal = securityIdentity.getPrincipal().getName();
    if (principal != null && principal.contains("@")) {
      return principal.split("@")[0];
    }
    return principal != null ? principal : "unknown";
  }

  // DTOs
  record ValidateIssueRequest(
      @JsonProperty("user_id") String userId,
      @JsonProperty("issue_number") String issueNumber,
      @JsonProperty("label") String label) {}

  record ResolveIssueRequest(
      @JsonProperty("user_id") String userId,
      @JsonProperty("issue_number") String issueNumber,
      @JsonProperty("pr_number") String prNumber,
      @JsonProperty("label") String label) {}

  record LeaderboardResponse(@JsonProperty("leaderboard") List<LeaderboardEntry> leaderboard) {}

  record LeaderboardEntry(
      @JsonProperty("user_id") String userId,
      @JsonProperty("total_points") long totalPoints,
      @JsonProperty("level") String level,
      @JsonProperty("issues_created") int issuesCreated,
      @JsonProperty("issues_resolved") int issuesResolved) {}

  record ProfileResponse(
      @JsonProperty("user_id") String userId,
      @JsonProperty("total_points") long totalPoints,
      @JsonProperty("issue_creation_points") long issueCreationPoints,
      @JsonProperty("issue_resolution_points") long issueResolutionPoints,
      @JsonProperty("level") String level,
      @JsonProperty("reward_frame_id") String rewardFrameId,
      @JsonProperty("issues_created") int issuesCreated,
      @JsonProperty("issues_resolved") int issuesResolved,
      @JsonProperty("contributions") List<ContributionEntry> contributions) {}

  record ContributionEntry(
      @JsonProperty("event_type") BountyHunterEventType eventType,
      @JsonProperty("issue_number") String issueNumber,
      @JsonProperty("pr_number") String prNumber,
      @JsonProperty("points") long points,
      @JsonProperty("label") String label,
      @JsonProperty("timestamp") Instant timestamp) {}

  record ValidationResponse(
      @JsonProperty("status") String status,
      @JsonProperty("user_id") String userId,
      @JsonProperty("total_points") long totalPoints,
      @JsonProperty("level") String level) {}

  record ErrorResponse(@JsonProperty("error") String error) {}
}
