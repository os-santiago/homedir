package com.scanales.eventflow.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.endsWith;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class HomeResourceRedirectTest {

  @Test
  public void eventsPathRedirectsToHome() {
    given()
        .redirects()
        .follow(false)
        .when()
        .get("/events")
        .then()
        .statusCode(301)
        .header("Location", endsWith("/"));
  }
}
