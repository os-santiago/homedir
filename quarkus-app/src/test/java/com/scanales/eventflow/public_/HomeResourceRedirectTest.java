package com.scanales.eventflow.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class HomeResourceRedirectTest {

    @Test
    public void eventsPathRedirectsToHome() {
        given()
            .when().get("/events")
            .then()
            .statusCode(301)
            .header("Location", equalTo("/"));
    }
}
