package com.scanales.homedir.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class SeoResourceTest {

  @Test
  void robotsTxtReturnsPlainText() {
    given()
        .when()
        .get("/robots.txt")
        .then()
        .statusCode(200)
        .contentType("text/plain")
        .body(containsString("User-agent: *"))
        .body(containsString("Allow: /"))
        .body(containsString("Sitemap:"));
  }

  @Test
  void robotsTxtDoesNotContainHardcodedDomain() {
    String body = given().when().get("/robots.txt").then().statusCode(200).extract().asString();
    assert !body.contains("homedir.opensourcesantiago.io")
        : "robots.txt should not contain hardcoded domain";
  }

  @Test
  void robotsTxtSitemapUrlReferencesCurrentHost() {
    String body = given().when().get("/robots.txt").then().statusCode(200).extract().asString();
    assert body.contains("localhost") || body.contains("127.0.0.1")
        : "robots.txt sitemap URL should reference localhost in test env, got: " + body;
  }

  @Test
  void sitemapXmlReturnsXml() {
    given()
        .when()
        .get("/sitemap.xml")
        .then()
        .statusCode(200)
        .contentType("application/xml")
        .body(containsString("<?xml"))
        .body(containsString("<urlset"))
        .body(not(isEmptyOrNullString()));
  }

  @Test
  void sitemapXmlDoesNotContainHardcodedDomain() {
    String body = given().when().get("/sitemap.xml").then().statusCode(200).extract().asString();
    assert !body.contains("homedir.opensourcesantiago.io")
        : "sitemap.xml should not contain hardcoded domain";
  }

  @Test
  void sitemapXmlContainsExpectedRoutes() {
    given()
        .when()
        .get("/sitemap.xml")
        .then()
        .statusCode(200)
        .body(containsString("/proyectos"))
        .body(containsString("/eventos"))
        .body(containsString("/comunidad"))
        .body(containsString("/privacy-policy"))
        .body(containsString("/terms-of-service"))
        .body(containsString("localhost"));
  }
}
