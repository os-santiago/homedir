package com.scanales.eventflow.service;

import com.scanales.eventflow.model.Quest;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import io.quarkus.test.InjectMock;
import static org.mockito.Mockito.when;
import com.scanales.eventflow.model.UserProfile;
import com.scanales.eventflow.model.QuestProfile;

@QuarkusTest
public class QuestServiceTest {

    @Inject
    QuestService questService;

    @InjectMock
    UserProfileService userProfileService;

    @Test
    public void testGetQuestBoard_YamlLoading() {
        // This test assumes initial-quests.yaml is present in src/main/resources
        List<Quest> quests = questService.getQuestBoard();
        Assertions.assertNotNull(quests);
        Assertions.assertFalse(quests.isEmpty(), "Quest board should not be empty (should load from YAML)");

        Quest q1 = quests.get(0);
        Assertions.assertNotNull(q1.xpReward());
        Assertions.assertNotNull(q1.title());
    }

    @Test
    public void testGetProfile_Persistence() {
        String userId = "testuser";
        UserProfile mockProfile = new UserProfile();
        mockProfile.setUserId(userId);
        mockProfile.setCurrentXp(150); // Level 2 starts at 100
        mockProfile.addHistoryItem(
                new com.scanales.eventflow.model.UserProfile.QuestHistoryItem("Quest 1", 100, "2023-01-01"));

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
