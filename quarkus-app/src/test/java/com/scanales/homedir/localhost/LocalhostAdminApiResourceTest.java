package com.scanales.homedir.localhost;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for the localhost admin API endpoint. These tests verify authentication, authorization, and
 * functionality.
 */
@QuarkusTest
public class LocalhostAdminApiResourceTest {

  private static final String VALID_TOKEN = "test-admin-token-12345";
  private static final String INVALID_TOKEN = "wrong-token";
  private static final String EVENT_ID = "test-event-2026";

  @Test
  public void testStatusEndpointWithValidToken() {
    given()
        .header("Authorization", "Bearer " + VALID_TOKEN)
        .when()
        .get("/api/localhost-admin/status")
        .then()
        .statusCode(200)
        .body("authenticated", equalTo(true))
        .body("mode", equalTo("localhost-admin"));
  }

  @Test
  public void testStatusEndpointWithoutToken() {
    given()
        .when()
        .get("/api/localhost-admin/status")
        .then()
        .statusCode(401)
        .body("error", equalTo("missing_token"));
  }

  @Test
  public void testStatusEndpointWithInvalidToken() {
    given()
        .header("Authorization", "Bearer " + INVALID_TOKEN)
        .when()
        .get("/api/localhost-admin/status")
        .then()
        .statusCode(403)
        .body("error", equalTo("invalid_token"));
  }

  @Test
  public void testGetEvents() {
    given()
        .header("Authorization", "Bearer " + VALID_TOKEN)
        .when()
        .get("/api/localhost-admin/events")
        .then()
        .statusCode(200)
        .body("$", instanceOf(java.util.List.class));
  }

  @Test
  public void testGetCfpSubmissionNotFound() {
    given()
        .header("Authorization", "Bearer " + VALID_TOKEN)
        .when()
        .get("/api/localhost-admin/cfp/" + EVENT_ID + "/non-existent-id")
        .then()
        .statusCode(404)
        .body("error", equalTo("not_found"));
  }

  @Test
  public void testUpdateCfpStatusWithMissingStatus() {
    given()
        .header("Authorization", "Bearer " + VALID_TOKEN)
        .contentType(ContentType.JSON)
        .body(Map.of("note", "Test note"))
        .when()
        .put("/api/localhost-admin/cfp/" + EVENT_ID + "/some-id/status")
        .then()
        .statusCode(400)
        .body("error", equalTo("missing_status"));
  }

  @Test
  public void testUpdateCfpStatusWithInvalidStatus() {
    given()
        .header("Authorization", "Bearer " + VALID_TOKEN)
        .contentType(ContentType.JSON)
        .body(Map.of("status", "invalid_status_value"))
        .when()
        .put("/api/localhost-admin/cfp/" + EVENT_ID + "/some-id/status")
        .then()
        .statusCode(400)
        .body("error", equalTo("invalid_status"));
  }

  @Test
  public void testGetUsers() {
    given()
        .header("Authorization", "Bearer " + VALID_TOKEN)
        .when()
        .get("/api/localhost-admin/users")
        .then()
        .statusCode(200)
        .body("$", instanceOf(java.util.List.class));
  }

  @Test
  public void testGetUsersWithQuery() {
    given()
        .header("Authorization", "Bearer " + VALID_TOKEN)
        .queryParam("query", "test")
        .when()
        .get("/api/localhost-admin/users")
        .then()
        .statusCode(200)
        .body("$", instanceOf(java.util.List.class));
  }

  @Test
  public void testAddUserXpWithMissingParameters() {
    given()
        .header("Authorization", "Bearer " + VALID_TOKEN)
        .contentType(ContentType.JSON)
        .body(Map.of("amount", 100))
        .when()
        .post("/api/localhost-admin/users/test-user/xp")
        .then()
        .statusCode(400)
        .body("error", equalTo("missing_parameters"));
  }

  @Test
  public void testUpdateUserClassWithMissingQuestClass() {
    given()
        .header("Authorization", "Bearer " + VALID_TOKEN)
        .contentType(ContentType.JSON)
        .body(Map.of())
        .when()
        .post("/api/localhost-admin/users/test-user/quest-class")
        .then()
        .statusCode(400)
        .body("error", equalTo("missing_quest_class"));
  }

  @Test
  public void testGetMetrics() {
    given()
        .header("Authorization", "Bearer " + VALID_TOKEN)
        .when()
        .get("/api/localhost-admin/metrics")
        .then()
        .statusCode(200);
  }

  @Test
  public void testMalformedBearerHeader() {
    given()
        .header("Authorization", "NotBearer token")
        .when()
        .get("/api/localhost-admin/status")
        .then()
        .statusCode(401)
        .body("error", equalTo("missing_token"));
  }

  @Test
  public void testEmptyBearerToken() {
    given()
        .header("Authorization", "Bearer ")
        .when()
        .get("/api/localhost-admin/status")
        .then()
        .statusCode(401)
        .body("error", equalTo("missing_token"));
  }
}
