package com.scanales.homedir.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import com.scanales.homedir.challenges.ChallengeService;
import com.scanales.homedir.cfp.CfpSubmission;
import com.scanales.homedir.cfp.CfpEventConfigService;
import com.scanales.homedir.cfp.CfpSubmissionService;
import com.scanales.homedir.cfp.CfpSubmissionStatus;
import com.scanales.homedir.model.Event;
import com.scanales.homedir.model.GamificationActivity;
import com.scanales.homedir.model.UserProfile;
import com.scanales.homedir.model.QuestClass;
import com.scanales.homedir.service.EventService;
import com.scanales.homedir.service.UserProfileService;
import com.scanales.homedir.volunteers.VolunteerApplication;
import com.scanales.homedir.volunteers.VolunteerApplicationService;
import com.scanales.homedir.volunteers.VolunteerApplicationStatus;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class PublicProfileResourceTest {

  @Inject UserProfileService userProfileService;
  @Inject EventService eventService;
  @Inject CfpEventConfigService cfpEventConfigService;
  @Inject CfpSubmissionService cfpSubmissionService;
  @Inject VolunteerApplicationService volunteerApplicationService;
  @Inject ChallengeService challengeService;

  private static final String CFP_EVENT_ID = "public-cfp-event";
  private static final String VOLUNTEER_EVENT_ID = "public-volunteer-event";

  @BeforeEach
  void setup() {
    cfpSubmissionService.clearAllForTests();
    volunteerApplicationService.clearAllForTests();
    challengeService.resetForTests();
    eventService.saveEvent(new Event(CFP_EVENT_ID, "Public CFP Event", "Public CFP profile view test"));
    eventService.saveEvent(new Event(VOLUNTEER_EVENT_ID, "Public Volunteer Event", "Public volunteer profile view test"));
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
    userProfileService.addXp(
        "public.user@example.com", 50, "Community vote", QuestClass.SCIENTIST);
    userProfileService.addXp(
        "public.user@example.com", 20, "Project exploration", QuestClass.ENGINEER);

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

    CfpSubmission created =
        cfpSubmissionService.create(
            "public.user@example.com",
            "Public User",
            new CfpSubmissionService.CreateRequest(
                CFP_EVENT_ID,
                "Public CFP Talk",
                "Public CFP summary.",
                "Public CFP abstract text.",
                "beginner",
                "talk",
                30,
                "en",
                "ai-agents-copilots",
                List.of("public"),
                List.of("https://example.com/public-cfp")));
    cfpSubmissionService.updateStatus(created.id(), CfpSubmissionStatus.ACCEPTED, "admin", "approved");
    cfpEventConfigService.publishResults(
        CFP_EVENT_ID,
        "admin@example.org",
        "Welcome to the agenda",
        "Thank you for submitting.");
    VolunteerApplication volunteerCreated =
        volunteerApplicationService.create(
            "public.user@example.com",
            "Public User",
            new VolunteerApplicationService.CreateRequest(
                VOLUNTEER_EVENT_ID,
                "Experienced on-site helper for technology meetups.",
                "I want to support attendees and speaker logistics.",
                "Strong coordination and communication."));
    volunteerApplicationService.updateStatus(
        volunteerCreated.id(),
        VolunteerApplicationStatus.SELECTED,
        "admin",
        "selected",
        volunteerCreated.updatedAt());

    challengeService.recordActivity("public.user@example.com", GamificationActivity.COMMUNITY_VOTE);
    challengeService.recordActivity("public.user@example.com", GamificationActivity.COMMUNITY_VOTE);
    challengeService.recordActivity("public.user@example.com", GamificationActivity.COMMUNITY_VOTE);
    challengeService.recordActivity(
        "public.user@example.com", GamificationActivity.COMMUNITY_BOARD_MEMBERS_VIEW);
    challengeService.recordActivity("public.user@example.com", GamificationActivity.BOARD_PROFILE_OPEN);
  }

  @Test
  void githubProfileShowsUnifiedAccounts() {
    given()
        .header("Accept-Language", "en")
        .when()
        .get("/u/public-user")
        .then()
        .statusCode(200)
        .body(containsString("Public User"))
        .body(containsString("@public-user"))
        .body(containsString("public_user#1001"))
        .body(containsString("Connected accounts"))
        .body(containsString("CFP track record"))
        .body(containsString("Public CFP Talk"))
        .body(containsString("Public CFP Event"))
        .body(containsString("Volunteer track record"))
        .body(containsString("Public Volunteer Event"))
        .body(containsString("Completed challenges"))
        .body(containsString("Community Scout"))
        .body(containsString("Open challenge card"))
        .body(containsString("Class progression"))
        .body(containsString("Activities completed"))
        .body(containsString("Scientist"));
  }

  @Test
  void completedChallengeHasPublicShareCard() {
    given()
        .header("Accept-Language", "en")
        .when()
        .get("/u/public-user/challenges/community-scout")
        .then()
        .statusCode(200)
        .body(containsString("Community Scout"))
        .body(containsString("Verified challenge completion"))
        .body(containsString("Copy public link"))
        .body(containsString("Share on LinkedIn"))
        .body(containsString("Public User"));
  }

  @Test
  void incompleteChallengeShareCardIsNotPublic() {
    given()
        .header("Accept-Language", "en")
        .when()
        .get("/u/public-user/challenges/event-explorer")
        .then()
        .statusCode(404);
  }

  @Test
  void homedirProfileWithoutGithubIsResolvable() {
    String homedirId = homedirMemberId("homedir.user@example.com");
    given()
        .header("Accept-Language", "en")
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
    given().header("Accept-Language", "en").when().get("/u/does-not-exist").then().statusCode(404);
  }

  @Test
  void reputationSummaryIsHiddenWhenFlagIsDisabled() {
    given()
        .header("Accept-Language", "en")
        .when()
        .get("/u/public-user")
        .then()
        .statusCode(200)
        .body(not(containsString("Reputation snapshot")));
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
