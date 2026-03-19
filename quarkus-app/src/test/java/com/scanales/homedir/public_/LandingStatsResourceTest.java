package com.scanales.homedir.public_;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

@QuarkusTest
class LandingStatsResourceTest {

  @Test
  void landingStatsExposeNonNegativeValuesAndAvoidLegacyDummyTuple() {
    Response response =
        given()
            .accept("application/json")
            .header("X-Forwarded-For", "203.0.113.231")
            .when()
            .get("/api/landing/stats")
            .then()
            .statusCode(200)
            .extract()
            .response();

    long members = response.jsonPath().getLong("totalMembers");
    long xp = response.jsonPath().getLong("totalXP");
    long quests = response.jsonPath().getLong("totalQuests");
    long projects = response.jsonPath().getLong("totalProjects");

    assertTrue(members >= 0L);
    assertTrue(xp >= 0L);
    assertTrue(quests >= 0L);
    assertTrue(projects >= 0L);
    assertFalse(
        xp == 1337L && quests == 7L && projects == 3L,
        "Landing stats should be derived from persisted data, not legacy hardcoded defaults.");
  }
}
