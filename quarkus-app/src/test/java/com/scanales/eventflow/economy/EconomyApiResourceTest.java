package com.scanales.eventflow.economy;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class EconomyApiResourceTest {

  @Inject EconomyService economyService;

  @BeforeEach
  void setup() {
    economyService.resetForTests();
  }

  @Test
  void catalogIsPublic() {
    given()
        .accept("application/json")
        .when()
        .get("/api/economy/catalog")
        .then()
        .statusCode(200)
        .body("count", greaterThan(0))
        .body("items", hasSize(greaterThan(0)));
  }

  @Test
  void walletRequiresAuth() {
    given()
        .accept("application/json")
        .when()
        .get("/api/economy/wallet")
        .then()
        .statusCode(401);
  }

  @Test
  @TestSecurity(user = "user@example.com")
  void authenticatedUserCanPurchaseAndQueryState() {
    economyService.rewardFromGamification("user@example.com", "seed", 1000, "seed");

    given()
        .contentType("application/json")
        .body("{\"itemId\":\"profile-glow\"}")
        .when()
        .post("/api/economy/purchase")
        .then()
        .statusCode(200)
        .body("purchase.itemId", equalTo("profile-glow"))
        .body("purchase.balanceAfterHcoin", greaterThanOrEqualTo(0));

    given()
        .accept("application/json")
        .when()
        .get("/api/economy/wallet")
        .then()
        .statusCode(200)
        .body("wallet.balance_hcoin", notNullValue());

    given()
        .accept("application/json")
        .when()
        .get("/api/economy/inventory?limit=10&offset=0")
        .then()
        .statusCode(200)
        .body("items", hasSize(greaterThan(0)));

    given()
        .accept("application/json")
        .when()
        .get("/api/economy/transactions?limit=10&offset=0")
        .then()
        .statusCode(200)
        .body("items", hasSize(greaterThan(0)));
  }
}
