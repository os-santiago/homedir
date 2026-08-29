package com.scanales.homedir.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class DocsPageSmokeTest {

  @Test
  void docsPageDoesNotExposeTodoPlaceholders() {
    given().when().get("/docs").then().statusCode(200).body(not(containsString("@-- TODO")));
  }

  @Test
  void docsPageShowsOfficialLinks() {
    given()
        .when()
        .get("/docs")
        .then()
        .statusCode(200)
        .body(anyOf(containsString("GitHub repository"), containsString("Repositorio en GitHub")))
        .body(containsString("https://github.com/os-santiago/homedir"));
  }
}
