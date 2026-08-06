package com.scanales.homedir.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class HomePastEventsTest {

  @Test
  public void homeShowsCommunityCards() {
    given()
        .header("Accept-Language", "en")
        .accept("text/html")
        .when()
        .get("/")
        .then()
        .statusCode(200)
        .body(containsString("<html lang=\"en\">"))
        .body(containsString("Latest from the Community"))
        .body(containsString("HomeDir: Built by Devs, for Devs"))
        .body(containsString("DevOpsDays Santiago 2026"))
        .body(containsString("Call for Volunteers"));
  }
}
