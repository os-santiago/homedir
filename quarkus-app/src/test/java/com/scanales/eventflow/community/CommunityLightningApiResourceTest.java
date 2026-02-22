package com.scanales.eventflow.community;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class CommunityLightningApiResourceTest {

  @Inject CommunityLightningService lightningService;

  @BeforeEach
  void setup() {
    lightningService.clearAllForTests();
  }

  @Test
  void createThreadRequiresAuthentication() {
    given()
        .contentType("application/json")
        .body(
            """
            {
              "mode":"lightning_thread",
              "title":"First lightning",
              "body":"Keep shipping."
            }
            """)
        .when()
        .post("/api/community/lightning/threads")
        .then()
        .statusCode(401);
  }

  @Test
  @TestSecurity(user = "user@example.com")
  void authenticatedUserCanCreateAndListPublishedThreads() {
    given()
        .contentType("application/json")
        .body(
            """
            {
              "mode":"short_debate",
              "title":"Golden path",
              "body":"Should teams default to platform templates?"
            }
            """)
        .when()
        .post("/api/community/lightning/threads")
        .then()
        .statusCode(201)
        .body("item.mode", equalTo("short_debate"));

    given()
        .accept("application/json")
        .when()
        .get("/api/community/lightning?limit=10&offset=0")
        .then()
        .statusCode(200)
        .body("items", hasSize(greaterThanOrEqualTo(1)))
        .body("items[0].title", equalTo("Golden path"));
  }

  @Test
  @TestSecurity(user = "user@example.com")
  void secondThreadWithinHourReturnsRateLimit() {
    String payload =
        """
        {
          "mode":"lightning_thread",
          "title":"Post %s",
          "body":"Body for %s"
        }
        """;

    given()
        .contentType("application/json")
        .body(payload.formatted("A", "A"))
        .when()
        .post("/api/community/lightning/threads")
        .then()
        .statusCode(201);

    given()
        .contentType("application/json")
        .body(payload.formatted("B", "B"))
        .when()
        .post("/api/community/lightning/threads")
        .then()
        .statusCode(429)
        .body("error", equalTo("user_hourly_post_limit"));
  }
}
