package com.scanales.homedir.campaigns;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scanales.homedir.TestDataDir;
import com.scanales.homedir.model.Event;
import com.scanales.homedir.model.EventType;
import com.scanales.homedir.model.GamificationActivity;
import com.scanales.homedir.service.EventService;
import com.scanales.homedir.service.GamificationService;
import com.scanales.homedir.service.UsageMetricsService;
import com.scanales.homedir.service.UserProfileService;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(TestDataDir.class)
class CampaignServiceTest {

  @Inject CampaignService campaignService;
  @Inject UsageMetricsService usageMetricsService;
  @Inject EventService eventService;
  @Inject UserProfileService userProfileService;
  @Inject GamificationService gamificationService;

  @BeforeEach
  void setUp() {
    usageMetricsService.reset();
    eventService.reset();
  }

  @Test
  void refreshBuildsInternalDraftsFromLiveSignals() {
    Event event =
        new Event(
            "campaign-event",
            "Campaign Event",
            "Launch touchpoint",
            1,
            LocalDateTime.now(),
            "admin@example.com");
    event.setDate(LocalDate.now().plusDays(10));
    event.setType(EventType.CONFERENCE);
    eventService.saveEvent(event);

    String userId = "campaigns@example.com";
    userProfileService.upsert(userId, "Campaign User", userId);
    gamificationService.award(userId, GamificationActivity.FIRST_LOGIN_BONUS, "login");
    gamificationService.award(userId, GamificationActivity.GITHUB_LINKED, "github");
    gamificationService.award(userId, GamificationActivity.DISCORD_LINKED, "discord");
    usageMetricsService.recordFunnelStep("challenge_completed");

    CampaignStateSnapshot snapshot = campaignService.refreshDrafts();
    CampaignService.CampaignPreviewSnapshot preview = campaignService.preview("es");

    assertFalse(snapshot.drafts().isEmpty());
    assertTrue(snapshot.drafts().stream().anyMatch(item -> "product_pulse".equals(item.kind())));
    assertTrue(snapshot.drafts().stream().anyMatch(item -> "event_spotlight".equals(item.kind())));
    assertTrue(preview.drafts().stream().anyMatch(item -> item.title().contains("HomeDir")));
  }
}
