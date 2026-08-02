package com.scanales.homedir.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class AboutResourceDevExposureTest {

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
