package com.scanales.homedir.admin;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class AdminViewAccessTest {

  @Test
  @TestSecurity(user = "viewer@example.org")
  public void viewerCanOpenReadOnlyBackofficePages() {
    given()
        .when()
        .get("/private/admin")
        .then()
        .statusCode(200)
        .body(containsString("/private/admin/campaigns"));

    given()
        .when()
        .get("/private/admin/campaigns")
        .then()
        .statusCode(200)
        .body(containsString("id=\"campaignsProcessNav\""));

    given()
        .when()
        .get("/private/admin/events/new")
        .then()
        .statusCode(200);

    given()
        .when()
        .get("/private/admin/speakers")
        .then()
        .statusCode(200);

    given()
        .when()
        .get("/private/admin/backup")
        .then()
        .statusCode(200);
  }

  @Test
  @TestSecurity(user = "viewer@example.org")
  public void viewerCanAccessReadOnlyAdminDataButNotSensitiveExports() {
    given()
        .accept(MediaType.APPLICATION_JSON)
        .when()
        .get("/api/private/admin/insights/status")
        .then()
        .statusCode(200);

    given()
        .accept(MediaType.APPLICATION_JSON)
        .when()
        .get("/api/private/admin/observability/dashboard")
        .then()
        .statusCode(200);

    given()
        .when()
        .get("/private/admin/metrics")
        .then()
        .statusCode(200);

    given()
        .when()
        .get("/private/admin/backup/download")
        .then()
        .statusCode(403);

    given()
        .when()
        .get("/private/admin/metrics/data")
        .then()
        .statusCode(403);
  }

  @Test
  @TestSecurity(user = "viewer@example.org")
  public void viewerCannotRunBackofficeMutations() {
    given()
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .formParam("action", "approve")
        .formParam("draftIds", "draft-1")
        .when()
        .post("/private/admin/campaigns/bulk-action")
        .then()
        .statusCode(403);

    given()
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "initiativeId", "viewer-blocked",
                "title", "Blocked",
                "definitionStartedAt", "2026-03-01T00:00:00Z",
                "metadata", Map.of()))
        .when()
        .post("/api/private/admin/insights/initiatives/start")
        .then()
        .statusCode(403);

    given()
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .formParam("name", "Viewer")
        .formParam("duration", "30")
        .when()
        .post("/private/admin/speakers/viewer/talk")
        .then()
        .statusCode(403);

    given()
        .when()
        .get("/private/admin/errors/resolve/test-error")
        .then()
        .statusCode(403);
  }

  @Test
  @TestSecurity(user = "viewer@example.org", roles = "admin-view")
  public void viewerRoleCanReadNotificationFeedButCannotBroadcast() {
    given()
        .when()
        .get("/admin/notifications")
        .then()
        .statusCode(200);

    given()
        .when()
        .get("/admin/api/notifications/latest?limit=10")
        .then()
        .statusCode(200);

    given()
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("type", "VIEW", "title", "Blocked", "message", "blocked"))
        .when()
        .post("/admin/api/notifications/broadcast")
        .then()
        .statusCode(403);
  }
}
