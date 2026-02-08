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
        .body(containsString("Discord users"));
  }

  @Test
  void boardDetailPageRendersMembers() {
    given()
        .when()
        .get("/comunidad/board/github-users")
        .then()
        .statusCode(200)
        .body(containsString("board-user"))
        .body(containsString("Copy profile link"));
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
}
