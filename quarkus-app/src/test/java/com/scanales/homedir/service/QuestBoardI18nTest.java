package com.scanales.homedir.service;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class QuestBoardI18nTest {

  @Test
  void englishLocaleShowsEnglishQuestContent() {
    given()
        .header("Accept-Language", "en")
        .when()
        .get("/quests")
        .then()
        .statusCode(200)
        .body(containsString("Quest Board"))
        .body(containsString("List Your Shelter"))
        .body(containsString("First Contact"))
        .body(containsString("Report an Anomaly"))
        .body(containsString("Code Contribution"));
  }

  @Test
  void spanishLocaleShowsSpanishQuestContent() {
    given()
        .header("Accept-Language", "es")
        .when()
        .get("/quests")
        .then()
        .statusCode(200)
        .body(containsString("Tablero de misiones"))
        .body(containsString("Enlista tu Refugio"))
        .body(containsString("Primer Contacto"))
        .body(containsString("Reporta una Anomalía"))
        .body(containsString("Contribución de Código"));
  }
}
