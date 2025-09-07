package com.scanales.eventflow.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class HealthResourceTest {

  @Test
  public void readyEndpoint() {
    given().when().get("/health/ready").then().statusCode(200).body(equalTo("ready"));
  }
}
