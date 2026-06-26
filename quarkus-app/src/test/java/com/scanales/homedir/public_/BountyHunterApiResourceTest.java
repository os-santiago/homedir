package com.scanales.homedir.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import com.scanales.homedir.reputation.bounty.BountyHunterRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class BountyHunterApiResourceTest {

  @Inject BountyHunterRepository repository;

  @BeforeEach
  void setUp() {
    repository.scoresByUserId.clear();
    repository.events.clear();
  }

  @Test
  void testGetLeaderboardEmpty() {
    given()
        .when()
        .get("/api/bounty-hunters/leaderboard")
        .then()
        .statusCode(200)
        .body("size()", equalTo(0));
  }

  @Test
  void testGetLeaderboardWithLimit() {
    given()
        .queryParam("limit", 10)
        .when()
        .get("/api/bounty-hunters/leaderboard")
        .then()
        .statusCode(200);
  }

  @Test
  void testGetUserProfileNotFound() {
    given()
        .when()
        .get("/api/bounty-hunters/profile/unknown-user")
        .then()
        .statusCode(404);
  }

  @Test
  void testValidateIssue() {
    var request =
        """
        {
          "userId": "test-user",
          "issueNumber": "997",
          "labelName": "bug-impact-high",
          "validatedBy": "os-santiago"
        }
        """;

    given()
        .contentType("application/json")
        .body(request)
        .when()
        .post("/api/bounty-hunters/validate-issue")
        .then()
        .statusCode(200)
        .body("success", equalTo(true))
        .body("score.totalPoints", equalTo(30));
  }

  @Test
  void testValidateIssueUnauthorized() {
    var request =
        """
        {
          "userId": "test-user",
          "issueNumber": "997",
          "labelName": "bug-impact-high",
          "validatedBy": "random-user"
        }
        """;

    given()
        .contentType("application/json")
        .body(request)
        .when()
        .post("/api/bounty-hunters/validate-issue")
        .then()
        .statusCode(403)
        .body("success", equalTo(false));
  }

  @Test
  void testResolveIssue() {
    var request =
        """
        {
          "userId": "test-user",
          "issueNumber": "997",
          "prNumber": "999",
          "labelName": "feature-request"
        }
        """;

    given()
        .contentType("application/json")
        .body(request)
        .when()
        .post("/api/bounty-hunters/resolve-issue")
        .then()
        .statusCode(200)
        .body("success", equalTo(true))
        .body("score.totalPoints", equalTo(20));
  }

  @Test
  void testGetEligibleLabels() {
    given()
        .when()
        .get("/api/bounty-hunters/config/labels")
        .then()
        .statusCode(200)
        .body("size()", greaterThan(0))
        .body("labelName", hasItems("bug-impact-high", "feature-request"));
  }

  @Test
  void testGetLevels() {
    given()
        .when()
        .get("/api/bounty-hunters/config/levels")
        .then()
        .statusCode(200)
        .body("size()", equalTo(6))
        .body("name", hasItems("NOVICE", "EXPERIENCED", "PROFESSIONAL"));
  }
}
