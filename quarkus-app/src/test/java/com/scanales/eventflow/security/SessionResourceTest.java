package com.scanales.eventflow.security;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

/** Tests for session refresh endpoint. */
@QuarkusTest
public class SessionResourceTest {

  @Test
  @TestSecurity(user = "user@example.com")
  public void refreshKeepsSessionActive() {
    given().when().post("/auth/session/refresh").then().statusCode(200).body("active", is(true));
  }
}
