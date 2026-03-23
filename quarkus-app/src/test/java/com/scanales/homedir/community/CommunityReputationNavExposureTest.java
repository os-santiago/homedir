package com.scanales.homedir.community;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

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
@TestProfile(CommunityReputationNavExposureTest.Profile.class)
class CommunityReputationNavExposureTest {

  public static class Profile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "metrics.buffer.max-size", "200",
          "reputation.engine.enabled", "true",
          "reputation.hub.ui.enabled", "true",
          "reputation.hub.nav.public.enabled", "true");
    }
  }

  private static RequestSpecification englishRequest() {
    return given().header("Accept-Language", "en");
  }

  @Test
  void communityPageShowsReputationHubLinkWhenPublicNavFlagIsEnabled() {
    englishRequest()
        .when()
        .get("/comunidad")
        .then()
        .statusCode(200)
        .body(containsString("href=\"/comunidad/reputation-hub\""))
        .body(containsString("href=\"/comunidad/board\""));
  }

  @Test
  void communityBoardShowsReputationHubLinkWhenPublicNavFlagIsEnabled() {
    englishRequest()
        .when()
        .get("/comunidad/board")
        .then()
        .statusCode(200)
        .body(containsString("href=\"/comunidad/reputation-hub\""))
        .body(containsString("href=\"/comunidad/board\""));
  }
}
