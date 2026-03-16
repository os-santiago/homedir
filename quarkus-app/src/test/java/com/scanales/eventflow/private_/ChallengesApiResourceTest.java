package com.scanales.eventflow.private_;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scanales.eventflow.TestDataDir;
import com.scanales.eventflow.challenges.ChallengeService;
import com.scanales.eventflow.economy.EconomyService;
import com.scanales.eventflow.model.GamificationActivity;
import com.scanales.eventflow.service.GamificationService;
import com.scanales.eventflow.service.UserProfileService;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(TestDataDir.class)
class ChallengesApiResourceTest {

  @Inject ChallengeService challengeService;
  @Inject EconomyService economyService;
  @Inject UserProfileService userProfileService;
  @Inject GamificationService gamificationService;

  @BeforeEach
  void setUp() {
    challengeService.resetForTests();
    economyService.resetForTests();
    userProfileService.upsert("member@example.com", "Member Example", "member@example.com");
  }

  @Test
  void anonymousCannotAccessChallengeProgress() {
    given().when().get("/api/private/challenges/me").then().statusCode(401);
  }

  @Test
  @TestSecurity(user = "member@example.com", roles = {"user"})
  void memberCanReadOwnChallengeProgress() {
    gamificationService.award("member@example.com", GamificationActivity.COMMUNITY_VOTE, "pick-1");

    Map<String, Object> payload =
        given()
            .when()
            .get("/api/private/challenges/me")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getMap("$");

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items = (List<Map<String, Object>>) payload.get("items");
    assertFalse(items.isEmpty());
    assertTrue(items.stream().anyMatch(item -> "community-scout".equals(item.get("id"))));
  }
}
