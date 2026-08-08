package com.scanales.homedir.service;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

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
        .body(not(containsString("Tablero de Misiones")))
        .body(not(containsString("Tablero de misiones")))
        // Quest 1
        .body(containsString("List Your Shelter"))
        .body(containsString("Fork the &#39;homedir&#39; repository or register your own configuration repository in the community directory."))
        .body(not(containsString("Enlista tu Refugio")))
        .body(not(containsString("Crea un fork del repositorio &#39;homedir&#39; o registra tu propio repositorio de configuración en el directorio de la comunidad.")))
        // Quest 2
        .body(containsString("First Contact"))
        .body(containsString("Join the community by linking your GitHub account in your HomeDir profile."))
        .body(not(containsString("Primer Contacto")))
        .body(not(containsString("Únete a la comunidad vinculando tu cuenta de GitHub en tu perfil de HomeDir.")))
        // Quest 3
        .body(containsString("Report an Anomaly"))
        .body(containsString("Find a bug or suggest an improvement by creating an Issue in the official repository."))
        .body(not(containsString("Reporta una Anomalía")))
        .body(not(containsString("Encuentra un bug o sugiere una mejora creando un Issue en el repositorio oficial.")))
        // Quest 4
        .body(containsString("Code Contribution"))
        .body(containsString("Submit a Pull Request (PR) to the repository. It can be documentation, a fix or a feature."))
        .body(not(containsString("Contribución de Código")))
        .body(not(containsString("Envía un Pull Request (PR) al repositorio. Puede ser documentación, fix o feature.")));
  }

  @Test
  void spanishLocaleShowsSpanishQuestContent() {
    given()
        .header("Accept-Language", "es")
        .when()
        .get("/quests")
        .then()
        .statusCode(200)
        .body(containsString("Tablero de Misiones"))
        .body(not(containsString("Quest Board")))
        // Quest 1
        .body(containsString("Enlista tu Refugio"))
        .body(containsString("Crea un fork del repositorio &#39;homedir&#39; o registra tu propio repositorio de configuración en el directorio de la comunidad."))
        .body(not(containsString("List Your Shelter")))
        .body(not(containsString("Fork the &#39;homedir&#39; repository or register your own configuration repository in the community directory.")))
        // Quest 2
        .body(containsString("Primer Contacto"))
        .body(containsString("Únete a la comunidad vinculando tu cuenta de GitHub en tu perfil de HomeDir."))
        .body(not(containsString("First Contact")))
        .body(not(containsString("Join the community by linking your GitHub account in your HomeDir profile.")))
        // Quest 3
        .body(containsString("Reporta una Anomalía"))
        .body(containsString("Encuentra un bug o sugiere una mejora creando un Issue en el repositorio oficial."))
        .body(not(containsString("Report an Anomaly")))
        .body(not(containsString("Find a bug or suggest an improvement by creating an Issue in the official repository.")))
        // Quest 4
        .body(containsString("Contribución de Código"))
        .body(containsString("Envía un Pull Request (PR) al repositorio. Puede ser documentación, fix o feature."))
        .body(not(containsString("Code Contribution")))
        .body(not(containsString("Submit a Pull Request (PR) to the repository. It can be documentation, a fix or a feature.")));
  }
}
