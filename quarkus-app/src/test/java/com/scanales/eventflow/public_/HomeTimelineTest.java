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
        .body(containsString("Contributor Hub"))
        .body(containsString("New / Hot"))
        .body(containsString("LTA quick preview"))
        .body(containsString("Community"))
        .body(containsString("Events"))
        .body(containsString("Project"));
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
        .body(containsString("HomeDir unifica contenido curado, eventos y actividad del proyecto en un solo hub."))
        .body(containsString("Vista rápida de LTA"))
        .body(containsString("Community"))
        .body(containsString("Events"));
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

  @Test
  public void eventsDirectoryRespectsAcceptLanguage() {
    given()
        .header("Accept-Language", "es")
        .accept("text/html")
        .when()
        .get("/eventos")
        .then()
        .statusCode(200)
        .body(containsString("<html lang=\"es\">"))
        .body(containsString(">Inicio</a>"));
  }
}
