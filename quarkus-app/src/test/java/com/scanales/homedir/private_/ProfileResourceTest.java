package com.scanales.homedir.private_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import com.scanales.homedir.challenges.ChallengeService;
import com.scanales.homedir.cfp.CfpSubmissionService;
import com.scanales.homedir.community.CommunityBoardService;
import com.scanales.homedir.model.Event;
import com.scanales.homedir.model.GamificationActivity;
import com.scanales.homedir.model.QuestClass;
import com.scanales.homedir.model.UserProfile;
import com.scanales.homedir.service.EventService;
import com.scanales.homedir.service.UserScheduleService;
import com.scanales.homedir.service.UserProfileService;
import com.scanales.homedir.volunteers.VolunteerApplication;
import com.scanales.homedir.volunteers.VolunteerApplicationService;
import com.scanales.homedir.volunteers.VolunteerApplicationStatus;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
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
  @Inject EventService eventService;
  @Inject CfpSubmissionService cfpSubmissionService;
  @Inject VolunteerApplicationService volunteerApplicationService;
  @Inject ChallengeService challengeService;

  @Inject SecurityIdentity securityIdentity;

  private static final String CFP_EVENT_ID = "profile-cfp-event";
  private static final String VOLUNTEER_EVENT_ID = "profile-volunteer-event";

  @BeforeEach
  void setup() throws Exception {
    userSchedule.reset();
    challengeService.resetForTests();
    cfpSubmissionService.clearAllForTests();
    volunteerApplicationService.clearAllForTests();
    eventService.saveEvent(new Event(CFP_EVENT_ID, "Profile CFP Event", "CFP profile integration test"));
    eventService.saveEvent(new Event(VOLUNTEER_EVENT_ID, "Profile Volunteer Event", "Volunteer profile integration test"));
    cfpSubmissionService.create(
        currentUserEmail(),
        "Current User",
        new CfpSubmissionService.CreateRequest(
            CFP_EVENT_ID,
            "Profile CFP Talk",
            "CFP summary for profile page.",
            "Detailed abstract for profile CFP test coverage.",
            "beginner",
            "talk",
            30,
            "en",
            "ai-agents-copilots",
            List.of("profile"),
            List.of("https://example.com/profile-cfp")));
    VolunteerApplication volunteerCreated =
        volunteerApplicationService.create(
            currentUserEmail(),
            "Current User",
            new VolunteerApplicationService.CreateRequest(
                VOLUNTEER_EVENT_ID,
                "I have operational experience running community events.",
                "I want to support this edition.",
                "I can coordinate attendee registration."));
    volunteerApplicationService.updateStatus(
        volunteerCreated.id(),
        VolunteerApplicationStatus.SELECTED,
        "admin@example.org",
        "Great fit",
        volunteerCreated.updatedAt());
    userProfiles.linkGithub(
        currentUserEmail(),
        "Current User",
        currentUserEmail(),
        new UserProfile.GithubAccount(
            "current-user",
            "https://github.com/current-user",
            "https://avatars.githubusercontent.com/u/101",
            "101",
            Instant.now()));
    challengeService.recordActivity(currentUserEmail(), GamificationActivity.COMMUNITY_VOTE);
    challengeService.recordActivity(currentUserEmail(), GamificationActivity.EVENT_VIEW);
    challengeService.recordActivity(currentUserEmail(), GamificationActivity.AGENDA_VIEW);
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

  @Test
  public void updateLocalePersistsProfilePreferenceAndSetsLocaleCookie() {
    userProfiles.upsert(currentUserEmail(), currentUserEmail(), currentUserEmail());

    given()
        .redirects()
        .follow(false)
        .contentType("application/x-www-form-urlencoded")
        .formParam("locale", "en")
        .formParam("redirect", "/comunidad")
        .when()
        .post("/private/profile/update-locale")
        .then()
        .statusCode(303)
        .header("Location", endsWith("/comunidad"))
        .header("Set-Cookie", containsString("QP_LOCALE=en"));

    UserProfile profile = userProfiles.find(currentUserEmail()).orElseThrow();
    assertEquals("en", profile.getPreferredLocale());
  }

  @Test
  public void updateLocaleFallsBackToSpanishWhenLocaleIsUnsupported() {
    userProfiles.upsert(currentUserEmail(), currentUserEmail(), currentUserEmail());

    given()
        .redirects()
        .follow(false)
        .contentType("application/x-www-form-urlencoded")
        .formParam("locale", "fr")
        .formParam("redirect", "/private/profile")
        .when()
        .post("/private/profile/update-locale")
        .then()
        .statusCode(303)
        .header("Location", endsWith("/private/profile"))
        .header("Set-Cookie", containsString("QP_LOCALE=es"));

    UserProfile profile = userProfiles.find(currentUserEmail()).orElseThrow();
    assertEquals("es", profile.getPreferredLocale());
  }

  @Test
  public void profileShowsClassMomentumAndNoManualClassForm() {
    userProfiles.addXp(currentUserEmail(), 20, "Test scientist progress", QuestClass.SCIENTIST);

    given()
        .header("Accept-Language", "en")
        .when()
        .get("/private/profile")
        .then()
        .statusCode(200)
        .body(anyOf(containsString("Activity to class map"), containsString("Mapa actividad")))
        .body(containsString("/notifications/center"))
        .body(containsString("/private/profile/catalog#"))
        .body(not(containsString("/private/profile/update-class")));
  }

  @Test
  public void profileShowsCfpOverviewAndManageLinks() {
    given()
        .header("Accept-Language", "en")
        .when()
        .get("/private/profile")
        .then()
        .statusCode(200)
        .body(containsString("Call for Papers"))
        .body(containsString("Profile CFP Talk"))
        .body(containsString("/event/" + CFP_EVENT_ID + "/cfp#my-proposals"));
  }

  @Test
  public void profileShowsChallengesPanelAndProgress() {
    userProfiles.updateLocale(currentUserEmail(), "en");
    given()
        .header("Accept-Language", "en")
        .when()
        .get("/private/profile")
        .then()
        .statusCode(200)
        .body(containsString("data-profile-nav=\"challenges-panel\""))
        .body(containsString("My challenges"))
        .body(containsString("Community Scout"))
        .body(containsString("Event Explorer"))
        .body(containsString("Open Source Identity"))
        .body(containsString("Explore Events"))
        .body(containsString("Open Community Picks"));
  }

  @Test
  public void profileShowsVolunteerOverviewAndLoungeLink() {
    given()
        .header("Accept-Language", "en")
        .when()
        .get("/private/profile")
        .then()
        .statusCode(200)
        .body(containsString("Volunteers"))
        .body(containsString("Profile Volunteer Event"))
        .body(containsString("/event/" + VOLUNTEER_EVENT_ID + "/volunteers"))
        .body(containsString("/event/" + VOLUNTEER_EVENT_ID + "/volunteers#volunteer-lounge"));
  }

  @Test
  public void economyCatalogPageLoadsWithPreviewAnchors() {
    given()
        .when()
        .get("/private/profile/catalog")
        .then()
        .statusCode(200)
        .body(containsString("id=\"profile-glow\""))
        .body(containsString("id=\"community-spotlight\""))
        .body(containsString("id=\"event-fast-pass\""))
        .body(containsString("id=\"architect-badge\""))
        .body(containsString("hd-catalog-compare"))
        .body(containsString("hd-catalog-compare-label"))
        .body(containsString("/private/profile#economy-panel"));
  }

  @Test
  public void profileHistoryLoadsInBatchesOfTenWithSafeWindowCap() {
    String userId = currentUserEmail();
    for (int i = 1; i <= 130; i++) {
      userProfiles.addXp(userId, 1, "History item " + i);
    }

    given()
        .when()
        .get("/private/profile")
        .then()
        .statusCode(200)
        .body(
            anyOf(
                containsString("Load 10 older entries"),
                containsString("Cargar 10 registros anteriores")))
        .body(containsString("historyLimit=20"));

    given()
        .when()
        .get("/private/profile?historyLimit=200")
        .then()
        .statusCode(200)
        .body(
            anyOf(
                containsString("Showing up to 100 recent entries for safety."),
                containsString("Mostrando hasta 100 registros recientes por seguridad.")));
  }

  @Test
  public void profileShowsHybridClassWhenTopMomentumIsClose() {
    String userId = currentUserEmail();
    UserProfile profile = userProfiles.upsert(userId, userId, userId);
    java.util.EnumMap<QuestClass, Integer> classXp = new java.util.EnumMap<>(QuestClass.class);
    classXp.put(QuestClass.SCIENTIST, 15);
    classXp.put(QuestClass.MAGE, 14);
    classXp.put(QuestClass.ENGINEER, 5);
    classXp.put(QuestClass.WARRIOR, 0);
    profile.setClassXp(classXp);
    profile.setCurrentXp(34);
    userProfiles.update(profile);

    given()
        .when()
        .get("/private/profile")
        .then()
        .statusCode(200)
        .body(containsString("Most active profile: Hybrid (Scientist + Mage)"));
  }

  private String currentUserEmail() {
    Object emailAttr = securityIdentity.getAttribute("email");
    if (emailAttr != null && !emailAttr.toString().isBlank()) {
      return emailAttr.toString();
    }
    return securityIdentity.getPrincipal().getName();
  }
}
