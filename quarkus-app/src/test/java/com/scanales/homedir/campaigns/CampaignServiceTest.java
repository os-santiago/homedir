package com.scanales.homedir.campaigns;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.scanales.homedir.TestDataDir;
import com.scanales.homedir.model.Event;
import com.scanales.homedir.model.EventType;
import com.scanales.homedir.model.GamificationActivity;
import com.scanales.homedir.service.EventService;
import com.scanales.homedir.service.GamificationService;
import com.scanales.homedir.service.PersistenceService;
import com.scanales.homedir.service.UsageMetricsService;
import com.scanales.homedir.service.UserProfileService;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
  @Inject PersistenceService persistenceService;
  @InjectMock CampaignDiscordPublisherService discordPublisherService;
  @InjectMock CampaignBlueskyPublisherService blueskyPublisherService;
  @InjectMock CampaignMastodonPublisherService mastodonPublisherService;

  @BeforeEach
  void setUp() {
    usageMetricsService.reset();
    eventService.reset();
    campaignService.resetStateForTests();
    when(discordPublisherService.status())
        .thenReturn(
            new CampaignPublisherStatus("discord", false, true, false, false, Duration.ofMinutes(15)));
    when(discordPublisherService.effectiveMinInterval()).thenReturn(Duration.ofMinutes(15));
    when(blueskyPublisherService.status())
        .thenReturn(
            new CampaignPublisherStatus("bluesky", false, true, false, false, Duration.ofMinutes(15)));
    when(blueskyPublisherService.effectiveMinInterval()).thenReturn(Duration.ofMinutes(15));
    when(mastodonPublisherService.status())
        .thenReturn(
            new CampaignPublisherStatus("mastodon", false, true, false, false, Duration.ofMinutes(15)));
    when(mastodonPublisherService.effectiveMinInterval()).thenReturn(Duration.ofMinutes(15));
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
    usageMetricsService.recordPageView("/", "Mozilla/5.0");
    usageMetricsService.recordPageView("/comunidad", "Mozilla/5.0");
    usageMetricsService.recordPageView("/eventos", "Mozilla/5.0");

    CampaignStateSnapshot snapshot = campaignService.refreshDrafts();
    CampaignService.CampaignPreviewSnapshot preview = campaignService.preview("es");

    assertFalse(snapshot.drafts().isEmpty());
    assertTrue(snapshot.drafts().stream().anyMatch(item -> "product_pulse".equals(item.kind())));
    assertTrue(snapshot.drafts().stream().anyMatch(item -> "event_spotlight".equals(item.kind())));
    assertTrue(preview.drafts().stream().anyMatch(item -> item.title().contains("HomeDir")));
    assertFalse(preview.cadenceGuidance().overallWindows().isEmpty());
    assertTrue(preview.drafts().stream().anyMatch(item -> !item.recommendedWindowLabel().isBlank()));
    assertFalse(preview.previewPacks().isEmpty());
    assertTrue(
        preview.previewPacks().stream()
            .anyMatch(pack -> pack.channels().stream().anyMatch(channel -> "discord".equals(channel.channelCode()))));
    assertTrue(
        preview.previewPacks().stream()
            .anyMatch(pack -> pack.channels().stream().anyMatch(channel -> channel.landingUrl().contains("utm_source=campaigns"))));
  }

  @Test
  void attributionSummaryIncludesDraftRows() {
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

    campaignService.refreshDrafts();
    CampaignService.CampaignPreviewSnapshot preview = campaignService.preview("es");

    assertTrue(
        preview.attribution().stream()
            .anyMatch(row -> "product-pulse".equals(row.draftId()) && row.kindLabel() != null));
  }

  @Test
  void approvalAndScheduleSurviveDraftRefresh() {
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

    CampaignStateSnapshot initial = campaignService.refreshDrafts();
    CampaignDraftState target =
        initial.drafts().stream()
            .filter(item -> "event_spotlight".equals(item.kind()))
            .findFirst()
            .orElseThrow();

    campaignService.approveDraft(target.id(), "sergio.canales.e@gmail.com");
    CampaignStateSnapshot approved = campaignService.currentState();
    CampaignDraftState approvedDraft =
        approved.drafts().stream().filter(item -> item.id().equals(target.id())).findFirst().orElseThrow();
    assertTrue(approvedDraft.workflowState() == CampaignWorkflowState.APPROVED);
    assertNotNull(approvedDraft.approvedAt());

    LocalDateTime nextHour = LocalDateTime.now().plusHours(1).truncatedTo(ChronoUnit.MINUTES);
    campaignService.scheduleDraft(target.id(), nextHour, "sergio.canales.e@gmail.com");
    CampaignStateSnapshot refreshed = campaignService.refreshDrafts();
    CampaignDraftState scheduledDraft =
        refreshed.drafts().stream().filter(item -> item.id().equals(target.id())).findFirst().orElseThrow();

    assertTrue(scheduledDraft.workflowState() == CampaignWorkflowState.SCHEDULED);
    assertNotNull(scheduledDraft.scheduledFor());
    assertTrue("sergio.canales.e@gmail.com".equals(scheduledDraft.approvedBy()));
  }

  @Test
  void scheduledPublishMarksDraftAsPublishedWhenDiscordPublisherSucceeds() {
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

    CampaignStateSnapshot initial = campaignService.refreshDrafts();
    CampaignDraftState target =
        initial.drafts().stream()
            .filter(item -> "event_spotlight".equals(item.kind()))
            .findFirst()
            .orElseThrow();

    campaignService.approveDraft(target.id(), "sergio.canales.e@gmail.com");
    campaignService.scheduleDraft(target.id(), LocalDateTime.now().minusMinutes(1), "sergio.canales.e@gmail.com");
    when(discordPublisherService.status())
        .thenReturn(
            new CampaignPublisherStatus("discord", true, false, true, true, Duration.ofMinutes(15)));
    when(discordPublisherService.publish(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            CampaignPublishResult.published("discord", Instant.parse("2026-03-19T14:40:00Z"), "published"));

    CampaignStateSnapshot published = campaignService.publishScheduledNow();
    CampaignDraftState publishedDraft =
        published.drafts().stream().filter(item -> item.id().equals(target.id())).findFirst().orElseThrow();

    assertTrue(publishedDraft.workflowState() == CampaignWorkflowState.PUBLISHED);
    assertTrue(publishedDraft.publishedChannels().containsKey("discord"));
    assertTrue("published".equals(publishedDraft.lastPublishOutcome()));
  }

  @Test
  void publishedDraftCanContinuePublishingPendingChannels() {
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

    CampaignStateSnapshot initial = campaignService.refreshDrafts();
    CampaignDraftState target =
        initial.drafts().stream()
            .filter(item -> "event_spotlight".equals(item.kind()))
            .findFirst()
            .orElseThrow();

    campaignService.approveDraft(target.id(), "sergio.canales.e@gmail.com");
    campaignService.scheduleDraft(target.id(), LocalDateTime.now().minusMinutes(30), "sergio.canales.e@gmail.com");
    when(discordPublisherService.status())
        .thenReturn(
            new CampaignPublisherStatus("discord", true, false, true, true, Duration.ofMinutes(15)));
    when(discordPublisherService.publish(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            CampaignPublishResult.published("discord", Instant.parse("2026-03-19T14:40:00Z"), "published"));
    campaignService.publishScheduledNow();

    when(blueskyPublisherService.status())
        .thenReturn(
            new CampaignPublisherStatus("bluesky", true, false, true, true, Duration.ZERO));
    when(blueskyPublisherService.publish(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            CampaignPublishResult.published(
                "bluesky", Instant.parse("2026-03-19T14:45:00Z"), "published_bluesky"));

    CampaignStateSnapshot republished = campaignService.publishScheduledNow();
    CampaignDraftState republishedDraft =
        republished.drafts().stream().filter(item -> item.id().equals(target.id())).findFirst().orElseThrow();

    assertTrue(republishedDraft.publishedChannels().containsKey("discord"));
    assertTrue(republishedDraft.publishedChannels().containsKey("bluesky"));
    assertTrue("published_bluesky".equals(republishedDraft.lastPublishOutcome()));
  }

  @Test
  void manualLinkedinHandoffMarksDraftAsPublishedAndVisibleInPreview() {
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

    CampaignStateSnapshot initial = campaignService.refreshDrafts();
    CampaignDraftState target =
        initial.drafts().stream()
            .filter(item -> "event_spotlight".equals(item.kind()))
            .findFirst()
            .orElseThrow();

    campaignService.approveDraft(target.id(), "sergio.canales.e@gmail.com");
    CampaignStateSnapshot published = campaignService.markLinkedinPublished(target.id(), "sergio.canales.e@gmail.com");
    CampaignDraftState publishedDraft =
        published.drafts().stream().filter(item -> item.id().equals(target.id())).findFirst().orElseThrow();
    CampaignService.CampaignPreviewSnapshot preview = campaignService.preview("es");

    assertEquals(CampaignWorkflowState.PUBLISHED, publishedDraft.workflowState());
    assertTrue(publishedDraft.publishedChannels().containsKey("linkedin"));
    assertEquals("published_linkedin_manual", publishedDraft.lastPublishOutcome());
    assertTrue(
        preview.linkedinHandoffs().stream()
            .anyMatch(item -> item.draftId().equals(target.id()) && item.completed()));
    assertTrue(preview.summary().linkedinCompletedCount() >= 1);
  }

  @Test
  void queueHealthFlagsStaleAndOverdueDrafts() {
    Instant now = Instant.now();
    CampaignStateSnapshot snapshot =
        new CampaignStateSnapshot(
            CampaignStateSnapshot.SCHEMA_VERSION,
            now,
            java.util.List.of(
                new CampaignDraftState(
                    "draft-stale",
                    "product_pulse",
                    now.minus(3, ChronoUnit.DAYS),
                    java.util.Map.of("version", "3.498.0"),
                    java.util.List.of("discord", "linkedin"),
                    true,
                    CampaignWorkflowState.DRAFT,
                    null,
                    "",
                    null,
                    now.minus(2, ChronoUnit.DAYS),
                    true,
                    java.util.Map.of(),
                    null,
                    ""),
                new CampaignDraftState(
                    "approved-stale",
                    "community_spotlight",
                    now.minus(2, ChronoUnit.DAYS),
                    java.util.Map.of("title", "Community refresh", "source", "internet", "publishedAt", LocalDate.now().toString()),
                    java.util.List.of("discord"),
                    true,
                    CampaignWorkflowState.APPROVED,
                    now.minus(30, ChronoUnit.HOURS),
                    "sergio.canales.e@gmail.com",
                    null,
                    now.minus(30, ChronoUnit.HOURS),
                    true,
                    java.util.Map.of(),
                    null,
                    ""),
                new CampaignDraftState(
                    "scheduled-overdue",
                    "event_spotlight",
                    now.minus(2, ChronoUnit.DAYS),
                    java.util.Map.of(
                        "eventTitle", "DevOpsDays Santiago 2026",
                        "eventType", EventType.CONFERENCE.name(),
                        "eventDate", LocalDate.now().plusDays(10).toString(),
                        "eventUrl", "/event/devopsdays-santiago-2026"),
                    java.util.List.of("discord", "linkedin"),
                    true,
                    CampaignWorkflowState.SCHEDULED,
                    now.minus(3, ChronoUnit.HOURS),
                    "sergio.canales.e@gmail.com",
                    now.minus(2, ChronoUnit.HOURS),
                    now.minus(2, ChronoUnit.HOURS),
                    true,
                    java.util.Map.of(),
                    null,
                    "")));
    persistenceService.saveCampaignStateSync(snapshot);

    CampaignService.CampaignPreviewSnapshot preview = campaignService.preview("es");

    assertEquals("high", preview.queueHealth().statusCode());
    assertEquals(1, preview.queueHealth().staleDraftCount());
    assertEquals(1, preview.queueHealth().staleApprovedCount());
    assertEquals(1, preview.queueHealth().overdueScheduledCount());
    assertEquals(1, preview.queueHealth().blockedPublicationCount());
    assertFalse(preview.queueRisks().isEmpty());
    assertTrue(
        preview.queueRisks().stream()
            .anyMatch(item -> "scheduled-overdue".equals(item.draftId()) && "high".equals(item.badgeClass())));
  }
}
