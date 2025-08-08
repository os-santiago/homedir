package com.scanales.eventflow.private_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;

@QuarkusTest
public class ProfileResourceTest {

    @Test
    @TestSecurity(user = "user@example.com")
    public void addTalkJson() {
        given()
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .body("{}")
        .when()
            .post("/private/profile/add/t1")
        .then()
            .statusCode(200)
            .body("status", is("added"))
            .body("talkId", is("t1"));
    }

    @Test
    @TestSecurity(user = "user@example.com")
    public void removeTalkJson() {
        // ensure talk exists
        given().header("Accept", "application/json").header("Content-Type", "application/json").body("{}")
            .post("/private/profile/add/t2").then().statusCode(200);

        given()
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .body("{}")
        .when()
            .post("/private/profile/remove/t2")
        .then()
            .statusCode(200)
            .body("status", is("removed"))
            .body("talkId", is("t2"));
    }

    @Test
    @TestSecurity(user = "user@example.com")
    public void addTalkHtmlRedirect() {
        given()
            .redirects().follow(false)
            .header("Accept", "text/html")
            .header("Content-Type", "application/json")
            .body("{}")
        .when()
            .post("/private/profile/add/t3")
        .then()
            .statusCode(303)
            .header("Location", endsWith("/talk/t3"));
    }

    @Test
    @TestSecurity(user = "user@example.com")
    public void removeTalkHtmlRedirect() {
        // ensure talk exists
        given().header("Accept", "application/json").header("Content-Type", "application/json").body("{}")
            .post("/private/profile/add/t4").then().statusCode(200);

        given()
            .redirects().follow(false)
            .header("Accept", "text/html")
            .header("Content-Type", "application/json")
            .body("{}")
        .when()
            .post("/private/profile/remove/t4")
        .then()
            .statusCode(303)
            .header("Location", endsWith("/private/profile"));
    }
}
