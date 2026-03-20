package com.scanales.homedir.private_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scanales.homedir.TestDataDir;
import com.scanales.homedir.campaigns.CampaignService;
import jakarta.ws.rs.core.MediaType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(TestDataDir.class)
class AdminCampaignsPageTest {

  @Inject CampaignService campaignService;

  @BeforeEach
  void resetCampaignState() {
    campaignService.resetStateForTests();
    campaignService.refreshDrafts();
  }

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
        .body(containsString("id=\"campaignsFilterPanel\""))
        .body(containsString("id=\"campaignsBulkPanel\""))
        .body(containsString("id=\"campaignsBulkActionForm\""))
        .body(containsString("id=\"campaignsBulkApplyBtn\""))
        .body(containsString("id=\"campaignsPublishNowBtn\""))
        .body(containsString("id=\"campaignsPauseRefreshBtn\""))
        .body(containsString("id=\"campaignsPausePublishBtn\""))
        .body(containsString("id=\"campaignsDisableChannelBtn-discord\""))
        .body(containsString("id=\"campaignsSummaryPanel\""))
        .body(containsString("id=\"campaignsQueueHealthPanel\""))
        .body(containsString("id=\"campaignsBusinessPanel\""))
        .body(containsString("id=\"campaignsRolloutPanel\""))
        .body(containsString("id=\"campaignsRolloutAckBtn-discord\""))
        .body(containsString("id=\"campaignsPilotRunbookPanel\""))
        .body(containsString("id=\"campaignsPilotVerificationPanel\""))
        .body(containsString("id=\"campaignsRecoveryPanel\""))
        .body(containsString("id=\"campaignsQueueRiskPanel\""))
        .body(containsString("id=\"campaignsCadencePanel\""))
        .body(containsString("id=\"campaignsAuditTrailPanel\""))
        .body(containsString("id=\"campaignsPreviewPackPanel\""))
        .body(containsString("id=\"campaignsAttributionPanel\""))
        .body(containsString("id=\"campaignsLinkedinPanel\""))
        .body(containsString("campaigns-admin-grid"))
        .body(containsString("campaigns-admin-list"))
        .body(containsString("Bluesky"))
        .body(containsString("Mastodon"))
        .body(containsString("LinkedIn"))
        .body(containsString("utm_source=campaigns"))
        .body(containsString("/private/admin/campaigns/"));
  }

  @Test
  @TestSecurity(user = "sergio.canales.e@gmail.com")
  void adminCanApproveDraftsInBulk() {
    List<String> draftIds =
        campaignService.preview("es").drafts().stream()
            .filter(item -> "draft".equals(item.workflowStateCode()))
            .limit(2)
            .map(CampaignService.CampaignPreviewCard::id)
            .toList();
    assertTrue(!draftIds.isEmpty());

    var response =
        given()
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .formParam("action", "approve")
        .formParam("draftIds", draftIds.toArray())
        .when()
        .post("/private/admin/campaigns/bulk-action");

    var assertions =
        response.then()
            .statusCode(200)
            .body(containsString("Se aplicó la acción por lote"))
            .body(containsString("<code>" + draftIds.size() + "</code>"));
    for (String draftId : draftIds) {
      assertions.body(containsString(draftId));
    }
  }

  @Test
  @TestSecurity(user = "sergio.canales.e@gmail.com")
  void adminShowsBulkSelectionErrorWhenNoDraftIsSelected() {
    given()
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .formParam("action", "approve")
        .when()
        .post("/private/admin/campaigns/bulk-action")
        .then()
        .statusCode(200)
        .body(containsString("Selecciona al menos un borrador"));
  }

  @Test
  @TestSecurity(user = "sergio.canales.e@gmail.com")
  void adminCanToggleCampaignAutomationControls() {
    given()
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .formParam("enabled", "false")
        .when()
        .post("/private/admin/campaigns/automation/refresh")
        .then()
        .statusCode(200)
        .body(containsString("campaignsResumeRefreshBtn"));

    given()
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .formParam("enabled", "false")
        .when()
        .post("/private/admin/campaigns/automation/publish")
        .then()
        .statusCode(200)
        .body(containsString("campaignsResumePublishBtn"));

    given()
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .formParam("enabled", "false")
        .when()
        .post("/private/admin/campaigns/automation/channel/discord")
        .then()
        .statusCode(200)
        .body(containsString("campaignsEnableChannelBtn-discord"));
  }

  @Test
  @TestSecurity(user = "sergio.canales.e@gmail.com")
  void adminCanAcknowledgeChannelGoLiveReadiness() {
    given()
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .formParam("acknowledged", "true")
        .when()
        .post("/private/admin/campaigns/rollout/channel/discord/ack")
        .then()
        .statusCode(200)
        .body(containsString("campaignsRolloutClearBtn-discord"))
        .body(containsString("discord"))
        .body(containsString("Confirmado"));
  }

  @Test
  @TestSecurity(user = "sergio.canales.e@gmail.com")
  void adminCanSelectPilotLiveChannel() {
    given()
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .when()
        .post("/private/admin/campaigns/rollout/pilot/discord/select")
        .then()
        .statusCode(200)
        .body(containsString("campaignsPilotClearBtn"))
        .body(containsString("Discord"))
        .body(containsString("canal piloto"));
  }

  @Test
  @TestSecurity(user = "sergio.canales.e@gmail.com")
  void adminCanArmPilotLiveChannel() {
    given()
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .when()
        .post("/private/admin/campaigns/rollout/pilot/discord/select")
        .then()
        .statusCode(200);

    given()
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .when()
        .post("/private/admin/campaigns/rollout/pilot/arm")
        .then()
        .statusCode(200)
        .body(containsString("campaignsPilotDisarmBtn"))
        .body(containsString("Discord"))
        .body(containsString("Armado"));
  }

  @Test
  @TestSecurity(user = "sergio.canales.e@gmail.com")
  void adminShowsPilotVerificationControls() {
    campaignService.setPilotLiveChannel("discord", "sergio.canales.e@gmail.com");
    campaignService.setPilotLiveArmed(true, "sergio.canales.e@gmail.com");
    campaignService.setPilotVerificationAcknowledged(true, "sergio.canales.e@gmail.com");

    given()
        .when()
        .get("/private/admin/campaigns")
        .then()
        .statusCode(200)
        .body(containsString("id=\"campaignsPilotVerificationPanel\""))
        .body(containsString("campaignsPilotVerificationClearBtn"))
        .body(containsString("Verificado"));
  }

  @Test
  @TestSecurity(user = "sergio.canales.e@gmail.com")
  void adminCanFilterCampaignsPage() {
    given()
        .queryParam("q", "HomeDir")
        .queryParam("kind", "product_pulse")
        .queryParam("workflow", "draft")
        .queryParam("channel", "linkedin")
        .when()
        .get("/private/admin/campaigns")
        .then()
        .statusCode(200)
        .body(containsString("id=\"campaignsFilterPanel\""))
        .body(containsString("value=\"HomeDir\""))
        .body(containsString("option value=\"product_pulse\" selected"))
        .body(containsString("option value=\"draft\" selected"))
        .body(containsString("option value=\"linkedin\" selected"))
        .body(containsString("HomeDir"));
  }

  @Test
  @TestSecurity(user = "sergio.canales.e@gmail.com")
  void adminShowsNotReadyMessageWhenScheduleIsBlocked() {
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
        .body(containsString(draftId))
        .body(containsString("todavía no está listo"));
  }

  @Test
  @TestSecurity(user = "sergio.canales.e@gmail.com")
  void adminShowsRetryBlockedMessageWhenChannelRetryIsNotReady() {
    String draftId = campaignService.preview("es").drafts().getFirst().id();

    given()
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .formParam("returnTo", "detail")
        .when()
        .post("/private/admin/campaigns/" + draftId + "/retry-channel/discord")
        .then()
        .statusCode(200)
        .body(containsString(draftId))
        .body(containsString("todavía no se puede reintentar"));
  }

  @Test
  @TestSecurity(user = "sergio.canales.e@gmail.com")
  void adminActionsPreserveActiveFilters() {
    String draftId = campaignService.preview("es").drafts().getFirst().id();

    given()
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .formParam("q", "HomeDir")
        .formParam("kind", "product_pulse")
        .formParam("workflow", "draft")
        .formParam("channel", "linkedin")
        .when()
        .post("/private/admin/campaigns/" + draftId + "/approve")
        .then()
        .statusCode(200)
        .body(containsString("value=\"HomeDir\""))
        .body(containsString("option value=\"product_pulse\" selected"))
        .body(containsString("option value=\"draft\" selected"))
        .body(containsString("option value=\"linkedin\" selected"));
  }

  @Test
  @TestSecurity(user = "sergio.canales.e@gmail.com")
  void adminCanOpenCampaignDetailPageAndKeepFiltersInBackLink() {
    String draftId = campaignService.preview("es").drafts().getFirst().id();

    given()
        .queryParam("q", "HomeDir")
        .queryParam("kind", "product_pulse")
        .queryParam("workflow", "draft")
        .queryParam("channel", "linkedin")
        .when()
        .get("/private/admin/campaigns/" + draftId)
        .then()
        .statusCode(200)
        .body(containsString("id=\"campaignDetailOverviewPanel\""))
        .body(containsString("id=\"campaignDetailPreviewPanel\""))
        .body(containsString("id=\"campaignDetailRecoveryPanel\""))
        .body(containsString("id=\"campaignDetailAuditPanel\""))
        .body(containsString("id=\"campaignDetailRiskPanel\""))
        .body(
            containsString(
                "/private/admin/campaigns?q=HomeDir&amp;workflow=draft&amp;kind=product_pulse&amp;channel=linkedin"));
  }

  @Test
  @TestSecurity(user = "sergio.canales.e@gmail.com")
  void detailActionsPreserveFiltersAndStayOnDetail() {
    String draftId = campaignService.preview("es").drafts().getFirst().id();

    given()
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .formParam("q", "HomeDir")
        .formParam("kind", "product_pulse")
        .formParam("workflow", "draft")
        .formParam("channel", "linkedin")
        .formParam("returnTo", "detail")
        .when()
        .post("/private/admin/campaigns/" + draftId + "/approve")
        .then()
        .statusCode(200)
        .body(containsString("id=\"campaignDetailOverviewPanel\""))
        .body(containsString("value=\"HomeDir\""))
        .body(containsString("name=\"returnTo\" value=\"detail\""))
        .body(containsString(draftId));
  }

  @Test
  @TestSecurity(user = "sergio.canales.e@gmail.com")
  void adminCanConfirmLinkedinHandoff() {
    String draftId = campaignService.preview("es").drafts().getFirst().id();
    campaignService.approveDraft(draftId, "sergio.canales.e@gmail.com");

    given()
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .formParam("q", "HomeDir")
        .formParam("kind", "product_pulse")
        .formParam("workflow", "draft")
        .formParam("channel", "linkedin")
        .when()
        .post("/private/admin/campaigns/" + draftId + "/mark-linkedin")
        .then()
        .statusCode(200)
        .body(containsString(draftId))
        .body(containsString("LinkedIn"));
  }
}
