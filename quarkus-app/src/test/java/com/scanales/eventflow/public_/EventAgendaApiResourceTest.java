package com.scanales.eventflow.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import com.scanales.eventflow.agenda.AgendaProposalConfigService;
import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.service.EventService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class EventAgendaApiResourceTest {

  private static final String EVENT_ID = "agenda-config-event";

  @Inject EventService eventService;
  @Inject AgendaProposalConfigService agendaProposalConfigService;

  @BeforeEach
  void setup() {
    eventService.deleteEvent(EVENT_ID);
    eventService.saveEvent(new Event(EVENT_ID, "Agenda config event", "desc"));
    agendaProposalConfigService.resetForTests();
  }

  @Test
  void configEndpointIsPublicAndReturnsCurrentValue() {
    given()
        .accept("application/json")
        .when()
        .get("/api/events/" + EVENT_ID + "/agenda/config")
        .then()
        .statusCode(200)
        .body("proposal_notice_enabled", equalTo(true))
        .body("admin", equalTo(false));
  }

  @Test
  @TestSecurity(user = "member@example.com")
  void nonAdminCannotUpdateAgendaConfig() {
    given()
        .contentType("application/json")
        .body("{\"proposal_notice_enabled\":false}")
        .when()
        .put("/api/events/" + EVENT_ID + "/agenda/config")
        .then()
        .statusCode(403)
        .body("error", equalTo("admin_required"));
  }

  @Test
  @TestSecurity(user = "admin@example.org")
  void adminCanUpdateAgendaConfig() {
    given()
        .contentType("application/json")
        .body("{\"proposal_notice_enabled\":false}")
        .when()
        .put("/api/events/" + EVENT_ID + "/agenda/config")
        .then()
        .statusCode(200)
        .body("proposal_notice_enabled", equalTo(false))
        .body("admin", equalTo(true));

    given()
        .accept("application/json")
        .when()
        .get("/api/events/" + EVENT_ID + "/agenda/config")
        .then()
        .statusCode(200)
        .body("proposal_notice_enabled", equalTo(false));
  }

  @Test
  void missingEventReturnsNotFound() {
    given()
        .accept("application/json")
        .when()
        .get("/api/events/missing-event-for-agenda/agenda/config")
        .then()
        .statusCode(404)
        .body("error", equalTo("event_not_found"));
  }
}
