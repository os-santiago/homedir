package com.scanales.homedir.trending;

import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class TrendingResourceTest {

  @Test
  public void testTrendingPageDaily() {
    RestAssured.given()
        .when()
        .get("/trending?period=daily")
        .then()
        .statusCode(200)
        .contentType("text/html");
  }

  @Test
  public void testTrendingPageWeekly() {
    RestAssured.given()
        .when()
        .get("/trending?period=weekly")
        .then()
        .statusCode(200)
        .contentType("text/html");
  }

  @Test
  public void testTrendingPageMonthly() {
    RestAssured.given()
        .when()
        .get("/trending?period=monthly")
        .then()
        .statusCode(200)
        .contentType("text/html");
  }

  @Test
  public void testTrendingPageWithCount() {
    RestAssured.given()
        .when()
        .get("/trending?period=daily&count=5")
        .then()
        .statusCode(200)
        .contentType("text/html");
  }

  @Test
  public void testTrendingPageDefault() {
    RestAssured.given().when().get("/trending").then().statusCode(200).contentType("text/html");
  }
}
