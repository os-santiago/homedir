package com.scanales.homedir.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.qute.Engine;
import io.quarkus.qute.Template;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class ErrorPagesTest {

  @Inject Engine engine;

  @Test
  void unknownRouteRenders404Page() {
    given()
        .accept("text/html")
        .when()
        .get("/__route_that_does_not_exist__")
        .then()
        .statusCode(404)
        .body(containsString("404"))
        .body(containsString("Page Not Found"))
        .body(containsString("Go to Home"));
  }

  @Test
  void error500TemplateCompilesAndRenders() {
    Template template = engine.getTemplate("errors/500");
    assertNotNull(template, "errors/500 template should be resolvable");
    String html = template.render();
    assertTrue(html.contains("500"), "rendered template should contain 500");
    assertTrue(
        html.contains("Internal Server Error"), "rendered template should contain error heading");
    assertTrue(html.contains("Go to Home"), "rendered template should contain home link");
  }

  @Test
  void error404TemplateCompilesAndRenders() {
    Template template = engine.getTemplate("errors/404");
    assertNotNull(template, "errors/404 template should be resolvable");
    String html = template.render();
    assertTrue(html.contains("404"), "rendered template should contain 404");
    assertTrue(html.contains("Page Not Found"), "rendered template should contain error heading");
    assertTrue(html.contains("Go to Home"), "rendered template should contain home link");
  }

  @Test
  void error403TemplateCompilesAndRenders() {
    Template template = engine.getTemplate("errors/403");
    assertNotNull(template, "errors/403 template should be resolvable");
    String html = template.render();
    assertTrue(html.contains("403"), "rendered template should contain 403");
    assertTrue(html.contains("Access Denied"), "rendered template should contain error heading");
    assertTrue(html.contains("Go to Home"), "rendered template should contain home link");
  }
}
