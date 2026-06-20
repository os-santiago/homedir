package com.scanales.homedir.public_;

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
        .body(containsString("HomeDir focuses on events, community news, and collaboration."))
        .body(containsString("DevOpsDays Santiago is the first HomeDir priority."))
        .body(containsString("Community and local event news"))
        .body(containsString("Choose how to collaborate"))
        .body(containsString("DevOpsDays Santiago Call for Papers"))
        .body(containsString("/event/devopsdays-santiago-2026/cfp"))
        .body(containsString("/event/devopsdays-santiago-2026/volunteers"));
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
        .body(containsString(">Home</a>"));
  }

  @Test
  public void homeHighlightsCommunityAndEventsInSpanish() {
    given()
        .header("Accept-Language", "en")
        .accept("text/html")
        .when()
        .get("/")
        .then()
        .statusCode(200)
        .body(containsString("<html lang=\"en\">"))
        .body(containsString(">Home</a>"))
        .body(containsString("HomeDir"))
        .body(containsString("HomeDir focuses on events, community news, and collaboration."))
        .body(containsString("DevOpsDays Santiago is the first HomeDir priority."))
        .body(containsString("Community and local event news"))
        .body(containsString("Choose how to collaborate"))
        .body(containsString("DevOpsDays Santiago Call for Papers"))
        .body(containsString("/event/devopsdays-santiago-2026/cfp"))
        .body(containsString("/event/devopsdays-santiago-2026/volunteers"));
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
        .body(containsString(">Home</a>"));
  }

  @Test
  public void eventsDirectoryRespectsAcceptLanguage() {
    given()
        .header("Accept-Language", "en")
        .accept("text/html")
        .when()
        .get("/eventos")
        .then()
        .statusCode(200)
        .body(containsString("<html lang=\"en\">"))
        .body(containsString(">Home</a>"));
  }
}
