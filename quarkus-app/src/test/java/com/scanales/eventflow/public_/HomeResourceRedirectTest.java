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
        .header("Accept-Language", "en")
        .when()
        .get("/events")
        .then()
        .statusCode(200)
        .body(containsString("Events and talks"));
  }

  @Test
  public void homeUsesBrowserLocaleWhenSupportedForAnonymousSessions() {
    given()
        .header("Accept-Language", "en-US,en;q=0.9")
        .when()
        .get("/")
        .then()
        .statusCode(200)
        .body(containsString("option value=\"en\" selected"));
  }

  @Test
  public void homeFallsBackToSpanishWhenBrowserLocaleUnsupported() {
    given()
        .header("Accept-Language", "fr-FR,fr;q=0.9")
        .when()
        .get("/")
        .then()
        .statusCode(200)
        .body(containsString("option value=\"es\" selected"));
  }

  @Test
  public void localeCookieOverridesBrowserPreference() {
    given()
        .cookie("QP_LOCALE", "en")
        .header("Accept-Language", "es-CL,es;q=0.9")
        .when()
        .get("/")
        .then()
        .statusCode(200)
        .body(containsString("option value=\"en\" selected"));
  }
}
