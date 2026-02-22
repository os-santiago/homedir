package com.scanales.eventflow.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class HomeTimelineTest {

  @Test
  public void homeHighlightsCommunityAndEvents() {
    given()
        .accept("text/html")
        .when()
        .get("/")
        .then()
        .statusCode(200)
        .body(containsString("HomeDir"))
        .body(containsString("Welcome"))
        .body(containsString("New / Hot"))
        .body(containsString("Highlights"))
        .body(containsString("LTA quick preview"))
        .body(containsString("Latest community content"))
        .body(containsString("Upcoming agenda"));
  }

  @Test
  public void homeHighlightsCommunityAndEventsInSpanish() {
    given()
        .header("Accept-Language", "es")
        .accept("text/html")
        .when()
        .get("/")
        .then()
        .statusCode(200)
        .body(containsString("<html lang=\"es\">"))
        .body(containsString(">Inicio</a>"))
        .body(containsString("HomeDir"))
        .body(containsString("Welcome"))
        .body(containsString("HomeDir: tu comunidad para construir, aprender y compartir."))
        .body(containsString("Vista rápida de LTA"))
        .body(containsString("Latest community content"));
  }

  @Test
  public void homeCookieLocaleOverridesAcceptLanguage() {
    given()
        .cookie("QP_LOCALE", "es")
        .header("Accept-Language", "en-US,en;q=0.8")
        .accept("text/html")
        .when()
        .get("/")
        .then()
        .statusCode(200)
        .body(containsString("<html lang=\"es\">"))
        .body(containsString(">Inicio</a>"));
  }
}
