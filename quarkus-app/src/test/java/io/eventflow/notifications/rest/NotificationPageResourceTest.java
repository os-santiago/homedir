package io.eventflow.notifications.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/** Tests for the public notifications center page. */
@QuarkusTest
public class NotificationPageResourceTest {
  @Test
  public void centerRendersForVisitor() {
    given()
      .when().get("/notifications/center")
      .then()
      .statusCode(200)
      .body(containsString("Notificaciones"))
      .body(containsString("actividades del día en curso"));
  }
}
