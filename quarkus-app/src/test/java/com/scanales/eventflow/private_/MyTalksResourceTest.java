package com.scanales.eventflow.private_;

import static io.restassured.RestAssured.given;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;

@QuarkusTest
public class MyTalksResourceTest {

    @Test
    @TestSecurity(user = "user@example.com")
    public void getMyTalks() {
        given()
          .when().get("/private/my-talks")
          .then().statusCode(200);
    }
}
