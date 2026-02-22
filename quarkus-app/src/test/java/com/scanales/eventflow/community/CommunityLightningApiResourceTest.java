package com.scanales.eventflow.community;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

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
    String threadId =
        given()
            .contentType("application/json")
            .body(
                """
                {
                  "statement":"Docker or Podman?"
                }
                """)
            .when()
            .post("/api/community/lightning/threads")
            .then()
            .statusCode(201)
            .body("item.mode", equalTo("sharp_statement"))
            .extract()
            .path("item.id");

    given()
        .accept("application/json")
        .when()
        .get("/api/community/lightning?limit=10&offset=0")
        .then()
        .statusCode(200)
        .body("items", hasSize(greaterThanOrEqualTo(1)))
        .body("items[0].id", equalTo(threadId))
        .body("items[0].title", equalTo("Docker or Podman?"));
  }

  @Test
  @TestSecurity(user = "user@example.com")
  void secondThreadWithinHourReturnsRateLimit() {
    String payload =
        """
        {
          "statement":"Post %s"
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

  @Test
  @TestSecurity(user = "user@example.com")
  void secondCommentWithinMinuteReturnsRateLimit() {
    String threadId =
        given()
            .contentType("application/json")
            .body(
                """
                {
                  "statement":"Sharp test thread"
                }
                """)
            .when()
            .post("/api/community/lightning/threads")
            .then()
            .statusCode(201)
            .extract()
            .path("item.id");

    given()
        .contentType("application/json")
        .body("{\"body\":\"First concise reply.\"}")
        .when()
        .post("/api/community/lightning/threads/" + threadId + "/comments")
        .then()
        .statusCode(200);

    given()
        .contentType("application/json")
        .body("{\"body\":\"Second concise reply.\"}")
        .when()
        .post("/api/community/lightning/threads/" + threadId + "/comments")
        .then()
        .statusCode(429)
        .body("error", equalTo("user_comment_rate_limit"));
  }

  @Test
  @TestSecurity(user = "editor@example.com")
  void ownerCanEditThreadAndComment() {
    String threadId =
        given()
            .contentType("application/json")
            .body("{\"statement\":\"Docker or Podman?\"}")
            .when()
            .post("/api/community/lightning/threads")
            .then()
            .statusCode(201)
            .extract()
            .path("item.id");

    given()
        .contentType("application/json")
        .body("{\"statement\":\"Docker and Podman together?\"}")
        .when()
        .put("/api/community/lightning/threads/" + threadId)
        .then()
        .statusCode(200)
        .body("item.title", equalTo("Docker and Podman together?"))
        .body("item.is_owner", equalTo(true))
        .body("item.updated_at", notNullValue());

    String commentId =
        given()
            .contentType("application/json")
            .body("{\"body\":\"First concise reply.\"}")
            .when()
            .post("/api/community/lightning/threads/" + threadId + "/comments")
            .then()
            .statusCode(200)
            .extract()
            .path("comment.id");

    given()
        .contentType("application/json")
        .body("{\"body\":\"Edited concise reply.\"}")
        .when()
        .put("/api/community/lightning/comments/" + commentId)
        .then()
        .statusCode(200)
        .body("comment.body", equalTo("Edited concise reply."))
        .body("comment.is_owner", equalTo(true))
        .body("comment.updated_at", notNullValue())
        .body("thread.last_comment_at", notNullValue());
  }

  @Test
  @TestSecurity(user = "intruder@example.com")
  void nonOwnerCannotEditThread() {
    String threadId =
        lightningService
            .createThread(
                "owner@example.com",
                "Owner",
                new CommunityLightningService.CreateThreadRequest(
                    "sharp_statement", "Owner title", "Owner title"))
            .item()
            .id();

    given()
        .contentType("application/json")
        .body("{\"statement\":\"Intruder edit\"}")
        .when()
        .put("/api/community/lightning/threads/" + threadId)
        .then()
        .statusCode(403)
        .body("error", equalTo("thread_edit_forbidden"));
  }
}
