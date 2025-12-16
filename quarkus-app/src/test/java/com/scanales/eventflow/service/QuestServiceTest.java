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
    public void testLabelParsing() {
        QuestService.GithubIssue issue = new QuestService.GithubIssue();
        issue.number = 1;
        issue.title = "Test Quest";
        issue.body = "Description";
        issue.html_url = "http://example.com";

        QuestService.GithubLabel l1 = new QuestService.GithubLabel();
        l1.name = "xp:200";
        QuestService.GithubLabel l2 = new QuestService.GithubLabel();
        l2.name = "difficulty:S";
        QuestService.GithubLabel l3 = new QuestService.GithubLabel();
        l3.name = "status:IN_PROGRESS";

        issue.labels = List.of(l1, l2, l3);

        List<Quest> quests = questService.mapIssuesToQuests(List.of(issue));

        Assertions.assertEquals(1, quests.size());
        Quest q = quests.get(0);
        Assertions.assertEquals(200, q.xpReward());
        Assertions.assertEquals("S", q.difficulty());
        Assertions.assertEquals("IN_PROGRESS", q.status());
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
