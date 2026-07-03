package com.scanales.homedir.trending;

import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

/**
 * Smoke test: trending page renders structure + count selector (1/5/10) regardless of cache state.
 */
@QuarkusTest
public class TrendingSmokeTest {

  @Test
  public void checkTrendingPageContent() {
    String body =
        RestAssured.given().when().get("/trending").then().statusCode(200).extract().asString();

    // Always present: page structure
    assertTrue(body.contains("Trending Repositories"), "should have heading");
    assertTrue(
        body.contains("period=daily") || body.contains("period=weekly"),
        "should have period links: " + body.substring(0, Math.min(200, body.length())));

    // Count selector always renders (moved outside {#if repos.isEmpty()})
    assertTrue(body.contains("trending-count-selector"), "count selector should always render");
    assertTrue(body.contains("count=1"), "should have count=1 in selector");
    assertTrue(body.contains("count=5"), "should have count=5 in selector");
    assertTrue(body.contains("count=10"), "should have count=10 in selector");
  }
}
