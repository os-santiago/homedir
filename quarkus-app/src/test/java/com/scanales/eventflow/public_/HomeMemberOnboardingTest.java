package com.scanales.eventflow.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import com.scanales.eventflow.economy.EconomyService;
import com.scanales.eventflow.model.QuestClass;
import com.scanales.eventflow.service.UserProfileService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestSecurity(
    user = "member@example.com",
    roles = {"user"})
class HomeMemberOnboardingTest {

  @Inject UserProfileService userProfileService;
  @Inject EconomyService economyService;

  @BeforeEach
  void setup() {
    userProfileService.upsert("member@example.com", "Member Example", "member@example.com");
    userProfileService.addXp("member@example.com", 140, "home-test", QuestClass.MAGE);
    userProfileService.addXp("member@example.com", 80, "home-test", QuestClass.ENGINEER);
    economyService.rewardFromGamification("member@example.com", "home-test", 600, "home-test");
  }

  @Test
  void homeShowsStarterMissionAndPersonalizedActionsForAuthenticatedMember() {
    given()
        .header("Accept-Language", "en")
        .accept("text/html")
        .when()
        .get("/")
        .then()
        .statusCode(200)
        .body(containsString("Get your first visible win"))
        .body(containsString("Link GitHub"))
        .body(containsString("Link Discord"))
        .body(containsString("Vote one Community Pick"))
        .body(containsString("Your next best moves"))
        .body(containsString("Profile setup"))
        .body(containsString("Open rewards"))
        .body(containsString("Class momentum"))
        .body(containsString("Reward runway"))
        .body(containsString("Mage is leading"))
        .body(containsString("Profile Glow"));
  }

  @Test
  void homeShowsGamificationPanelsInSpanishForAuthenticatedMember() {
    given()
        .header("Accept-Language", "es")
        .accept("text/html")
        .when()
        .get("/")
        .then()
        .statusCode(200)
        .body(containsString("Impulso por clase"))
        .body(containsString("Ruta de recompensas"))
        .body(containsString("Clase más activa"))
        .body(containsString("Saldo HCoin"));
  }
}
