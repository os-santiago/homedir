package com.scanales.homedir.trending;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@QuarkusTest
public class TrendingResourceTest {

  private static Stream<String> trendingPageParams() {
    return Stream.of(
        "/trending",
        "/trending?period=daily",
        "/trending?period=weekly",
        "/trending?period=monthly",
        "/trending?period=daily&count=5",
        "/trending?period=weekly&count=10",
        "/trending?period=invalid",
        "/trending?count=1",
        "/trending?count=100");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("trendingPageParams")
  public void testTrendingPage(String path) {
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

  @ParameterizedTest(name = "/api{0}")
  @MethodSource("trendingPageParams")
  public void testApiTrendingEndpoint(String path) {
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

  @ParameterizedTest(name = "count selector appears on {0}")
  @MethodSource("trendingPageParams")
  public void testCountSelectorStructure(String path) {
    String body = RestAssured.given().when().get(path).then().statusCode(200).extract().asString();

    // The count selector links should always be in the page
    // (they may be inside or outside the if-empty block depending on data)
    // Show more links reference count=1, count=5, count=10
    boolean hasCount1 = hasCountLink(body, 1);
    boolean hasCount5 = hasCountLink(body, 5);
    boolean hasCount10 = hasCountLink(body, 10);

    // All three count options should appear together or not at all
    assertTrue(
        hasCount1 == hasCount5 && hasCount5 == hasCount10,
        "count selector options must appear together; got 1="
            + hasCount1
            + " 5="
            + hasCount5
            + " 10="
            + hasCount10);
  }

  private static boolean hasCountLink(String body, int count) {
    String token = "count=" + count;
    return body.contains(token + "\"") || body.contains(token + "&");
  }
}
