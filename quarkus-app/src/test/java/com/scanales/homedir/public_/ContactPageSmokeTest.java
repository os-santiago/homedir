package com.scanales.homedir.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class ContactPageSmokeTest {

  @Test
  void contactPageRespondsWithDiscordCta() {
    given()
        .when()
        .get("/contacto")
        .then()
        .statusCode(200)
        .body(
            allOf(
                anyOf(containsString("Open Discord"), containsString("Abrir Discord")),
                containsString("https://discord.gg/3eawzc9ybc")));
  }

  @Test
  void contactPageRespondsWithGithubCta() {
    given()
        .when()
        .get("/contacto")
        .then()
        .statusCode(200)
        .body(
            allOf(
                anyOf(containsString("Open an issue"), containsString("Abrir un issue")),
                containsString("https://github.com/os-santiago/homedir/issues")));
  }
}
