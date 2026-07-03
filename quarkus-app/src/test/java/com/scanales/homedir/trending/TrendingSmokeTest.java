package com.scanales.homedir.trending;

import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

/**
 * Quick smoke test to check if the trending page actually renders repos. Debugging helper: shows
 * the actual page output when repos are (or aren't) available.
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

    // Check for count selector - only present when repos are available
    boolean hasCountSelector = body.contains("trending-count-selector");
    boolean hasErrorState = body.contains("Unable to load trending repositories");

    System.out.println("=== TRENDING PAGE SMOKE CHECK ===");
    System.out.println("Has repos (count selector visible): " + hasCountSelector);
    System.out.println("Shows error state: " + hasErrorState);
    System.out.println("Page length: " + body.length() + " chars");

    if (hasCountSelector) {
      System.out.println("COUNT SELECTOR PRESENT - repos are loading");
      // Check that all 3 count options appear
      assertTrue(body.contains("count=3"), "should have count=3 in selector");
      assertTrue(body.contains("count=5"), "should have count=5 in selector");
      assertTrue(body.contains("count=10"), "should have count=10 in selector");

      // Check for actual repo cards
      boolean hasRepoCards = body.contains("trending-repo-card");
      System.out.println("Has repo cards: " + hasRepoCards);

      if (hasRepoCards) {
        // Extract how many repos were rendered
        int cardCount = body.split("trending-repo-card", -1).length - 1;
        System.out.println("Repo cards rendered: " + cardCount);
      }
    } else {
      System.out.println("NO REPOS - showing error state (GitHub scrape may not work in test env)");
    }
    System.out.println("=== END SMOKE CHECK ===");
  }
}
