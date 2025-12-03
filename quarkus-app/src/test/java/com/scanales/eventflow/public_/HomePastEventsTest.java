package com.scanales.eventflow.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class HomePastEventsTest {

  @Test
  public void homeShowsCommunityCards() {
    given()
        .accept("text/html")
        .when()
        .get("/")
        .then()
        .statusCode(200)
        .body(containsString("Comunidad"))
        .body(containsString("Eventos"))
        .body(containsString("Proyectos"));
  }
}
