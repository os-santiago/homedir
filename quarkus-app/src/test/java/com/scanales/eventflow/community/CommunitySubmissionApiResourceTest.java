package com.scanales.eventflow.community;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scanales.eventflow.service.PersistenceService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class CommunitySubmissionApiResourceTest {

  @Inject CommunitySubmissionService submissionService;
  @Inject CommunityContentService contentService;
  @Inject PersistenceService persistenceService;

  @BeforeEach
  void setup() throws Exception {
    submissionService.clearAllForTests();
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
    contentService.refreshNowForTests();
  }

  @Test
  void createRequiresAuthentication() {
    given()
        .contentType("application/json")
        .body(
            """
            {
              "title":"Item de prueba",
              "url":"https://example.org/post",
              "summary":"Resumen de prueba"
            }
            """)
        .when()
        .post("/api/community/submissions")
        .then()
        .statusCode(401);
  }

  @Test
  @TestSecurity(user = "user@example.com")
  void authenticatedUserCanCreateAndListMine() {
    given()
        .contentType("application/json")
        .body(
            """
            {
              "title":"Item de prueba",
              "url":"https://example.org/post",
              "summary":"Resumen de prueba",
              "source":"Example",
              "tags":["ai","opensource"]
            }
            """)
        .when()
        .post("/api/community/submissions")
        .then()
        .statusCode(201)
        .body("item.status", equalTo("pending"))
        .body("item.title", equalTo("Item de prueba"));

    given()
        .accept("application/json")
        .when()
        .get("/api/community/submissions/mine?limit=10&offset=0")
        .then()
        .statusCode(200)
        .body("items", hasSize(greaterThanOrEqualTo(1)))
        .body("items[0].status", equalTo("pending"));
  }

  @Test
  @TestSecurity(user = "user@example.com")
  void createRejectsDuplicateWhenResourceAlreadyCurated() throws Exception {
    Path dir = contentDir();
    Files.writeString(
        dir.resolve("20260208-curated-item.yml"),
        """
        id: curated-item-1
        title: "Already curated"
        url: "https://example.org/article"
        summary: "Already in feed."
        source: "example.org"
        created_at: "2026-02-08T10:00:00Z"
        """);
    contentService.refreshNowForTests();

    given()
        .contentType("application/json")
        .body(
            """
            {
              "title":"Duplicado",
              "url":"https://example.org/article?utm_source=twitter",
              "summary":"No debería entrar por duplicado"
            }
            """)
        .when()
        .post("/api/community/submissions")
        .then()
        .statusCode(409)
        .body("error", equalTo("duplicate_url_submission"));
  }

  @Test
  @TestSecurity(user = "user@example.com")
  void pendingListRequiresAdmin() {
    submissionService.create(
        "member@example.com",
        "Member",
        new CommunitySubmissionService.CreateRequest(
            "Recurso",
            "https://example.org/resource",
            "Resumen",
            "Example",
            List.of("dev")));

    given()
        .accept("application/json")
        .when()
        .get("/api/community/submissions/pending?limit=10&offset=0")
        .then()
        .statusCode(403)
        .body("error", equalTo("admin_required"));
  }

  @Test
  @TestSecurity(user = "admin@example.org")
  void adminApproveRejectsDuplicateWhenCuratedAppearsAfterSubmission() throws Exception {
    CommunitySubmission created =
        submissionService.create(
            "member@example.com",
            "Member",
            new CommunitySubmissionService.CreateRequest(
                "Artículo candidato",
                "https://example.org/deep-dive",
                "Resumen",
                "Example",
                List.of("java")));

    Path dir = contentDir();
    Files.writeString(
        dir.resolve("20260208-existing.yml"),
        """
        id: curated-existing
        title: "Existing"
        url: "https://example.org/deep-dive?utm_campaign=weekly"
        summary: "Already published."
        source: "example.org"
        created_at: "2026-02-08T12:00:00Z"
        """);
    contentService.refreshNowForTests();

    given()
        .contentType("application/json")
        .body("{\"note\":\"dup\"}")
        .when()
        .put("/api/community/submissions/" + created.id() + "/approve")
        .then()
        .statusCode(409)
        .body("error", equalTo("duplicate_url_submission"));
  }

  @Test
  @TestSecurity(user = "admin@example.org")
  void adminCanApproveAndContentAppearsInCommunityFeed() throws Exception {
    CommunitySubmission created =
        submissionService.create(
            "member@example.com",
            "Member",
            new CommunitySubmissionService.CreateRequest(
                "Kubernetes para equipos plataforma",
                "https://example.org/k8s-platform",
                "Resumen de aporte comunitario.",
                "Example",
                List.of("kubernetes", "platform")));

    String contentId =
        given()
            .contentType("application/json")
            .body("{\"note\":\"ok\"}")
            .when()
            .put("/api/community/submissions/" + created.id() + "/approve")
            .then()
            .statusCode(200)
            .body("item.status", equalTo("approved"))
            .extract()
            .path("item.content_id");

    given()
        .accept("application/json")
        .when()
        .get("/api/community/submissions/pending?limit=10&offset=0")
        .then()
        .statusCode(200)
        .body("items", hasSize(0));

    CommunitySubmission persisted = persistenceService.loadCommunitySubmissions().get(created.id());
    assertNotNull(persisted);
    assertTrue(persisted.status() == CommunitySubmissionStatus.APPROVED);

    assertTrue(waitForContent(contentId, Duration.ofSeconds(5)));
  }

  private boolean waitForContent(String contentId, Duration timeout) throws Exception {
    Instant deadline = Instant.now().plus(timeout);
    while (Instant.now().isBefore(deadline)) {
      List<String> ids =
          given()
              .accept("application/json")
              .when()
              .get("/api/community/content?view=new&limit=30&offset=0")
              .then()
              .statusCode(200)
              .extract()
              .path("items.id");
      if (ids != null && ids.contains(contentId)) {
        return true;
      }
      Thread.sleep(80);
    }
    return false;
  }

  private Path contentDir() {
    return Path.of(System.getProperty("homedir.data.dir"), "community", "content");
  }
}
