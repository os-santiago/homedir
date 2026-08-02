package com.scanales.homedir.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(AboutResourceDevExposureTest.NonProdProfile.class)
public class AboutResourceDevExposureTest {

  public static class NonProdProfile implements QuarkusTestProfile {
    @Override
    public String getConfigProfile() {
      return "test";
    }
  }

  @Test
  void nonProdShowsCommitHashButHidesAuthConfig() {
    given()
        .when()
        .get("/about")
        .then()
        .statusCode(200)
        .body(containsString("Hash de commit desplegado"))
        .body(not(containsString("Autenticaci")));
  }
}
