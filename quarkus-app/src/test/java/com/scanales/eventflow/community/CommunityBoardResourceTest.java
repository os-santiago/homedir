package com.scanales.eventflow.community;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import com.scanales.eventflow.model.UserProfile;
import com.scanales.eventflow.service.UserProfileService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class CommunityBoardResourceTest {

  @Inject UserProfileService userProfileService;
  @Inject CommunityBoardService boardService;

  @BeforeEach
  void setup() throws Exception {
    userProfileService.linkGithub(
        "board.user@example.com",
        "Board User",
        "board.user@example.com",
        new UserProfile.GithubAccount(
            "board-user",
            "https://github.com/board-user",
            "https://avatars.githubusercontent.com/u/12345",
            "12345",
            Instant.parse("2026-02-01T00:00:00Z")));
    userProfileService.upsert("board.local@example.com", "Board Local", "board.local@example.com");

    Path discordFile = Path.of(System.getProperty("homedir.data.dir"), "community", "board", "discord-users.yml");
    Files.createDirectories(discordFile.getParent());
    Files.writeString(
        discordFile,
        """
        members:
          - id: discord-001
            display_name: Discord User
            handle: discord_user#1001
            joined_at: "2026-01-10T00:00:00Z"
        """);
    boardService.resetDiscordCacheForTests();
  }

  @Test
  void boardSummaryPageRenders() {
    given()
        .when()
        .get("/comunidad/board")
        .then()
        .statusCode(200)
        .body(containsString("Community Board"))
        .body(containsString("HomeDir users"))
        .body(containsString("GitHub users"))
        .body(containsString("Discord users"))
        .body(containsString("Listed profiles: 1"));
  }

  @Test
  void boardDetailPageRendersMembers() {
    given()
        .when()
        .get("/comunidad/board/github-users")
        .then()
        .statusCode(200)
        .body(containsString("board-user"))
        .body(containsString("/u/board-user"))
        .body(containsString("Copy profile link"));
  }

  @Test
  void homedirMembersWithoutGithubUseUnifiedPublicProfilePath() {
    given()
        .when()
        .get("/comunidad/board/homedir-users")
        .then()
        .statusCode(200)
        .body(containsString("/u/hd-"));
  }

  @Test
  void boardDetailPagePaginatesInBlocksOfTen() throws Exception {
    Path discordFile = Path.of(System.getProperty("homedir.data.dir"), "community", "board", "discord-users.yml");
    StringBuilder yaml = new StringBuilder("members:\n");
    for (int i = 1; i <= 12; i++) {
      yaml.append("  - id: discord-").append(String.format("%03d", i)).append('\n');
      yaml.append("    display_name: Discord User ").append(i).append('\n');
      yaml.append("    handle: discord_user_").append(i).append("#1001\n");
      yaml.append("    joined_at: \"2026-01-10T00:00:00Z\"\n");
    }
    Files.writeString(discordFile, yaml.toString());
    boardService.resetDiscordCacheForTests();

    given()
        .when()
        .get("/comunidad/board/discord-users")
        .then()
        .statusCode(200)
        .body(containsString("Showing 1 to 10 of 12"))
        .body(containsString("limit=10"))
        .body(containsString("offset=10"))
        .body(containsString("Next"));
  }

  @Test
  void boardDetailIgnoresLargeLimitAndKeepsTenPerPage() throws Exception {
    Path discordFile = Path.of(System.getProperty("homedir.data.dir"), "community", "board", "discord-users.yml");
    StringBuilder yaml = new StringBuilder("members:\n");
    for (int i = 1; i <= 12; i++) {
      yaml.append("  - id: discord-").append(String.format("%03d", i)).append('\n');
      yaml.append("    display_name: Discord User ").append(i).append('\n');
      yaml.append("    handle: discord_user_").append(i).append("#1001\n");
      yaml.append("    joined_at: \"2026-01-10T00:00:00Z\"\n");
    }
    Files.writeString(discordFile, yaml.toString());
    boardService.resetDiscordCacheForTests();

    given()
        .when()
        .get("/comunidad/board/discord-users?limit=200&offset=0")
        .then()
        .statusCode(200)
        .body(containsString("Showing 1 to 10 of 12"))
        .body(containsString("limit=10"))
        .body(containsString("offset=10"));
  }

  @Test
  void englishCommunityAliasRedirectsToLocalizedBoardPath() {
    given()
        .redirects()
        .follow(false)
        .when()
        .get("/community/board")
        .then()
        .statusCode(303)
        .header("Location", containsString("/comunidad/board"));
  }

  @Test
  void memberSharePageRenders() {
    given()
        .redirects()
        .follow(false)
        .when()
        .get("/community/member/github-users/board-user")
        .then()
        .statusCode(303)
        .header("Location", containsString("/u/board-user"));
  }

  @Test
  void spanishMemberAliasRedirectsToCanonicalSharePath() {
    given()
        .redirects()
        .follow(false)
        .when()
        .get("/comunidad/member/github-users/board-user")
        .then()
        .statusCode(303)
        .header("Location", containsString("/community/member/github-users/board-user"));
  }

  @Test
  void unknownMemberSharePageReturnsNotFound() {
    given().when().get("/community/member/github-users/does-not-exist").then().statusCode(404);
  }

  @Test
  void unclaimedDiscordMemberRedirectsToBoardHighlight() throws Exception {
    Path discordFile = Path.of(System.getProperty("homedir.data.dir"), "community", "board", "discord-users.yml");
    Files.writeString(
        discordFile,
        """
        members:
          - id: discord-unclaimed-001
            display_name: Unclaimed Discord User
            handle: unclaimed#1001
            joined_at: "2026-01-10T00:00:00Z"
        """);
    boardService.resetDiscordCacheForTests();

    given()
        .redirects()
        .follow(false)
        .when()
        .get("/community/member/discord-users/discord-unclaimed-001")
        .then()
        .statusCode(303)
        .header("Location", containsString("/comunidad/board/discord-users?member=discord-unclaimed-001"));
  }

  @Test
  void claimedDiscordMemberUsesCanonicalProfileLink() {
    userProfileService.linkDiscord(
        "board.user@example.com",
        "Board User",
        "board.user@example.com",
        new UserProfile.DiscordAccount(
            "discord-001",
            "discord_user#1001",
            "https://discord.com/users/discord-001",
            null,
            Instant.parse("2026-02-18T00:00:00Z")));
    boardService.resetDiscordCacheForTests();

    given()
        .when()
        .get("/comunidad/board/discord-users")
        .then()
        .statusCode(200)
        .body(containsString("/u/board-user"));
  }

  @Test
  void claimedDiscordMemberSharePageUsesCanonicalProfileLink() {
    userProfileService.linkDiscord(
        "board.user@example.com",
        "Board User",
        "board.user@example.com",
        new UserProfile.DiscordAccount(
            "discord-001",
            "discord_user#1001",
            "https://discord.com/users/discord-001",
            null,
            Instant.parse("2026-02-18T00:00:00Z")));
    boardService.resetDiscordCacheForTests();

    given()
        .redirects()
        .follow(false)
        .when()
        .get("/community/member/discord-users/discord-001")
        .then()
        .statusCode(303)
        .header("Location", containsString("/u/board-user"));
  }
}
