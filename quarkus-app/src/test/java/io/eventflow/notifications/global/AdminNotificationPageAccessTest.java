package io.eventflow.notifications.global;

import static io.restassured.RestAssured.given;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class AdminNotificationPageAccessTest {

  @Test
  @TestSecurity(user = "admin", roles = "admin")
  public void adminCanAccess() {
    given().when().get("/admin/notifications").then().statusCode(200);
  }

  @Test
  @TestSecurity(user = "alice")
  public void nonAdminGets403() {
    given().when().get("/admin/notifications").then().statusCode(403);
  }
}
