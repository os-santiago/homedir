package com.scanales.eventflow.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.service.EventService;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
public class HomeTimelineTest {

    @Inject
    EventService eventService;

    @BeforeEach
    public void setup() {
        Event first = new Event("home1", "Evento Cercano", "desc");
        first.setDate(LocalDate.now().plusDays(3));
        Event second = new Event("home2", "Evento Lejano", "desc");
        second.setDate(LocalDate.now().plusDays(10));
        eventService.saveEvent(first);
        eventService.saveEvent(second);
    }

    @AfterEach
    public void cleanup() {
        eventService.listEvents().forEach(e -> eventService.deleteEvent(e.getId()));
    }

    @Test
    public void homeShowsEventsOrderedWithCountdown() {
        String html = given()
                .when().get("/")
                .then()
                .statusCode(200)
                .body(containsString("Faltan 3 días"))
                .body(containsString("Faltan 10 días"))
                .extract().asString();

        Assertions.assertTrue(html.indexOf("Evento Cercano") < html.indexOf("Evento Lejano"),
                "El evento más próximo debe aparecer primero");
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        Assertions.assertTrue(html.contains("Hoy: " + today));
    }
}

