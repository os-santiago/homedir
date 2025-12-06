package com.scanales.eventflow.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class HomeResourceRedirectTest {

  @Test
  public void eventsPathShowsEventsPage() {
    given()
        .when()
        .get("/events")
        .then()
        .statusCode(200)
        .body(containsString("Eventos y charlas"));
  }
}
