package com.scanales.homedir.community;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import com.scanales.homedir.TestDataDir;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.specification.RequestSpecification;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(TestDataDir.class)
@TestProfile(CommunityBoardPrimarySwitchTest.Profile.class)
class CommunityBoardPrimarySwitchTest {

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

  private static RequestSpecification englishRequest() {
    return given().header("Accept-Language", "en");
  }

  @Test
  void boardSummaryRedirectsToReputationHubWhenPrimarySwitchIsEnabled() {
    englishRequest()
        .redirects()
        .follow(false)
        .when()
        .get("/comunidad/board")
        .then()
        .statusCode(303)
        .header("Location", containsString("/comunidad/reputation-hub"));
  }

  @Test
  void boardDetailRedirectsToReputationHubWhenPrimarySwitchIsEnabled() {
    englishRequest()
        .redirects()
        .follow(false)
        .when()
        .get("/comunidad/board/discord-users")
        .then()
        .statusCode(303)
        .header("Location", containsString("/comunidad/reputation-hub"));
  }

  @Test
  void communityMenuHidesBoardLinkWhenPrimarySwitchIsEnabled() {
    englishRequest()
        .when()
        .get("/comunidad")
        .then()
        .statusCode(200)
        .body(containsString("href=\"/comunidad/reputation-hub\""))
        .body(not(containsString("href=\"/comunidad/board\"")));
  }

  @Test
  void reputationHubMenuHidesBoardLinkWhenPrimarySwitchIsEnabled() {
    englishRequest()
        .when()
        .get("/comunidad/reputation-hub")
        .then()
        .statusCode(200)
        .body(containsString("href=\"/comunidad/reputation-hub\""))
        .body(not(containsString("href=\"/comunidad/board\"")));
  }
}
