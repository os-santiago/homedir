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
        .header("Accept-Language", "en")
        .accept("text/html")
        .when()
        .get("/")
        .then()
        .statusCode(200)
        .body(containsString("HomeDir"))
        .body(containsString("HomeDir: build, learn, and level up with your community."))
        .body(containsString("How HomeDir works"))
        .body(containsString("Turn login into visible impact"))
        .body(containsString("Community"))
        .body(containsString("Events"))
        .body(containsString("Project"));
  }

  @Test
  public void homeFallbackLocaleIsSpanishWhenHeaderIsUnsupported() {
    given()
        .header("Accept-Language", "fr-FR,fr;q=0.9")
        .accept("text/html")
        .when()
        .get("/")
        .then()
        .statusCode(200)
        .body(containsString("<html lang=\"es\">"))
        .body(containsString(">Inicio</a>"));
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
        .body(containsString("HomeDir: construye, aprende y sube de nivel con tu comunidad."))
        .body(containsString("Cómo funciona HomeDir"))
        .body(containsString("Comunidad"))
        .body(containsString("Eventos"));
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
