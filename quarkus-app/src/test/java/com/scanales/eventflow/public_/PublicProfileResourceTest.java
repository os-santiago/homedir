package com.scanales.eventflow.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import com.scanales.eventflow.model.UserProfile;
import com.scanales.eventflow.service.UserProfileService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class PublicProfileResourceTest {

  @Inject UserProfileService userProfileService;

  @BeforeEach
  void setup() {
    userProfileService.linkGithub(
        "public.user@example.com",
        "Public User",
        "public.user@example.com",
        new UserProfile.GithubAccount(
            "public-user",
            "https://github.com/public-user",
            "https://avatars.githubusercontent.com/u/9001",
            "9001",
            Instant.parse("2026-02-01T00:00:00Z")));
    userProfileService.linkDiscord(
        "public.user@example.com",
        "Public User",
        "public.user@example.com",
        new UserProfile.DiscordAccount(
            "discord-public-1",
            "public_user#1001",
            "https://discord.com/users/discord-public-1",
            "https://cdn.discordapp.com/avatars/discord-public-1.png",
            Instant.parse("2026-02-10T00:00:00Z")));

    userProfileService.upsert("homedir.user@example.com", "Homedir User", "homedir.user@example.com");
    userProfileService.linkDiscord(
        "homedir.user@example.com",
        "Homedir User",
        "homedir.user@example.com",
        new UserProfile.DiscordAccount(
            "discord-homedir-2",
            "homedir_user#1002",
            "https://discord.com/users/discord-homedir-2",
            null,
            Instant.parse("2026-02-10T00:00:00Z")));
  }

  @Test
  void githubProfileShowsUnifiedAccounts() {
    given()
        .when()
        .get("/u/public-user")
        .then()
        .statusCode(200)
        .body(containsString("Public User"))
        .body(containsString("@public-user"))
        .body(containsString("public_user#1001"))
        .body(containsString("Connected accounts"));
  }

  @Test
  void homedirProfileWithoutGithubIsResolvable() {
    String homedirId = homedirMemberId("homedir.user@example.com");
    given()
        .when()
        .get("/u/" + homedirId)
        .then()
        .statusCode(200)
        .body(containsString("Homedir User"))
        .body(containsString(homedirId))
        .body(containsString("homedir_user#1002"));
  }

  @Test
  void unknownProfileReturnsNotFound() {
    given().when().get("/u/does-not-exist").then().statusCode(404);
  }

  private static String homedirMemberId(String identitySeed) {
    return "hd-" + shortHash(identitySeed.trim().toLowerCase(), 16);
  }

  private static String shortHash(String value, int maxLength) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(hashed.length * 2);
      for (byte b : hashed) {
        hex.append(String.format("%02x", b));
      }
      int end = Math.min(hex.length(), Math.max(6, maxLength));
      return hex.substring(0, end);
    } catch (Exception e) {
      return Integer.toHexString(value.hashCode());
    }
  }
}

