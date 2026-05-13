package com.scanales.homedir.admin;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import com.scanales.homedir.model.Event;
import com.scanales.homedir.service.EventService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.SecurityAttribute;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class AdminAccessTest {

  @Inject
  EventService eventService;

  @Test
  @TestSecurity(user = "alice")
  public void nonAdminCannotAccess() {
    given().when().get("/private/admin/events/new").then().statusCode(403);
  }

  @Test
  @TestSecurity(user = "sergio.canales.e@gmail.com")
  public void adminCanAccess() {
    given().when().get("/private/admin/events/new").then().statusCode(200);
  }

  @Test
  @TestSecurity(user = "alice")
  public void nonAdminCannotAccessInsights() {
    given().when().get("/private/admin/insights").then().statusCode(403);
  }

  @Test
  @TestSecurity(user = "sergio.canales.e@gmail.com")
  public void adminCanAccessInsights() {
    given()
        .when()
        .get("/private/admin/insights")
        .then()
        .statusCode(200)
        .body(containsString("id=\"insightsStatus\""))
        .body(containsString("id=\"insightsRefreshBtn\""))
        .body(containsString("id=\"insightsInitiativesSearch\""))
        .body(containsString("id=\"insightsInitiativesState\""))
        .body(containsString("id=\"insightsInitiativesSort\""))
        .body(containsString("id=\"insightsInitiativesWindow\""))
        .body(containsString("id=\"insightsInitiativesCount\""));
  }

  @Test
  @TestSecurity(
      user = "sergio.canales.e@gmail.com",
      attributes = {
        @SecurityAttribute(key = "email", value = "sergio.canales.e@gmail.com"),
        @SecurityAttribute(key = "name", value = "Sergio Canales")
      })
  public void adminHubShowsCampaignsAccess() {
    given()
        .when()
        .get("/private/admin")
        .then()
        .statusCode(200)
        .body(containsString("/private/admin/campaigns"))
        .body(containsString("campaign"));
  }

  @Test
  @TestSecurity(user = "sergio.canales.e@gmail.com")
  public void adminEventsListUsesNormalizedActionButtons() {
    String eventId = "admin-actions-ui-test";
    eventService.deleteEvent(eventId);
    eventService.saveEvent(new Event(eventId, "Admin Actions UI Test", "Admin events list UI test"));

    try {
      given()
          .when()
          .get("/private/admin/events")
          .then()
          .statusCode(200)
          .body(containsString("class=\"admin-event-actions\""))
          .body(containsString("btn btn-secondary admin-event-action"))
          .body(containsString("btn btn-ghost admin-event-action"))
          .body(containsString("btn btn-danger admin-event-action"))
          .body(containsString("/private/admin/metrics?event=admin-actions-ui-test&amp;range=all"));
    } finally {
      eventService.deleteEvent(eventId);
    }
  }

}
