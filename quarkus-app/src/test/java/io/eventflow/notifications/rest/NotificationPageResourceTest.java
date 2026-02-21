package io.eventflow.notifications.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.scanales.eventflow.model.QuestClass;
import com.scanales.eventflow.service.UserProfileService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/** Tests for the public notifications center page. */
@QuarkusTest
public class NotificationPageResourceTest {
  @Inject UserProfileService userProfiles;

  @Test
  public void centerRendersForVisitor() {
    given()
        .when()
        .get("/notifications/center")
        .then()
        .statusCode(200)
        .body(containsString("Notifications Center"))
        .body(containsString("global alerts"));
  }

  @Test
  @TestSecurity(user = "user@example.com", roles = {"user"})
  public void centerAwardsWarriorXpForAuthenticatedUser() {
    given().when().get("/notifications/center").then().statusCode(200);
    given().when().get("/notifications/center").then().statusCode(200);

    var profile = userProfiles.find("user@example.com").orElseThrow();
    assertEquals(4, profile.getClassXp(QuestClass.WARRIOR));
  }
}
