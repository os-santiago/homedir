package com.scanales.eventflow.private_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import com.scanales.eventflow.community.CommunityBoardService;
import com.scanales.eventflow.model.UserProfile;
import com.scanales.eventflow.service.UserScheduleService;
import com.scanales.eventflow.service.UserProfileService;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestSecurity(
    user = "user@example.com",
    roles = {"user"})
public class ProfileResourceTest {

  @Inject UserScheduleService userSchedule;
  @Inject UserProfileService userProfiles;
  @Inject CommunityBoardService boardService;

  @Inject SecurityIdentity securityIdentity;

  @BeforeEach
  void setup() throws Exception {
    userSchedule.reset();
    Path discordFile = Path.of(System.getProperty("homedir.data.dir"), "community", "board", "discord-users.yml");
    Files.createDirectories(discordFile.getParent());
    Files.writeString(
        discordFile,
        """
        members:
          - id: discord-claim-001
            display_name: Claimed Discord User
            handle: claimed_discord#1001
            avatar_url: https://cdn.discordapp.com/avatars/claim-001.png
          - id: discord-claim-002
            display_name: Claimed Discord User Two
            handle: claimed_discord_two#1002
            avatar_url: https://cdn.discordapp.com/avatars/claim-002.png
        """);
    boardService.resetDiscordCacheForTests();
  }

  @Test
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
  public void removeTalkJson() {
    // ensure talk exists
    given()
        .header("Accept", "application/json")
        .header("Content-Type", "application/json")
        .body("{}")
        .post("/private/profile/add/t2")
        .then()
        .statusCode(200);

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
  public void addTalkHtmlRedirect() {
    given()
        .redirects()
        .follow(false)
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
  public void removeTalkHtmlRedirect() {
    // ensure talk exists
    given()
        .header("Accept", "application/json")
        .header("Content-Type", "application/json")
        .body("{}")
        .post("/private/profile/add/t4")
        .then()
        .statusCode(200);

    given()
        .redirects()
        .follow(false)
        .header("Accept", "text/html")
        .header("Content-Type", "application/json")
        .body("{}")
        .when()
        .post("/private/profile/remove/t4")
        .then()
        .statusCode(303)
        .header("Location", endsWith("/private/profile"));
  }

  @Test
  public void updateTalkRejectsUnknownProperty() {
    given()
        .header("Accept", "application/json")
        .header("Content-Type", "application/json")
        .body("{\"unknown\":true}")
        .when()
        .post("/private/profile/update/t5")
        .then()
        .statusCode(400);
  }

  @Test
  public void updateTalkInvalidRating() {
    given()
        .header("Accept", "application/json")
        .header("Content-Type", "application/json")
        .body("{\"rating\":6}")
        .when()
        .post("/private/profile/update/t6")
        .then()
        .statusCode(400);
  }

  @Test
  public void visitedParamAddsTalkAndMarksAttended() {
    String email = currentUserEmail();
    assertFalse(userSchedule.getTalkDetailsForUser(email).containsKey("t7"));
    given()
        .redirects()
        .follow(false)
        .when()
        .get("/private/profile/add/t7?visited=true")
        .then()
        .statusCode(303)
        .header("Location", endsWith("/private/profile"));
    var details = userSchedule.getTalkDetailsForUser(email).get("t7");
    assertNotNull(details);
    assertTrue(details.attended);
  }

  @Test
  public void attendedParamAddsTalkAndMarksAttended() {
    String email = currentUserEmail();
    assertFalse(userSchedule.getTalkDetailsForUser(email).containsKey("t8"));
    given()
        .redirects()
        .follow(false)
        .when()
        .get("/private/profile/add/t8?attended=true")
        .then()
        .statusCode(303)
        .header("Location", endsWith("/private/profile"));
    var details = userSchedule.getTalkDetailsForUser(email).get("t8");
    assertNotNull(details);
    assertTrue(details.attended);
  }

  @Test
  public void currentUserEmailDefaultsToPrincipalName() {
    assertEquals(securityIdentity.getPrincipal().getName(), currentUserEmail());
  }

  @Test
  public void linkDiscordManualClaimIsRejectedAndRequiresOauth() {
    given()
        .redirects()
        .follow(false)
        .contentType("application/x-www-form-urlencoded")
        .formParam("discordId", "discord-claim-001")
        .formParam("redirect", "/private/profile")
        .when()
        .post("/private/profile/link-discord")
        .then()
        .statusCode(303)
        .header("Location", containsString("discordError=oauth_required"));

    UserProfile profile = userProfiles.find(currentUserEmail()).orElse(null);
    if (profile != null) {
      assertNull(profile.getDiscord());
    }
  }

  @Test
  public void linkDiscordManualClaimDoesNotOverrideExistingClaims() {
    userProfiles.linkDiscord(
        "other.member@example.com",
        "Other Member",
        "other.member@example.com",
        new UserProfile.DiscordAccount(
            "discord-claim-002",
            "claimed_discord_two#1002",
            "https://discord.com/users/discord-claim-002",
            null,
            Instant.now()));

    given()
        .redirects()
        .follow(false)
        .contentType("application/x-www-form-urlencoded")
        .formParam("discordId", "discord-claim-002")
        .formParam("redirect", "/private/profile")
        .when()
        .post("/private/profile/link-discord")
        .then()
        .statusCode(303)
        .header("Location", containsString("discordError=oauth_required"));

    UserProfile other = userProfiles.find("other.member@example.com").orElseThrow();
    assertNotNull(other.getDiscord());
    assertEquals("discord-claim-002", other.getDiscord().id());
  }

  @Test
  public void unlinkDiscordRemovesClaim() {
    userProfiles.linkDiscord(
        currentUserEmail(),
        "Current User",
        currentUserEmail(),
        new UserProfile.DiscordAccount(
            "discord-claim-001",
            "claimed_discord#1001",
            "https://discord.com/users/discord-claim-001",
            null,
            Instant.now()));

    given()
        .redirects()
        .follow(false)
        .contentType("application/x-www-form-urlencoded")
        .formParam("redirect", "/private/profile")
        .when()
        .post("/private/profile/unlink-discord")
        .then()
        .statusCode(303)
        .header("Location", containsString("/private/profile?discordUnlinked=1"));

    UserProfile profile = userProfiles.find(currentUserEmail()).orElseThrow();
    assertNull(profile.getDiscord());
  }

  private String currentUserEmail() {
    Object emailAttr = securityIdentity.getAttribute("email");
    if (emailAttr != null && !emailAttr.toString().isBlank()) {
      return emailAttr.toString();
    }
    return securityIdentity.getPrincipal().getName();
  }
}
