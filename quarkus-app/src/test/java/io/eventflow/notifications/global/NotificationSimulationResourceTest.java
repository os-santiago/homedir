package io.eventflow.notifications.global;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.Talk;
import com.scanales.eventflow.service.EventService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class NotificationSimulationResourceTest {

  @Inject EventService events;
  @Inject GlobalNotificationService service;

  private Event sampleEvent() {
    Event e = new Event("e1", "Ev", "d");
    e.setDate(LocalDate.of(2023, 1, 1));
    e.setTimezone("UTC");
    Talk t1 = new Talk("t1", "Talk1");
    t1.setStartTime(LocalTime.of(10, 0));
    t1.setDurationMinutes(60);
    e.getAgenda().add(t1);
    return e;
  }

  @BeforeEach
  void setup() {
    events.reset();
    service.clearAll();
  }

  @Test
  @TestSecurity(user = "admin", roles = {"admin"})
  public void dryRunIncludesUpcoming() {
    Event e = sampleEvent();
    events.saveEvent(e);
    Instant pivot = e.getStartDateTime().minusMinutes(5).toInstant();
    Map<String, Object> req = Map.of("eventId", e.getId(), "pivot", pivot.toString());
    String json =
        given()
            .contentType(ContentType.JSON)
            .body(req)
            .when()
            .post("/admin/api/notifications/sim/dry-run")
            .then()
            .statusCode(200)
            .extract()
            .asString();
    assertTrue(json.contains("UPCOMING"));
  }

  @Test
  @TestSecurity(user = "admin", roles = {"admin"})
  public void executeTestBroadcastEnqueues() {
    Event e = sampleEvent();
    events.saveEvent(e);
    Instant pivot = e.getStartDateTime().minusMinutes(5).toInstant();
    Map<String, Object> req =
        new HashMap<>();
    req.put("eventId", e.getId());
    req.put("mode", "test-broadcast");
    req.put("pivot", pivot.toString());
    given()
        .contentType(ContentType.JSON)
        .body(req)
        .when()
        .post("/admin/api/notifications/sim/execute")
        .then()
        .statusCode(200)
        .body("test", equalTo(true));
    assertTrue(service.latest(10).stream().anyMatch(n -> n.test));
  }

  @Test
  @TestSecurity(user = "admin", roles = {"admin"})
  public void realBroadcastForbidden() {
    Event e = sampleEvent();
    events.saveEvent(e);
    Instant pivot = e.getStartDateTime().minusMinutes(5).toInstant();
    Map<String, Object> req =
        Map.of("eventId", e.getId(), "mode", "real-broadcast", "pivot", pivot.toString());
    given()
        .contentType(ContentType.JSON)
        .body(req)
        .when()
        .post("/admin/api/notifications/sim/execute")
        .then()
        .statusCode(403);
  }
}
