package com.scanales.homedir.sdlc;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SdlcApiResourceTest {

  @Test
  @TestSecurity(user = "operator@example.test", roles = "admin")
  void authenticatedAdminCanReadSnapshotAndCompatibilityEndpoints() {
    given().when().get("/api/sdlc/snapshot").then().statusCode(200).body("stale", not((Object) null));
    given().when().get("/api/sdlc/status").then().statusCode(200).body("worker", not((Object) null));
    given().when().get("/api/sdlc/pipeline").then().statusCode(200);
    given().when().get("/api/sdlc/issues").then().statusCode(200);
    given().when().get("/api/sdlc/prs").then().statusCode(200);
    given().when().get("/api/sdlc/metrics?days=30").then().statusCode(200);
    given().when().get("/api/sdlc/anomalies").then().statusCode(200);
    given().when().get("/api/sdlc/configuration").then().statusCode(200);
  }

  @Test
  @TestSecurity(user = "operator@example.test", roles = "admin")
  void dashboardReferencesVersionedAssetInsteadOfImmutableLegacyUrl() {
    given()
        .when().get("/sdlc/dashboard")
        .then().statusCode(200).header("Cache-Control", containsString("no-store"))
        .body(containsString("/sdlc/dashboard/dashboard-v2.js"))
        .body(not(containsString("/sdlc/dashboard/dashboard.js\"")));
  }

  @Test
  void anonymousRequestsRemainProtected() {
    given().redirects().follow(false).when().get("/api/sdlc/snapshot").then().statusCode(401);
  }
}
