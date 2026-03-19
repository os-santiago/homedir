package com.scanales.homedir.private_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import com.scanales.homedir.TestDataDir;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(TestDataDir.class)
class AdminCampaignsPageTest {

  @Test
  @TestSecurity(user = "alice")
  void nonAdminCannotAccessCampaignsPage() {
    given().when().get("/private/admin/campaigns").then().statusCode(403);
  }

  @Test
  @TestSecurity(user = "sergio.canales.e@gmail.com")
  void adminCanOpenCampaignsPage() {
    given()
        .when()
        .get("/private/admin/campaigns")
        .then()
        .statusCode(200)
        .body(containsString("id=\"campaignsRefreshBtn\""))
        .body(containsString("campaigns-admin-grid"))
        .body(containsString("campaigns-admin-list"));
  }
}
