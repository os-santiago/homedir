package com.scanales.homedir.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class CssColorTokensTest {

  @Test
  void homedirCssServesSuccessfully() {
    given()
        .when()
        .get("/css/homedir.css")
        .then()
        .statusCode(200)
        .contentType("text/css");
  }

  @Test
  void retroThemeCssServesSuccessfully() {
    given()
        .when()
        .get("/css/retro-theme.css")
        .then()
        .statusCode(200)
        .contentType("text/css");
  }

  @Test
  void communityPageCssServesSuccessfully() {
    given()
        .when()
        .get("/css/community-page.css")
        .then()
        .statusCode(200)
        .contentType("text/css");
  }

  @Test
  void homedirCssDefinesRgbTokens() {
    given()
        .when()
        .get("/css/homedir.css")
        .then()
        .statusCode(200)
        .body(containsString("--black-rgb:"))
        .body(containsString("--white-rgb:"))
        .body(containsString("--gold-rgb:"))
        .body(containsString("--primary-rgb:"))
        .body(containsString("--mint-rgb:"));
  }

  @Test
  void homedirCssHasNoHardcodedRgbaBlack() {
    String body = given()
        .when()
        .get("/css/homedir.css")
        .then()
        .statusCode(200)
        .extract().asString();
    assertFalse(
        body.lines().anyMatch(l -> l.contains("rgba(0, 0, 0,") && !l.contains("var(")),
        "homedir.css should not contain hardcoded rgba(0,0,0,...) without var()");
  }

  @Test
  void homedirCssHasNoHardcodedRgbaWhite() {
    String body = given()
        .when()
        .get("/css/homedir.css")
        .then()
        .statusCode(200)
        .extract().asString();
    assertFalse(
        body.lines().anyMatch(l -> l.contains("rgba(255, 255, 255,") && !l.contains("var(")),
        "homedir.css should not contain hardcoded rgba(255,255,255,...) without var()");
  }

  @Test
  void retroThemeCssHasNoHardcodedRgbaBlack() {
    String body = given()
        .when()
        .get("/css/retro-theme.css")
        .then()
        .statusCode(200)
        .extract().asString();
    assertFalse(
        body.lines().anyMatch(l -> l.contains("rgba(0, 0, 0,") && !l.contains("var(")),
        "retro-theme.css should not contain hardcoded rgba(0,0,0,...) without var()");
  }

  @Test
  void retroThemeCssHasNoHardcodedRgbaWhite() {
    String body = given()
        .when()
        .get("/css/retro-theme.css")
        .then()
        .statusCode(200)
        .extract().asString();
    assertFalse(
        body.lines().anyMatch(l -> l.contains("rgba(255, 255, 255,") && !l.contains("var(")),
        "retro-theme.css should not contain hardcoded rgba(255,255,255,...) without var()");
  }

  @Test
  void communityPageCssHasNoHardcodedRgbaBlack() {
    String body = given()
        .when()
        .get("/css/community-page.css")
        .then()
        .statusCode(200)
        .extract().asString();
    assertFalse(
        body.lines().anyMatch(l -> l.contains("rgba(0, 0, 0,") && !l.contains("var(")),
        "community-page.css should not contain hardcoded rgba(0,0,0,...) without var()");
  }

  @Test
  void communityPageCssHasNoHardcodedRgbaWhite() {
    String body = given()
        .when()
        .get("/css/community-page.css")
        .then()
        .statusCode(200)
        .extract().asString();
    assertFalse(
        body.lines().anyMatch(l -> l.contains("rgba(255, 255, 255,") && !l.contains("var(")),
        "community-page.css should not contain hardcoded rgba(255,255,255,...) without var()");
  }

  @Test
  void allCssSheetsUseSemanticVariablesForBodyColor() {
    given()
        .when()
        .get("/css/homedir.css")
        .then()
        .statusCode(200)
        .body(containsString("var(--color-text-main)"))
        .body(containsString("var(--color-primary)"));
  }
}
