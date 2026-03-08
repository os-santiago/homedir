package com.scanales.eventflow.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.volunteers.VolunteerApplication;
import com.scanales.eventflow.volunteers.VolunteerApplicationService;
import com.scanales.eventflow.volunteers.VolunteerApplicationStatus;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class EventVolunteersPageTest {

  private static final String EVENT_ID = "volunteers-page-event";

  @Inject EventService eventService;
  @Inject VolunteerApplicationService volunteerApplicationService;

  @AfterEach
  public void cleanup() {
    eventService.deleteEvent(EVENT_ID);
    volunteerApplicationService.clearAllForTests();
  }

  @Test
  public void volunteersPageUsesSpanishWhenLocaleCookieIsSpanish() {
    Event event = new Event(EVENT_ID, "Volunteer Event", "desc");
    eventService.saveEvent(event);

    given()
        .header("Accept-Language", "en")
        .cookie("QP_LOCALE", "es")
        .when()
        .get("/event/" + EVENT_ID + "/volunteers")
        .then()
        .statusCode(200)
        .body(containsString("Programa de voluntariado"))
        .body(not(containsString("Volunteer Program")));
  }

  @Test
  @TestSecurity(user = "member@example.com")
  public void selectedVolunteerSeesLoungeCtaByDefault() {
    Event event = new Event(EVENT_ID, "Volunteer Event", "desc");
    eventService.saveEvent(event);
    VolunteerApplication created =
        volunteerApplicationService.create(
            "member@example.com",
            "Member",
            new VolunteerApplicationService.CreateRequest(EVENT_ID, "About", "Reason", "Diff"));
    volunteerApplicationService.updateStatus(
        created.id(),
        VolunteerApplicationStatus.SELECTED,
        "admin@example.org",
        "selected",
        created.updatedAt());

    given()
        .header("Accept-Language", "en")
        .when()
        .get("/event/" + EVENT_ID + "/volunteers")
        .then()
        .statusCode(200)
        .body(containsString("id=\"volunteerApplyContent\" hidden"))
        .body(containsString("id=\"volunteerSelectedCta\" class=\"volunteer-status-card\""))
        .body(not(containsString("id=\"volunteerSelectedCta\" class=\"volunteer-status-card\" hidden")));
  }
}

