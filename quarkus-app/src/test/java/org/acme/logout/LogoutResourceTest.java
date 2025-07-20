package org.acme.logout;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class LogoutResourceTest {

    @Test
    public void logoutRedirectsAndClearsCookie() {
        given()
            .when().get("/logout")
            .then()
            .statusCode(303)
            .header("Location", equalTo("/"))
            .header("Set-Cookie", equalTo("q_session=; Path=/; Max-Age=0; HttpOnly; Secure"));
    }
}
