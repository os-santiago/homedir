package com.scanales.homedir.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(AboutResourceProdExposureTest.ProdProfile.class)
public class AboutResourceProdExposureTest {

  public static class ProdProfile implements QuarkusTestProfile {
    @Override
    public String getConfigProfile() {
      return "prod";
    }
  }

  @Test
  void prodHidesCommitHashAndAuthConfig() {
    given()
        .when()
        .get("/about")
        .then()
        .statusCode(200)
        .body(containsString("Versi"))
        .body(not(containsString("Hash de commit desplegado")))
        .body(not(containsString("Autenticaci")));
  }
}
