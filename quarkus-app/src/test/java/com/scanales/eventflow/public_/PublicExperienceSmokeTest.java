package com.scanales.eventflow.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class PublicExperienceSmokeTest {

  private static final int HOME_HTML_BUDGET_BYTES = 120_000;
  private static final int COMMUNITY_HTML_BUDGET_BYTES = 240_000;
  private static final int COMMUNITY_BOARD_HTML_BUDGET_BYTES = 200_000;

  @Test
  void homePageAvoidsKnownRuntimeRegressionPatterns() {
    String html = fetchHtmlWithBudget("/", HOME_HTML_BUDGET_BYTES);
    assertTrue(html.contains("window.userAuthenticated"));
    assertTrue(html.contains("data-login-return-current=\"true\""));
    assertFalse(html.contains("canva-theme-v2.css"));
    assertFalse(html.contains("/js/retro-theme.js"));
  }

  @Test
  void communityPageAvoidsKnownRuntimeRegressionPatterns() {
    String html = fetchHtmlWithBudget("/comunidad", COMMUNITY_HTML_BUDGET_BYTES);
    assertTrue(html.contains("window.userAuthenticated"));
    assertTrue(html.contains("data-login-return-current=\"true\""));
    assertFalse(html.contains("canva-theme-v2.css"));
    assertFalse(html.contains("/js/retro-theme.js"));
  }

  @Test
  void discordBoardPageStaysWithinBudgetAndUsesCurrentLayout() {
    String html =
        fetchHtmlWithBudget("/comunidad/board/discord-users", COMMUNITY_BOARD_HTML_BUDGET_BYTES);
    assertTrue(html.contains("window.userAuthenticated"));
    assertFalse(html.contains("canva-theme-v2.css"));
  }

  @Test
  void criticalCssAssetsReturnSuccess() {
    given().when().get("/css/homedir.css").then().statusCode(200).body(not(isEmptyOrNullString()));
    given().when().get("/css/retro-theme.css").then().statusCode(200).body(not(isEmptyOrNullString()));
  }

  @Test
  void criticalJavascriptAssetsReturnSuccess() {
    given().when().get("/js/homedir.js").then().statusCode(200).body(not(isEmptyOrNullString()));
    given().when().get("/js/app.js").then().statusCode(200).body(not(isEmptyOrNullString()));
  }

  private String fetchHtmlWithBudget(String path, int budgetBytes) {
    String html =
        given().accept("text/html").when().get(path).then().statusCode(200).extract().asString();
    assertTrue(
        html.length() <= budgetBytes,
        () -> "HTML payload over budget for " + path + ": " + html.length() + " bytes");
    return html;
  }
}
