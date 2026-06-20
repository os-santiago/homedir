package com.scanales.homedir.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class ProjectsResourceTest {

  @Inject ProjectsResource projectsResource;

  @Test
  public void proyectosShowsHomedirDashboard() {
    given()
        .accept("text/html")
        .when()
        .get("/proyectos")
        .then()
        .statusCode(200)
        .body(
            anyOf(
                containsString("Product delivery overview"),
                containsString("Resumen de entrega del producto")))
        .body(
            anyOf(
                containsString("Homedir feature map"),
                containsString("Mapa de features de Homedir")))
        .body(containsString("Gamification: Levels"))
        .body(containsString("Rewards Catalog Preview"))
        .body(containsString("/notifications/center"));
  }

  @Test
  public void refreshCanRunOnDemandBeforeRendering() {
    projectsResource.refreshNowForTests();

    given()
        .accept("text/html")
        .when()
        .get("/proyectos")
        .then()
        .statusCode(200)
        .body(
            anyOf(
                containsString("Product delivery overview"),
                containsString("Resumen de entrega del producto")))
        .body(
            anyOf(
                containsString("Homedir feature map"),
                containsString("Mapa de features de Homedir")));
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
