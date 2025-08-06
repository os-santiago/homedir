package com.scanales.eventflow.private_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.Scenario;
import com.scanales.eventflow.model.Talk;
import com.scanales.eventflow.service.EventService;

import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class UserScheduleTest {

    @Inject
    EventService eventService;

    @BeforeEach
    public void setup() {
        Event e = new Event("evt", "Test Event", "desc", 1);
        Scenario sc = new Scenario("s1", "Main");
        e.getScenarios().add(sc);
        Talk t = new Talk("t1", "Talk 1");
        t.setLocation("s1");
        t.setDay(1);
        t.setDurationMinutes(30);
        t.setStartTime(LocalTime.of(10, 0));
        e.getAgenda().add(t);
        eventService.saveEvent(e);
    }

    @Test
    public void addRequiresAuth() {
        given()
                .when().get("/private/profile/add/t1")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "user@example.com")
    public void addAndRemoveTalk() {
        given()
                .redirects().follow(false)
                .when().get("/private/profile/add/t1")
                .then()
                .statusCode(303);

        given()
                .when().get("/private/profile")
                .then()
                .statusCode(200)
                .body(containsString("Talk 1"));

        given()
                .redirects().follow(false)
                .when().get("/private/profile/remove/t1")
                .then()
                .statusCode(303);

        given()
                .when().get("/private/profile")
                .then()
                .statusCode(200)
                .body(not(containsString("Talk 1")));
    }

    @Test
    @TestSecurity(user = "user@example.com")
    public void updateTalkDetails() {
        given()
                .redirects().follow(false)
                .when().get("/private/profile/add/t1")
                .then()
                .statusCode(303);

        given()
                .contentType("application/json")
                .body("{\"attended\":true,\"rating\":5,\"motivations\":[\"⭐ Relevante para mi trabajo\"]}")
                .when().post("/private/profile/update/t1")
                .then()
                .statusCode(200)
                .body(containsString("updated"));

        given()
                .when().get("/private/profile")
                .then()
                .statusCode(200)
                .body(containsString("data-attended=\"true\""))
                .body(containsString("data-rated=\"true\""))
                .body(containsString("Relevante para mi trabajo"));
    }
}
