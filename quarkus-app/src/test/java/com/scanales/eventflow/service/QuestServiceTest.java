package com.scanales.eventflow.service;

import com.scanales.eventflow.model.Quest;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;

@QuarkusTest
public class QuestServiceTest {

    @Inject
    QuestService questService;

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
}
