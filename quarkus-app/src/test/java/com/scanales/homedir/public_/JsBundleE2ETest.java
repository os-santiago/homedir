package com.scanales.homedir.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class JsBundleE2ETest {

  @Test
  void coreBundleServesSuccessfully() {
    given()
        .when()
        .get("/js/core-bundle.js")
        .then()
        .statusCode(200)
        .body(not(isEmptyOrNullString()));
  }

  @Test
  void communityBundleServesSuccessfully() {
    given()
        .when()
        .get("/js/community-bundle.js")
        .then()
        .statusCode(200)
        .body(not(isEmptyOrNullString()));
  }

  @Test
  void coreBundleIsLargerThanMinimumSize() {
    String body =
        given().when().get("/js/core-bundle.js").then().statusCode(200).extract().asString();
    assertTrue(
        body.length() > 10000, "core-bundle.js should be at least 10KB, got " + body.length());
  }

  @Test
  void communityBundleIsLargerThanMinimumSize() {
    String body =
        given().when().get("/js/community-bundle.js").then().statusCode(200).extract().asString();
    assertTrue(
        body.length() > 50000, "community-bundle.js should be at least 50KB, got " + body.length());
  }

  @Test
  void homePageIncludesCoreBundleNotIndividualScripts() {
    String html =
        given().accept("text/html").when().get("/").then().statusCode(200).extract().asString();
    assertTrue(html.contains("/js/core-bundle.js"), "home page should reference core-bundle.js");
    assertTrue(
        !html.contains("/js/homedir.js"), "home page should NOT reference individual homedir.js");
    assertTrue(!html.contains("/js/app.js"), "home page should NOT reference individual app.js");
  }

  @Test
  void communityPageIncludesCommunityBundleNotIndividualScripts() {
    String html =
        given()
            .accept("text/html")
            .when()
            .get("/comunidad")
            .then()
            .statusCode(200)
            .extract()
            .asString();
    assertTrue(
        html.contains("/js/community-bundle.js"),
        "community page should reference community-bundle.js");
    assertTrue(
        !html.contains("/js/home-lightning.js"),
        "community page should NOT reference home-lightning.js");
    assertTrue(
        !html.contains("/js/community-content.js"),
        "community page should NOT reference community-content.js");
    assertTrue(
        !html.contains("/js/community-submissions.js"),
        "community page should NOT reference community-submissions.js");
  }

  @Test
  void homePageBootstrapRunsBeforeCoreBundle() {
    String html =
        given().accept("text/html").when().get("/").then().statusCode(200).extract().asString();
    int bootstrapIndex = html.indexOf("window.userAuthenticated");
    int bundleIndex = html.indexOf("/js/core-bundle.js");
    assertTrue(bootstrapIndex >= 0, "missing userAuthenticated bootstrap");
    assertTrue(bundleIndex >= 0, "missing core-bundle.js");
    assertTrue(bootstrapIndex < bundleIndex, "bootstrap must appear before core-bundle.js");
  }

  @Test
  void individualCoreJsFilesStillAccessible() {
    given().when().get("/js/homedir.js").then().statusCode(200);
    given().when().get("/js/app.js").then().statusCode(200);
    given().when().get("/js/notifications-adapter.js").then().statusCode(200);
    given().when().get("/js/notifications.js").then().statusCode(200);
    given().when().get("/js/global-notifications-ws.js").then().statusCode(200);
  }

  @Test
  void individualCommunityJsFilesStillAccessible() {
    given().when().get("/js/home-lightning.js").then().statusCode(200);
    given().when().get("/js/community-content.js").then().statusCode(200);
    given().when().get("/js/community-submissions.js").then().statusCode(200);
  }

  @Test
  void eventsPageIncludesCoreBundle() {
    given()
        .accept("text/html")
        .when()
        .get("/eventos")
        .then()
        .statusCode(200)
        .body(containsString("/js/core-bundle.js"))
        .body(not(containsString("/js/homedir.js")));
  }
}
