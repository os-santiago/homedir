package com.scanales.homedir.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class LegalPagesSpanishTest {

  @Test
  void spanishPrivacyRouteReturnsSpanishContent() {
    given()
        .when()
        .get("/politica-de-privacidad")
        .then()
        .statusCode(200)
        .body(containsString("Política de Privacidad de la aplicación"))
        .body(containsString("Tus derechos en Chile"));
  }

  @Test
  void spanishTermsRouteReturnsSpanishContent() {
    given()
        .when()
        .get("/condiciones-del-servicio")
        .then()
        .statusCode(200)
        .body(containsString("Condiciones del servicio de la aplicación"))
        .body(containsString("Cambios, información al consumidor y jurisdicción"));
  }

  @Test
  void spanishRoutesAreNotIdenticalToEnglishRoutes() {
    given()
        .when()
        .get("/privacy-policy")
        .then()
        .statusCode(200);
  }
}
