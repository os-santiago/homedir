package org.acme.firebase;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class FirebaseLoginTest {

    @Test
    public void loginPageLoads() {
        given()
            .when().get("/login")
            .then()
            .statusCode(200)
            .body(containsString("Login con Google"));
    }

    @Test
    public void protectedUnauthorized() {
        given()
            .when().get("/protected")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "alice")
    public void protectedAuthorized() {
        given()
            .when().get("/protected")
            .then()
            .statusCode(200)
            .body(containsString("Bienvenido alice"));
    }
}
