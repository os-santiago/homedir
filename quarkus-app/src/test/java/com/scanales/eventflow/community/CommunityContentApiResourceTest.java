package com.scanales.eventflow.community;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class CommunityContentApiResourceTest {

  @Inject CommunityContentService contentService;
  @Inject CommunityVoteService voteService;
  @Inject CommunityFeaturedSnapshotService featuredSnapshotService;

  @BeforeEach
  void setup() throws Exception {
    voteService.clearAllForTests();
    Path dir = Path.of(System.getProperty("homedir.data.dir"), "community", "content");
    if (Files.exists(dir)) {
      try (var walk = Files.walk(dir)) {
        walk.sorted(Comparator.reverseOrder()).forEach(path -> {
          if (!path.equals(dir)) {
            try {
              Files.deleteIfExists(path);
            } catch (Exception ignored) {
            }
          }
        });
      }
    }
    Files.createDirectories(dir);
    Files.writeString(
        dir.resolve("20260207-java-item-1.yml"),
        """
        id: java-item-1
        title: "Java y Quarkus"
        url: "https://example.org/java-quarkus"
        summary: "Resumen de ejemplo para Quarkus."
        source: "example.org"
        thumbnail_url: "https://cdn.example.org/java-cover.png"
        created_at: "2026-02-07T10:00:00Z"
        media_type: "article_blog"
        tags: ["java","quarkus"]
        """);
    Files.writeString(
        dir.resolve("20260206-devops-item-2.yml"),
        """
        id: devops-item-2
        title: "DevOps baseline"
        url: "https://example.org/devops"
        summary: "Resumen devops."
        source: "example.org"
        created_at: "2026-02-06T10:00:00Z"
        media_type: "podcast"
        """);
    Files.writeString(
        dir.resolve("20260205-member-item-3.yml"),
        """
        id: submission-member-item-3
        title: "Aporte de comunidad"
        url: "https://community.example.org/post"
        summary: "Resumen desde miembros."
        source: "Community member"
        created_at: "2026-02-05T10:00:00Z"
        media_type: "video_story"
        tags: ["community"]
        """);
    contentService.refreshNowForTests();
    featuredSnapshotService.refreshNowForTests();
  }

  @Test
  void listsCuratedContent() {
    given()
        .accept("application/json")
        .when()
        .get("/api/community/content?view=new&limit=10&offset=0")
        .then()
        .statusCode(200)
        .body("view", equalTo("new"))
        .body("filter", equalTo("all"))
        .body("media", equalTo("all"))
        .body("total", greaterThanOrEqualTo(3))
        .body("items[0].id", equalTo("java-item-1"))
        .body("items[0].thumbnail_url", equalTo("https://cdn.example.org/java-cover.png"));
  }

  @Test
  void listIgnoresLargeLimitAndAlwaysReturnsTenPerPage() {
    given()
        .accept("application/json")
        .when()
        .get("/api/community/content?view=new&limit=50&offset=0")
        .then()
        .statusCode(200)
        .body("limit", equalTo(10));
  }

  @Test
  void filterMembersReturnsOnlyMemberContent() {
    given()
        .accept("application/json")
        .when()
        .get("/api/community/content?view=new&filter=members&limit=10&offset=0")
        .then()
        .statusCode(200)
        .body("filter", equalTo("members"))
        .body("total", equalTo(1))
        .body("items[0].id", equalTo("submission-member-item-3"))
        .body("items[0].origin", equalTo("members"));
  }

  @Test
  void filterInternetExcludesMemberSubmissions() {
    given()
        .accept("application/json")
        .when()
        .get("/api/community/content?view=new&filter=internet&limit=10&offset=0")
        .then()
        .statusCode(200)
        .body("filter", equalTo("internet"))
        .body("total", equalTo(2))
        .body("items[0].origin", equalTo("internet"));
  }

  @Test
  void mediaFilterReturnsOnlyVideoStory() {
    given()
        .accept("application/json")
        .when()
        .get("/api/community/content?view=new&media=video_story&limit=10&offset=0")
        .then()
        .statusCode(200)
        .body("media", equalTo("video_story"))
        .body("total", equalTo(1))
        .body("items[0].id", equalTo("submission-member-item-3"))
        .body("items[0].media_type", equalTo("video_story"));
  }

  @Test
  void voteRequiresAuthentication() {
    given()
        .contentType("application/json")
        .body("{\"vote\":\"recommended\"}")
        .when()
        .put("/api/community/content/java-item-1/vote")
        .then()
        .statusCode(401);
  }

  @Test
  @TestSecurity(user = "user@example.com")
  void voteUpsertReflectsInAggregatesAndMyVote() {
    given()
        .contentType("application/json")
        .body("{\"vote\":\"recommended\"}")
        .when()
        .put("/api/community/content/java-item-1/vote")
        .then()
        .statusCode(200)
        .body("item.vote_counts.recommended", equalTo(1))
        .body("item.my_vote", equalTo("recommended"));

    given()
        .contentType("application/json")
        .body("{\"vote\":\"must_see\"}")
        .when()
        .put("/api/community/content/java-item-1/vote")
        .then()
        .statusCode(200)
        .body("item.vote_counts.recommended", equalTo(0))
        .body("item.vote_counts.must_see", equalTo(1))
        .body("item.my_vote", equalTo("must_see"));
  }

  @Test
  @TestSecurity(user = "user@example.com")
  void featuredListRefreshesImmediatelyAfterVote() {
    given()
        .accept("application/json")
        .when()
        .get("/api/community/content?view=featured&limit=10&offset=0")
        .then()
        .statusCode(200)
        .body("items.find { it.id == 'java-item-1' }.vote_counts.recommended", equalTo(0));

    given()
        .contentType("application/json")
        .body("{\"vote\":\"recommended\"}")
        .when()
        .put("/api/community/content/java-item-1/vote")
        .then()
        .statusCode(200)
        .body("item.vote_counts.recommended", equalTo(1));

    given()
        .accept("application/json")
        .when()
        .get("/api/community/content?view=featured&limit=10&offset=0")
        .then()
        .statusCode(200)
        .body("items.find { it.id == 'java-item-1' }.vote_counts.recommended", equalTo(1))
        .body("items.find { it.id == 'java-item-1' }.my_vote", equalTo("recommended"));
  }
}
