package com.scanales.homedir.config;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class LocaleSwitchingTest {

  @Test
  void spanishCookieResolvesSpanishAndSwitchesContent() {
    given()
        .cookie("QP_LOCALE", "es")
        .when()
        .get("/docs")
        .then()
        .statusCode(200)
        .body(containsString("<html lang=\"es\""))
        .body(containsString("<option value=\"es\" selected"))
        .body(containsString("Plataforma por OpenSourceSantiago"));
  }

  @Test
  void englishCookieResolvesEnglishAndSwitchesContentAwayFromSpanish() {
    given()
        .cookie("QP_LOCALE", "en")
        .when()
        .get("/docs")
        .then()
        .statusCode(200)
        .body(containsString("<html lang=\"en\""))
        .body(containsString("<option value=\"en\" selected"))
        .body(not(containsString("Plataforma por OpenSourceSantiago")));
  }

  @Test
  void spanishAcceptLanguageFallsBackToSpanish() {
    given()
        .header("Accept-Language", "es-ES,es;q=0.9")
        .when()
        .get("/docs")
        .then()
        .statusCode(200)
        .body(containsString("<html lang=\"es\""))
        .body(containsString("Plataforma por OpenSourceSantiago"));
  }
}
