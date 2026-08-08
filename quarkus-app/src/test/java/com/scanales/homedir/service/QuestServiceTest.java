package com.scanales.homedir.service;

import static org.mockito.Mockito.when;

import com.scanales.homedir.model.Quest;
import com.scanales.homedir.model.QuestProfile;
import com.scanales.homedir.model.UserProfile;
import com.scanales.homedir.model.UserSession;
import io.quarkus.qute.Engine;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class QuestServiceTest {

  @Inject QuestService questService;

  @Inject Engine engine;

  @InjectMock UserProfileService userProfileService;

  @Test
  public void testGetQuestBoard_YamlLoading() {
    // This test assumes initial-quests.yaml is present in src/main/resources
    List<Quest> quests = questService.getQuestBoard();
    Assertions.assertNotNull(quests);
    Assertions.assertEquals(4, quests.size(), "Should have exactly 4 quests seeded");

    // Quest 1
    Quest q1 = quests.stream().filter(q -> q.id().equals("q-001")).findFirst().orElseThrow();
    Assertions.assertEquals("Enlista tu Refugio", q1.title());
    Assertions.assertEquals(
        "Crea un fork del repositorio 'homedir' o registra tu propio repositorio de configuración en el directorio de la comunidad.",
        q1.description());
    Assertions.assertEquals("List Your Shelter", q1.titleEn());
    Assertions.assertEquals(
        "Fork the 'homedir' repository or register your own configuration repository in the community directory.",
        q1.descriptionEn());
    Assertions.assertEquals(100, q1.xpReward());

    // Quest 2
    Quest q2 = quests.stream().filter(q -> q.id().equals("q-002")).findFirst().orElseThrow();
    Assertions.assertEquals("Primer Contacto", q2.title());
    Assertions.assertEquals(
        "Únete a la comunidad vinculando tu cuenta de GitHub en tu perfil de HomeDir.",
        q2.description());
    Assertions.assertEquals("First Contact", q2.titleEn());
    Assertions.assertEquals(
        "Join the community by linking your GitHub account in your HomeDir profile.",
        q2.descriptionEn());
    Assertions.assertEquals(50, q2.xpReward());

    // Quest 3
    Quest q3 = quests.stream().filter(q -> q.id().equals("q-003")).findFirst().orElseThrow();
    Assertions.assertEquals("Reporta una Anomalía", q3.title());
    Assertions.assertEquals(
        "Encuentra un bug o sugiere una mejora creando un Issue en el repositorio oficial.",
        q3.description());
    Assertions.assertEquals("Report an Anomaly", q3.titleEn());
    Assertions.assertEquals(
        "Find a bug or suggest an improvement by creating an Issue in the official repository.",
        q3.descriptionEn());
    Assertions.assertEquals(150, q3.xpReward());

    // Quest 4
    Quest q4 = quests.stream().filter(q -> q.id().equals("q-004")).findFirst().orElseThrow();
    Assertions.assertEquals("Contribución de Código", q4.title());
    Assertions.assertEquals(
        "Envía un Pull Request (PR) al repositorio. Puede ser documentación, fix o feature.",
        q4.description());
    Assertions.assertEquals("Code Contribution", q4.titleEn());
    Assertions.assertEquals(
        "Submit a Pull Request (PR) to the repository. It can be documentation, a fix or a feature.",
        q4.descriptionEn());
    Assertions.assertEquals(300, q4.xpReward());
  }

  @Test
  public void testQuestTemplateEnglishFallbackToSpanish() {
    // Quest with null English title
    Quest qNullTitle =
        new Quest(
            "q-test-1",
            "Spanish Title Test",
            "Spanish Description Test",
            10,
            "EASY",
            "OPEN",
            "/test",
            List.of(),
            List.of(),
            false,
            null,
            "English Description Test");

    // Quest with blank English title
    Quest qBlankTitle =
        new Quest(
            "q-test-2",
            "Spanish Title Test 2",
            "Spanish Description Test 2",
            10,
            "EASY",
            "OPEN",
            "/test",
            List.of(),
            List.of(),
            false,
            "   ",
            "English Description Test 2");

    // Quest with null English description
    Quest qNullDesc =
        new Quest(
            "q-test-3",
            "Spanish Title Test 3",
            "Spanish Description Test 3",
            10,
            "EASY",
            "OPEN",
            "/test",
            List.of(),
            List.of(),
            false,
            "English Title Test 3",
            null);

    // Quest with blank English description
    Quest qBlankDesc =
        new Quest(
            "q-test-4",
            "Spanish Title Test 4",
            "Spanish Description Test 4",
            10,
            "EASY",
            "OPEN",
            "/test",
            List.of(),
            List.of(),
            false,
            "English Title Test 4",
            " \t ");

    List<Quest> testQuests = List.of(qNullTitle, qBlankTitle, qNullDesc, qBlankDesc);

    String html =
        engine
            .getTemplate("QuestBoardResource/quests")
            .data("quests", testQuests)
            .data("filter", "all")
            .data("userSession", UserSession.anonymous())
            .data("resolvedLocaleCode", "en")
            .data("locale", java.util.Locale.ENGLISH)
            .data("activePage", "quests")
            .data("userAuthenticated", false)
            .data("userName", null)
            .data("userInitial", null)
            .render();

    Assertions.assertNotNull(html);

    // Test qNullTitle: should fall back to "Spanish Title Test", but use "English Description Test"
    Assertions.assertTrue(
        html.contains("Spanish Title Test"),
        "Should fall back to Spanish title when English title is null");
    Assertions.assertTrue(
        html.contains("English Description Test"), "Should use English description when not null");

    // Test qBlankTitle: should fall back to "Spanish Title Test 2", but use "English Description
    // Test 2"
    Assertions.assertTrue(
        html.contains("Spanish Title Test 2"),
        "Should fall back to Spanish title when English title is blank");
    Assertions.assertTrue(
        html.contains("English Description Test 2"),
        "Should use English description when not blank");

    // Test qNullDesc: should fall back to "Spanish Description Test 3", but use "English Title Test
    // 3"
    Assertions.assertTrue(
        html.contains("Spanish Description Test 3"),
        "Should fall back to Spanish description when English description is null");
    Assertions.assertTrue(
        html.contains("English Title Test 3"), "Should use English title when not null");

    // Test qBlankDesc: should fall back to "Spanish Description Test 4", but use "English Title
    // Test 4"
    Assertions.assertTrue(
        html.contains("Spanish Description Test 4"),
        "Should fall back to Spanish description when English description is blank");
    Assertions.assertTrue(
        html.contains("English Title Test 4"), "Should use English title when not blank");
  }

  @Test
  public void testGetProfile_Persistence() {
    String userId = "testuser";
    UserProfile mockProfile = new UserProfile();
    mockProfile.setUserId(userId);
    mockProfile.setCurrentXp(150); // Level 2 starts at 100
    mockProfile.addHistoryItem(
        new com.scanales.homedir.model.UserProfile.QuestHistoryItem("Quest 1", 100, "2023-01-01"));

    when(userProfileService.find(userId)).thenReturn(Optional.of(mockProfile));

    QuestProfile result = questService.getProfile(userId);

    Assertions.assertNotNull(result);
    Assertions.assertEquals(userId, result.username);
    Assertions.assertEquals(150, result.currentXp);
    Assertions.assertEquals(2, result.level); // Should be level 2
    Assertions.assertEquals(1, result.history.size());
    Assertions.assertEquals("Quest 1", result.history.get(0).title);
  }
}
