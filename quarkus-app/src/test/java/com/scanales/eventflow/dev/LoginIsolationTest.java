package com.scanales.eventflow.dev;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class LoginIsolationTest {

    @Test
    public void testLoginHtmlIsNotFoundInTestProfile() {
        // In @QuarkusTest, the default profile is 'test', so @IfBuildProfile("dev")
        // beans should be disabled.
        RestAssured.given()
                .when().get("/login.html")
                .then()
                .log().ifValidationFails()
                .statusCode(org.hamcrest.Matchers.not(200));
    }

    @Test
    public void testLoginPathIsNotFoundInTestProfile() {
        // Also check /login just in case config defaults shifted
        RestAssured.given()
                .when().get("/login")
                .then()
                .statusCode(org.hamcrest.Matchers.not(200));
    }
}
