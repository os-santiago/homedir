package org.acme.oauth;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class OAuthLoginTest {

    @Test
    public void loginPageLoads() {
        given()
            .when().get("/login.html")
            .then()
            .statusCode(200)
            .body(containsString("Sign in with Google"));
    }

    @Test
    public void privateUnauthorized() {
        given()
            .when().get("/private")
            .then()
            .statusCode(302);
    }

    @Test
    @TestSecurity(user = "alice")
    public void privateAuthorized() {
        given()
            .when().get("/private")
            .then()
            .statusCode(200)
            .body(containsString("alice"));
    }
}
