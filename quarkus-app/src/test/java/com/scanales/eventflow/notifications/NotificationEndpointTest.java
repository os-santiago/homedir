package com.scanales.eventflow.notifications;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(NotificationEndpointTest.Profile.class)
public class NotificationEndpointTest {

  public static class Profile implements io.quarkus.test.junit.QuarkusTestProfile {
    @Override
    public java.util.Map<String, String> getConfigOverrides() {
      return java.util.Map.of("notifications.sse.enabled", "true");
    }
  }

  @Test
  public void pollingRequiresAuth() {
    given().queryParam("since", 0).when().get("/api/notifications/next").then().statusCode(401);
  }

  @Test
  @TestSecurity(user = "alice")
  public void pollingNoStore() {
    given()
        .queryParam("since", 0)
        .when()
        .get("/api/notifications/next")
        .then()
        .statusCode(200)
        .header("Cache-Control", containsString("no-store"));
  }

  @Test
  public void streamRequiresAuth() {
    given().when().get("/api/notifications/stream").then().statusCode(401);
  }

  @Test
  @TestSecurity(user = "alice")
  public void streamNoStore() {
    var resp = given().accept("text/event-stream").when().get("/api/notifications/stream");
    if (resp.statusCode() == 200) {
      resp.then().header("Cache-Control", containsString("no-store"));
    } else {
      resp.then().statusCode(404);
    }
  }
}
