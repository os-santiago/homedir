package com.scanales.homedir.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.scanales.homedir.reputation.bounty.*;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class BountyHunterApiResourceTest {

  @InjectMock BountyHunterService service;
  @InjectMock BountyHunterConfigService configService;

  @BeforeEach
  void setUp() {
    reset(service, configService);
  }

  @Test
  void getLeaderboard_returnsTopScores() {
    List<BountyHunterScore> scores =
        List.of(
            new BountyHunterScore(
                "user1", 500L, 300L, 200L, BountyHunterLevel.PROFESSIONAL, 10, 8, Instant.now()),
            new BountyHunterScore(
                "user2", 200L, 100L, 100L, BountyHunterLevel.EXPERIENCED, 5, 5, Instant.now()));

    when(service.getLeaderboard(50)).thenReturn(scores);

    given()
        .when()
        .get("/api/bounty-hunters/leaderboard")
        .then()
        .statusCode(200)
        .body("size()", is(2))
        .body("[0].userId", equalTo("user1"))
        .body("[0].totalPoints", equalTo(500))
        .body("[0].level", equalTo("Professional Bounty Hunter"))
        .body("[1].userId", equalTo("user2"))
        .body("[1].totalPoints", equalTo(200));
  }

  @Test
  void getLeaderboard_withLimit_respectsLimit() {
    List<BountyHunterScore> scores =
        List.of(
            new BountyHunterScore(
                "user1", 500L, 300L, 200L, BountyHunterLevel.PROFESSIONAL, 10, 8, Instant.now()));

    when(service.getLeaderboard(10)).thenReturn(scores);

    given()
        .queryParam("limit", 10)
        .when()
        .get("/api/bounty-hunters/leaderboard")
        .then()
        .statusCode(200)
        .body("size()", is(1));
  }

  @Test
  void getUserProfile_existingUser_returnsProfile() {
    BountyHunterScore score =
        new BountyHunterScore(
            "testuser", 150L, 80L, 70L, BountyHunterLevel.EXPERIENCED, 3, 2, Instant.now());

    BountyHunterEvent event =
        new BountyHunterEvent(
            "evt1",
            "testuser",
            BountyHunterEventType.ISSUE_LABEL_APPROVED,
            "123",
            null,
            15L,
            "bug-impact-medium",
            "admin",
            Instant.now());

    when(service.getUserScore("testuser")).thenReturn(Optional.of(score));
    when(service.getUserHistory("testuser")).thenReturn(List.of(event));
    when(service.getUserRank("testuser")).thenReturn(Optional.of(5));
    when(service.getTotalHuntersCount()).thenReturn(42L);

    given()
        .when()
        .get("/api/bounty-hunters/profile/testuser")
        .then()
        .statusCode(200)
        .body("userId", equalTo("testuser"))
        .body("totalPoints", equalTo(150))
        .body("issueCreationPoints", equalTo(80))
        .body("issueResolutionPoints", equalTo(70))
        .body("currentLevel", equalTo("Experienced Bounty Hunter"))
        .body("currentLevelThreshold", equalTo(150))
        .body("issuesCreatedCount", equalTo(3))
        .body("issuesResolvedCount", equalTo(2))
        .body("rank", equalTo(5))
        .body("totalHunters", equalTo(42))
        .body("history.size()", equalTo(1));
  }

  @Test
  void getUserProfile_nonExistentUser_returns404() {
    when(service.getUserScore("nonexistent")).thenReturn(Optional.empty());

    given()
        .when()
        .get("/api/bounty-hunters/profile/nonexistent")
        .then()
        .statusCode(404);
  }

  @Test
  void validateIssue_validRequest_returnsSuccess() {
    BountyHunterScore score =
        new BountyHunterScore(
            "testuser", 15L, 15L, 0L, BountyHunterLevel.NONE, 1, 0, Instant.now());

    when(service.validateIssue("testuser", "123", "bug-impact-medium", "admin"))
        .thenReturn(Optional.of(score));

    given()
        .contentType(ContentType.JSON)
        .body(
            """
        {
          "userId": "testuser",
          "issueNumber": "123",
          "labelName": "bug-impact-medium",
          "validatedBy": "admin"
        }
        """)
        .when()
        .post("/api/bounty-hunters/validate-issue")
        .then()
        .statusCode(200)
        .body("success", equalTo(true))
        .body("score.userId", equalTo("testuser"))
        .body("score.totalPoints", equalTo(15));
  }

  @Test
  void validateIssue_invalidRequest_returns403() {
    when(service.validateIssue(any(), any(), any(), any())).thenReturn(Optional.empty());

    given()
        .contentType(ContentType.JSON)
        .body(
            """
        {
          "userId": "testuser",
          "issueNumber": "123",
          "labelName": "invalid",
          "validatedBy": "regular"
        }
        """)
        .when()
        .post("/api/bounty-hunters/validate-issue")
        .then()
        .statusCode(403)
        .body("success", equalTo(false))
        .body("error", equalTo("Validation failed"));
  }

  @Test
  void resolveIssue_validRequest_returnsSuccess() {
    BountyHunterScore score =
        new BountyHunterScore(
            "testuser", 20L, 0L, 20L, BountyHunterLevel.NONE, 0, 1, Instant.now());

    when(service.recordIssueResolution("testuser", "123", "456", "feature-request"))
        .thenReturn(score);

    given()
        .contentType(ContentType.JSON)
        .body(
            """
        {
          "userId": "testuser",
          "issueNumber": "123",
          "prNumber": "456",
          "labelName": "feature-request"
        }
        """)
        .when()
        .post("/api/bounty-hunters/resolve-issue")
        .then()
        .statusCode(200)
        .body("success", equalTo(true))
        .body("score.userId", equalTo("testuser"))
        .body("score.totalPoints", equalTo(20))
        .body("score.issueResolutionPoints", equalTo(20));
  }

  @Test
  void getEligibleLabels_returnsLabelList() {
    List<IssueImpactLabel> labels =
        List.of(
            new IssueImpactLabel("bug-impact-high", 30L),
            new IssueImpactLabel("feature-request", 20L),
            new IssueImpactLabel("bug-impact-medium", 15L));

    when(configService.getAllEligibleLabels()).thenReturn(labels);

    given()
        .when()
        .get("/api/bounty-hunters/config/labels")
        .then()
        .statusCode(200)
        .body("size()", is(3))
        .body("[0].labelName", equalTo("bug-impact-high"))
        .body("[0].points", equalTo(30));
  }

  @Test
  void getLevels_returnsLevelList() {
    List<BountyHunterLevel> levels = List.of(BountyHunterLevel.values());
    when(configService.getAllLevels()).thenReturn(levels);

    given()
        .when()
        .get("/api/bounty-hunters/config/levels")
        .then()
        .statusCode(200)
        .body("size()", is(6))
        .body("[0].name", equalTo("NONE"))
        .body("[1].name", equalTo("NOVICE"))
        .body("[1].requiredPoints", equalTo(50));
  }
}
