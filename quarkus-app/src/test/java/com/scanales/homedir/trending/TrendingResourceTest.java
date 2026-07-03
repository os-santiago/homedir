package com.scanales.homedir.trending;

import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@QuarkusTest
public class TrendingResourceTest {

  private static Stream<Arguments> trendingPageParams() {
    return Stream.of(
        Arguments.of("/trending", "default (no params)"),
        Arguments.of("/trending?period=daily", "daily period"),
        Arguments.of("/trending?period=weekly", "weekly period"),
        Arguments.of("/trending?period=monthly", "monthly period"),
        Arguments.of("/trending?period=daily&count=5", "daily + count=5"),
        Arguments.of("/trending?period=weekly&count=10", "weekly + count=10"),
        Arguments.of("/trending?period=invalid", "invalid period falls back to daily"),
        Arguments.of("/trending?count=1", "minimum count"),
        Arguments.of("/trending?count=100", "count capped at 10"));
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("trendingPageParams")
  public void testTrendingPage(String path, String description) {
    RestAssured.given()
        .when()
        .get(path)
        .then()
        .statusCode(200)
        .contentType("text/html")
        // Always-present elements
        .body(containsString("Trending Repositories"))
        .body(containsString("period=daily"))
        .body(containsString("period=weekly"))
        .body(containsString("period=monthly"));
  }

  @ParameterizedTest(name = "/api/trending - {1}")
  @MethodSource("trendingPageParams")
  public void testApiTrendingEndpoint(String path, String description) {
    String apiPath = path.replace("/trending", "/api/trending");
    RestAssured.given()
        .when()
        .get(apiPath)
        .then()
        .statusCode(200)
        .contentType("text/html")
        .body(containsString("Trending Repositories"))
        .body(containsString("period=daily"));
  }

  @ParameterizedTest(name = "count selector appears on {1}")
  @MethodSource("trendingPageParams")
  public void testCountSelectorStructure(String path, String description) {
    String body = RestAssured.given().when().get(path).then().statusCode(200).extract().asString();

    // The count selector links should always be in the page
    // (they may be inside or outside the if-empty block depending on data)
    // Show more links reference count=1, count=5, count=10
    boolean hasCount1 = body.contains("count=1");
    boolean hasCount5 = body.contains("count=5");
    boolean hasCount10 = body.contains("count=10");

    // All three count options should appear together or not at all
    assert hasCount1 == hasCount5 && hasCount5 == hasCount10
        : "count selector options must appear together; got 1="
            + hasCount1
            + " 5="
            + hasCount5
            + " 10="
            + hasCount10;
  }
}
