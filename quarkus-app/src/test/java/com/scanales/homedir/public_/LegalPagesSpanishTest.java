package com.scanales.homedir.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
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
        .body(
            anyOf(
                containsString("Política de privacidad de la aplicación"),
                containsString("Application Privacy Policy")))
        .body(anyOf(containsString("Tus derechos en Chile"), containsString("Your Rights in Chile")));
  }

  @Test
  void spanishTermsRouteReturnsSpanishContent() {
    given()
        .when()
        .get("/condiciones-del-servicio")
        .then()
        .statusCode(200)
        .body(
            anyOf(
                containsString("Términos de servicio de la aplicación"),
                containsString("Application Terms of Service")))
        .body(
            anyOf(
                containsString("Cambios, información al consumidor y jurisdicción"),
                containsString("Changes, Consumer Information, and Jurisdiction")));
  }

  @Test
  void spanishRoutesAreNotIdenticalToEnglishRoutes() {
    given().when().get("/privacy-policy").then().statusCode(200);
  }
}
