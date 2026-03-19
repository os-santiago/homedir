package com.scanales.homedir.private_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scanales.homedir.TestDataDir;
import jakarta.ws.rs.core.MediaType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
        .body(containsString("id=\"campaignsPublishNowBtn\""))
        .body(containsString("id=\"campaignsSummaryPanel\""))
        .body(containsString("id=\"campaignsCadencePanel\""))
        .body(containsString("id=\"campaignsLinkedinPanel\""))
        .body(containsString("campaigns-admin-grid"))
        .body(containsString("campaigns-admin-list"))
        .body(containsString("Bluesky"))
        .body(containsString("Mastodon"))
        .body(containsString("/private/admin/campaigns/"));
  }

  @Test
  @TestSecurity(user = "sergio.canales.e@gmail.com")
  void adminCanApproveAndScheduleCampaignDraft() {
    String html =
        given()
            .when()
            .get("/private/admin/campaigns")
            .then()
            .statusCode(200)
            .extract()
            .asString();
    Matcher matcher = Pattern.compile("/private/admin/campaigns/([^/]+)/approve").matcher(html);
    assertTrue(matcher.find());
    String draftId = matcher.group(1);

    given()
        .when()
        .post("/private/admin/campaigns/" + draftId + "/approve")
        .then()
        .statusCode(200)
        .body(containsString(draftId));

    given()
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .formParam("scheduledFor", "2026-03-20T11:30")
        .when()
        .post("/private/admin/campaigns/" + draftId + "/schedule")
        .then()
        .statusCode(200)
        .body(containsString(draftId));

    given()
        .when()
        .get("/private/admin/campaigns")
        .then()
        .statusCode(200)
        .body(containsString("/private/admin/campaigns/" + draftId + "/unschedule"))
        .body(containsString(draftId));
  }

  @Test
  @TestSecurity(user = "sergio.canales.e@gmail.com")
  void adminCanConfirmLinkedinHandoff() {
    String html =
        given()
            .when()
            .get("/private/admin/campaigns")
            .then()
            .statusCode(200)
            .extract()
            .asString();
    Matcher matcher = Pattern.compile("/private/admin/campaigns/([^/]+)/approve").matcher(html);
    assertTrue(matcher.find());
    String draftId = matcher.group(1);

    given()
        .when()
        .post("/private/admin/campaigns/" + draftId + "/approve")
        .then()
        .statusCode(200);

    given()
        .when()
        .post("/private/admin/campaigns/" + draftId + "/mark-linkedin")
        .then()
        .statusCode(200)
        .body(containsString(draftId))
        .body(containsString("LinkedIn"));
  }
}
