package com.scanales.eventflow.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class ProjectsResourceTest {

  @Test
  public void proyectosShowsHomedirDashboard() {
    given()
        .accept("text/html")
        .when()
        .get("/proyectos")
        .then()
        .statusCode(200)
        .body(containsString("Product delivery overview"))
        .body(containsString("Homedir feature map"))
        .body(containsString("Gamification: Levels"))
        .body(containsString("Rewards Catalog Preview"))
        .body(containsString("/notifications/center"));
  }

  @Test
  public void projectsAliasRedirectsToSpanishRoute() {
    given()
        .redirects()
        .follow(false)
        .when()
        .get("/projects")
        .then()
        .statusCode(303)
        .header("Location", endsWith("/proyectos"));
  }
}
