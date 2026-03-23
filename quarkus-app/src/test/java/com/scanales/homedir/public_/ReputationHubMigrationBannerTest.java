package com.scanales.homedir.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import com.scanales.homedir.TestDataDir;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(TestDataDir.class)
@TestProfile(ReputationHubMigrationBannerTest.Profile.class)
class ReputationHubMigrationBannerTest {

  public static class Profile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "metrics.buffer.max-size", "200",
          "reputation.engine.enabled", "true",
          "reputation.hub.ui.enabled", "true",
          "reputation.hub.nav.public.enabled", "true",
          "reputation.hub.primary.enabled", "true");
    }
  }

  @Test
  void migrationBannerAppearsOnHubAndHowPagesInEnglish() {
    given()
        .header("Accept-Language", "en")
        .when()
        .get("/comunidad/reputation-hub")
        .then()
        .statusCode(200)
        .body(containsString("Community update"))
        .body(containsString("Community Board now lives in Reputation Hub"));

    given()
        .header("Accept-Language", "en")
        .when()
        .get("/comunidad/reputation-hub/how")
        .then()
        .statusCode(200)
        .body(containsString("Community update"))
        .body(containsString("Community Board now lives in Reputation Hub"));
  }

  @Test
  void migrationBannerAppearsOnHubAndHowPagesInSpanish() {
    given()
        .header("Accept-Language", "es")
        .when()
        .get("/comunidad/reputation-hub")
        .then()
        .statusCode(200)
        .body(containsString("Actualización de comunidad"))
        .body(containsString("Community Board ahora vive en Reputation Hub"));

    given()
        .header("Accept-Language", "es")
        .when()
        .get("/comunidad/reputation-hub/how")
        .then()
        .statusCode(200)
        .body(containsString("Actualización de comunidad"))
        .body(containsString("Community Board ahora vive en Reputation Hub"));
  }
}
