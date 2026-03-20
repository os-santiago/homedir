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
  void businessDashboardIsExposedInPreview() {
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

    assertNotNull(preview.businessDashboard());
    assertNotNull(preview.businessDashboard().totalVisitsLabel());
    assertNotNull(preview.businessDashboard().averageVisitsLabel());
    assertNotNull(preview.businessDashboard().bestChannelLabel());
    assertNotNull(preview.businessDashboard().topDraftLabel());
  }

  @Test
  void rolloutChecklistReflectsEffectivePublisherReadiness() {
    when(discordPublisherService.status())
        .thenReturn(
            new CampaignPublisherStatus("discord", true, false, true, true, Duration.ofMinutes(15)));
    when(blueskyPublisherService.status())
        .thenReturn(
            new CampaignPublisherStatus("bluesky", true, true, true, true, Duration.ofMinutes(15)));
    when(mastodonPublisherService.status())
        .thenReturn(
            new CampaignPublisherStatus("mastodon", true, false, true, false, Duration.ofMinutes(15)));

    campaignService.setPublishAutomationEnabled(true, "admin@example.com");
    campaignService.setChannelAutomationEnabled("discord", true, "admin@example.com");
    campaignService.setChannelAutomationEnabled("bluesky", true, "admin@example.com");
    campaignService.setChannelAutomationEnabled("mastodon", true, "admin@example.com");
    campaignService.setPilotLiveChannel("discord", "admin@example.com");
    campaignService.setPilotLiveArmed(true, "admin@example.com");

    CampaignService.CampaignPreviewSnapshot preview = campaignService.preview("es");

    assertEquals(1, preview.rolloutChecklist().readyCount());
    assertEquals(2, preview.rolloutChecklist().blockedCount());
    assertEquals(1, preview.rolloutChecklist().dryRunCount());
    assertTrue(
        preview.rolloutChecklist().channels().stream()
            .anyMatch(item -> "discord".equals(item.channelCode()) && item.ready()));
  }

  @Test
  void rolloutChecklistTracksGoLiveAcknowledgements() {
    when(discordPublisherService.status())
        .thenReturn(
            new CampaignPublisherStatus("discord", true, false, true, true, Duration.ofMinutes(15)));
    campaignService.setPublishAutomationEnabled(true, "admin@example.com");
    campaignService.setChannelAutomationEnabled("discord", true, "admin@example.com");
    campaignService.setChannelGoLiveAcknowledged("discord", true, "admin@example.com");
    campaignService.setPilotLiveChannel("discord", "admin@example.com");
    campaignService.setPilotLiveArmed(true, "admin@example.com");

    CampaignService.CampaignPreviewSnapshot preview = campaignService.preview("es");

    assertEquals(1, preview.rolloutChecklist().acknowledgedCount());
    assertTrue(
        preview.rolloutChecklist().channels().stream()
            .anyMatch(
                item ->
                    "discord".equals(item.channelCode())
                        && item.acknowledged()
                        && "admin@example.com".equals(item.acknowledgedByLabel())));
  }

  @Test
  void pilotLiveChannelRestrictsAutomatedReadinessToSingleChannel() {
    when(discordPublisherService.status())
        .thenReturn(
            new CampaignPublisherStatus("discord", true, false, true, true, Duration.ofMinutes(15)));
    when(blueskyPublisherService.status())
        .thenReturn(
            new CampaignPublisherStatus("bluesky", true, false, true, true, Duration.ofMinutes(15)));
    campaignService.setPublishAutomationEnabled(true, "admin@example.com");
    campaignService.setChannelAutomationEnabled("discord", true, "admin@example.com");
    campaignService.setChannelAutomationEnabled("bluesky", true, "admin@example.com");
    campaignService.setPilotLiveChannel("discord", "admin@example.com");
    campaignService.setPilotLiveArmed(true, "admin@example.com");

    CampaignService.CampaignPreviewSnapshot preview = campaignService.preview("es");

    assertEquals("Discord", preview.rolloutChecklist().pilotChannelLabel());
    assertEquals("Armado", preview.rolloutChecklist().pilotActivationLabel());
    assertTrue(
        preview.rolloutChecklist().channels().stream()
            .anyMatch(
                item ->
                    "discord".equals(item.channelCode())
                        && item.pilotLive()
                        && item.liveArmed()
                        && item.ready()));
    assertTrue(
        preview.rolloutChecklist().channels().stream()
            .anyMatch(item -> "bluesky".equals(item.channelCode()) && !item.pilotLive() && !item.ready()));
  }

  @Test
  void pilotActivationRunbookHighlightsNextPendingGuardrail() {
    when(discordPublisherService.status())
        .thenReturn(
            new CampaignPublisherStatus("discord", true, false, true, true, Duration.ofMinutes(15)));
    campaignService.setPublishAutomationEnabled(true, "admin@example.com");
    campaignService.setChannelAutomationEnabled("discord", true, "admin@example.com");
    campaignService.setPilotLiveChannel("discord", "admin@example.com");

    CampaignService.CampaignPreviewSnapshot preview = campaignService.preview("es");

    assertEquals("En progreso", preview.pilotActivationRunbook().statusLabel());
    assertEquals("Discord", preview.pilotActivationRunbook().targetChannelLabel());
    assertEquals(4, preview.pilotActivationRunbook().completedCount());
    assertEquals(3, preview.pilotActivationRunbook().pendingCount());
    assertEquals(
        "Registra una confirmación explícita de go-live antes de la entrega real.",
        preview.pilotActivationRunbook().recommendationLabel());
    assertTrue(
        preview.pilotActivationRunbook().steps().stream()
            .anyMatch(
                step ->
                    "ack_go_live".equals(step.stepCode())
                        && "Pendiente".equals(step.statusLabel())
                        && !step.completed()));
    assertTrue(
        preview.pilotActivationRunbook().steps().stream()
            .anyMatch(
                step ->
                    "select_pilot".equals(step.stepCode())
                        && "Hecho".equals(step.statusLabel())
                        && step.completed()));
  }

  @Test
  void pilotVerificationSummaryTracksLivePublishAndAcknowledgement() {
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
    when(discordPublisherService.status())
        .thenReturn(
            new CampaignPublisherStatus("discord", true, false, true, true, Duration.ofMinutes(15)));
    campaignService.setPilotLiveChannel("discord", "sergio.canales.e@gmail.com");
    campaignService.setPilotLiveArmed(true, "sergio.canales.e@gmail.com");
    campaignService.scheduleDraft(target.id(), LocalDateTime.now().minusMinutes(1), "sergio.canales.e@gmail.com");
    Instant publishInstant = Instant.now().plusSeconds(1);
    when(discordPublisherService.publish(org.mockito.ArgumentMatchers.any()))
        .thenReturn(CampaignPublishResult.published("discord", publishInstant, "published"));
    campaignService.publishScheduledNow();

    CampaignService.CampaignPreviewSnapshot pendingPreview = campaignService.preview("es");
    assertEquals("Pendiente de verificación", pendingPreview.pilotVerificationSummary().statusLabel());
    assertEquals("Discord", pendingPreview.pilotVerificationSummary().targetChannelLabel());
    assertEquals(1, pendingPreview.pilotVerificationSummary().publishedCount());
    assertTrue(pendingPreview.pilotVerificationSummary().canAcknowledge());

    campaignService.setPilotVerificationAcknowledged(true, "sergio.canales.e@gmail.com");
    CampaignService.CampaignPreviewSnapshot verifiedPreview = campaignService.preview("es");

    assertEquals("Verificado", verifiedPreview.pilotVerificationSummary().statusLabel());
    assertEquals("Verificado", verifiedPreview.pilotVerificationSummary().verificationLabel());
    assertEquals("sergio.canales.e@gmail.com", verifiedPreview.pilotVerificationSummary().acknowledgedByLabel());
    assertFalse(verifiedPreview.pilotVerificationSummary().canAcknowledge());
    assertTrue(verifiedPreview.pilotVerificationSummary().acknowledged());
  }

  @Test
  void pilotDecisionSummaryRequiresVerificationAndTracksDecision() {
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
    when(discordPublisherService.status())
        .thenReturn(
            new CampaignPublisherStatus("discord", true, false, true, true, Duration.ofMinutes(15)));
    campaignService.setPilotLiveChannel("discord", "sergio.canales.e@gmail.com");
    campaignService.setPilotLiveArmed(true, "sergio.canales.e@gmail.com");
    campaignService.scheduleDraft(target.id(), LocalDateTime.now().minusMinutes(1), "sergio.canales.e@gmail.com");
    when(discordPublisherService.publish(org.mockito.ArgumentMatchers.any()))
        .thenReturn(CampaignPublishResult.published("discord", Instant.now().plusSeconds(1), "published"));
    campaignService.publishScheduledNow();

    CampaignService.CampaignPreviewSnapshot unverifiedPreview = campaignService.preview("es");
    assertEquals("Pendiente de decisión", unverifiedPreview.pilotDecisionSummary().statusLabel());
    assertEquals("Pendiente de verificación", unverifiedPreview.pilotDecisionSummary().verificationLabel());
    assertEquals("Sin decisión registrada", unverifiedPreview.pilotDecisionSummary().decisionLabel());
    assertFalse(unverifiedPreview.pilotDecisionSummary().canDecide());

    campaignService.setPilotVerificationAcknowledged(true, "sergio.canales.e@gmail.com");
    CampaignService.CampaignPreviewSnapshot pendingPreview = campaignService.preview("es");
    assertTrue(pendingPreview.pilotDecisionSummary().canDecide());
    assertEquals("Sin decisión registrada", pendingPreview.pilotDecisionSummary().decisionLabel());

    campaignService.setPilotDecision("approved", "sergio.canales.e@gmail.com");
    CampaignService.CampaignPreviewSnapshot approvedPreview = campaignService.preview("es");
    assertEquals("Aprobado", approvedPreview.pilotDecisionSummary().statusLabel());
    assertEquals("Aprobado", approvedPreview.pilotDecisionSummary().decisionLabel());
    assertEquals("sergio.canales.e@gmail.com", approvedPreview.pilotDecisionSummary().decidedByLabel());
    assertTrue(approvedPreview.pilotDecisionSummary().approved());
    assertTrue(approvedPreview.pilotDecisionSummary().canClear());

    campaignService.setPilotVerificationAcknowledged(false, "sergio.canales.e@gmail.com");
    CampaignService.CampaignPreviewSnapshot resetPreview = campaignService.preview("es");
    assertEquals("Sin decisión registrada", resetPreview.pilotDecisionSummary().decisionLabel());
    assertFalse(resetPreview.pilotDecisionSummary().hasDecision());
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
    when(discordPublisherService.status())
        .thenReturn(
            new CampaignPublisherStatus("discord", true, false, true, true, Duration.ofMinutes(15)));
    campaignService.setPilotLiveChannel("discord", "sergio.canales.e@gmail.com");
    campaignService.setPilotLiveArmed(true, "sergio.canales.e@gmail.com");
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
    when(discordPublisherService.status())
        .thenReturn(
            new CampaignPublisherStatus("discord", true, false, true, true, Duration.ofMinutes(15)));
    campaignService.setPilotLiveChannel("discord", "sergio.canales.e@gmail.com");
    campaignService.setPilotLiveArmed(true, "sergio.canales.e@gmail.com");
    campaignService.scheduleDraft(target.id(), LocalDateTime.now().minusMinutes(1), "sergio.canales.e@gmail.com");
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
    when(discordPublisherService.status())
        .thenReturn(
            new CampaignPublisherStatus("discord", true, false, true, true, Duration.ofMinutes(15)));
    campaignService.setPilotLiveChannel("discord", "sergio.canales.e@gmail.com");
    campaignService.setPilotLiveArmed(true, "sergio.canales.e@gmail.com");
    campaignService.scheduleDraft(target.id(), LocalDateTime.now().minusMinutes(30), "sergio.canales.e@gmail.com");
    when(discordPublisherService.publish(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            CampaignPublishResult.published("discord", Instant.parse("2026-03-19T14:40:00Z"), "published"));
    campaignService.publishScheduledNow();

    when(blueskyPublisherService.status())
        .thenReturn(
            new CampaignPublisherStatus("bluesky", true, false, true, true, Duration.ZERO));
    campaignService.setPilotLiveChannel("bluesky", "sergio.canales.e@gmail.com");
    campaignService.setPilotLiveArmed(true, "sergio.canales.e@gmail.com");
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
  void automationControlsPersistInPreviewAndCanDisableChannels() {
    campaignService.setRefreshAutomationEnabled(false, "sergio.canales.e@gmail.com");
    campaignService.setPublishAutomationEnabled(false, "sergio.canales.e@gmail.com");
    campaignService.setChannelAutomationEnabled("discord", false, "sergio.canales.e@gmail.com");

    CampaignService.CampaignPreviewSnapshot preview = campaignService.preview("es");

    assertFalse(preview.automation().refreshAutomationEnabled());
    assertFalse(preview.automation().publishAutomationEnabled());
    assertTrue(
        preview.automation().channels().stream()
            .anyMatch(
                item ->
                    "discord".equals(item.channelCode())
                        && !item.automationEnabled()
                        && !item.effectiveEnabled()));
  }

  @Test
  void manualPublishCanBypassGlobalPublishAutomationPause() {
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
    when(discordPublisherService.status())
        .thenReturn(
            new CampaignPublisherStatus("discord", true, false, true, true, Duration.ofMinutes(15)));
    campaignService.setPilotLiveChannel("discord", "sergio.canales.e@gmail.com");
    campaignService.setPilotLiveArmed(true, "sergio.canales.e@gmail.com");
    campaignService.scheduleDraft(target.id(), LocalDateTime.now().minusMinutes(1), "sergio.canales.e@gmail.com");
    campaignService.setPublishAutomationEnabled(false, "sergio.canales.e@gmail.com");
    when(discordPublisherService.publish(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            CampaignPublishResult.published("discord", Instant.parse("2026-03-19T14:40:00Z"), "published"));

    CampaignStateSnapshot published = campaignService.publishScheduledNow();
    CampaignDraftState publishedDraft =
        published.drafts().stream().filter(item -> item.id().equals(target.id())).findFirst().orElseThrow();

    assertEquals(CampaignWorkflowState.PUBLISHED, publishedDraft.workflowState());
    assertTrue(publishedDraft.publishedChannels().containsKey("discord"));
  }

  @Test
  void scheduleIsRejectedWhenNoAutomatedChannelIsReady() {
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

    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalStateException.class,
        () ->
            campaignService.scheduleDraft(
                target.id(),
                LocalDateTime.now().plusHours(1).truncatedTo(ChronoUnit.MINUTES),
                "sergio.canales.e@gmail.com"));
  }

  @Test
  void auditTrailCapturesWorkflowAndPublishingEvents() {
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
    when(discordPublisherService.status())
        .thenReturn(
            new CampaignPublisherStatus("discord", true, false, true, true, Duration.ofMinutes(15)));
    campaignService.setPilotLiveChannel("discord", "sergio.canales.e@gmail.com");
    campaignService.setPilotLiveArmed(true, "sergio.canales.e@gmail.com");
    campaignService.scheduleDraft(
        target.id(),
        LocalDateTime.now().minusMinutes(10),
        "sergio.canales.e@gmail.com");
    when(discordPublisherService.publish(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            CampaignPublishResult.published("discord", Instant.parse("2026-03-19T20:00:00Z"), "published"));

    campaignService.publishScheduledNow();
    CampaignService.CampaignPreviewSnapshot preview = campaignService.preview("es");

    assertFalse(preview.auditTrail().isEmpty());
    assertTrue(
        preview.auditTrail().stream()
            .anyMatch(item -> item.draftId().equals(target.id()) && item.eventLabel() != null));
    assertTrue(
        preview.auditTrail().stream()
            .anyMatch(item -> item.channelLabel().contains("Discord")));
  }

  @Test
  void manualChannelRetryPublishesPendingChannelWhenReady() {
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

    CampaignDraftState target =
        campaignService.refreshDrafts().drafts().stream()
            .filter(item -> "event_spotlight".equals(item.kind()))
            .findFirst()
            .orElseThrow();

    campaignService.approveDraft(target.id(), "sergio.canales.e@gmail.com");
    when(discordPublisherService.status())
        .thenReturn(
            new CampaignPublisherStatus("discord", true, false, true, true, Duration.ofMinutes(15)));
    campaignService.setPilotLiveChannel("discord", "sergio.canales.e@gmail.com");
    campaignService.setPilotLiveArmed(true, "sergio.canales.e@gmail.com");
    campaignService.scheduleDraft(target.id(), LocalDateTime.now().minusMinutes(10), "sergio.canales.e@gmail.com");
    when(discordPublisherService.publish(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            CampaignPublishResult.published("discord", Instant.parse("2026-03-19T20:00:00Z"), "published"));
    campaignService.publishScheduledNow();

    when(blueskyPublisherService.status())
        .thenReturn(
            new CampaignPublisherStatus("bluesky", true, false, true, true, Duration.ZERO));
    campaignService.setPilotLiveChannel("bluesky", "sergio.canales.e@gmail.com");
    campaignService.setPilotLiveArmed(true, "sergio.canales.e@gmail.com");
    when(blueskyPublisherService.publish(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            CampaignPublishResult.published(
                "bluesky", Instant.parse("2026-03-19T20:05:00Z"), "published_bluesky"));

    CampaignDraftState retried =
        campaignService.retryChannel(target.id(), "bluesky", "sergio.canales.e@gmail.com")
            .drafts().stream()
            .filter(item -> item.id().equals(target.id()))
            .findFirst()
            .orElseThrow();

    assertTrue(retried.publishedChannels().containsKey("bluesky"));
    assertEquals("published_bluesky", retried.lastPublishOutcome());
  }

  @Test
  void manualChannelRetryIsRejectedWhenChannelIsNotReady() {
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

    CampaignDraftState target =
        campaignService.refreshDrafts().drafts().stream()
            .filter(item -> "event_spotlight".equals(item.kind()))
            .findFirst()
            .orElseThrow();

    campaignService.approveDraft(target.id(), "sergio.canales.e@gmail.com");
    when(discordPublisherService.status())
        .thenReturn(
            new CampaignPublisherStatus("discord", true, false, true, true, Duration.ofMinutes(15)));
    campaignService.setPilotLiveChannel("discord", "sergio.canales.e@gmail.com");
    campaignService.setPilotLiveArmed(true, "sergio.canales.e@gmail.com");
    campaignService.scheduleDraft(target.id(), LocalDateTime.now().minusMinutes(10), "sergio.canales.e@gmail.com");

    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalStateException.class,
        () -> campaignService.retryChannel(target.id(), "bluesky", "sergio.canales.e@gmail.com"));
  }

  @Test
  void scheduleIsRejectedWhenPilotLiveIsNotArmed() {
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

    CampaignDraftState target =
        campaignService.refreshDrafts().drafts().stream()
            .filter(item -> "event_spotlight".equals(item.kind()))
            .findFirst()
            .orElseThrow();

    campaignService.approveDraft(target.id(), "sergio.canales.e@gmail.com");
    when(discordPublisherService.status())
        .thenReturn(
            new CampaignPublisherStatus("discord", true, false, true, true, Duration.ofMinutes(15)));
    campaignService.setPilotLiveChannel("discord", "sergio.canales.e@gmail.com");

    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalStateException.class,
        () ->
            campaignService.scheduleDraft(
                target.id(),
                LocalDateTime.now().plusHours(1).truncatedTo(ChronoUnit.MINUTES),
                "sergio.canales.e@gmail.com"));
  }

  @Test
  void previewCanFilterCampaignDraftsAndAuditTrail() {
    Event event =
        new Event(
            "campaign-event",
            "DevOpsDays Santiago 2026",
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

    CampaignService.CampaignPreviewSnapshot preview =
        campaignService.preview(
            "es",
            new CampaignService.CampaignAdminFilters(
                "DevOpsDays", "approved", "event_spotlight", "linkedin"));

    assertEquals(1, preview.drafts().size());
    assertEquals(target.id(), preview.drafts().getFirst().id());
    assertTrue(preview.drafts().stream().allMatch(item -> "approved".equals(item.workflowStateCode())));
    assertFalse(preview.auditTrail().isEmpty());
    assertTrue(preview.auditTrail().stream().allMatch(item -> target.id().equals(item.draftId())));
  }

  @Test
  void detailCanResolveDraftOutsideCurrentFilterAndReturnRelatedPanels() {
    Event event =
        new Event(
            "campaign-event",
            "DevOpsDays Santiago 2026",
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

    CampaignService.CampaignDetailSnapshot detail =
        campaignService
            .detail(
                "es",
                target.id(),
                new CampaignService.CampaignAdminFilters(
                    "HomeDir", "draft", "product_pulse", "linkedin"))
            .orElseThrow();

    assertEquals(target.id(), detail.draft().id());
    assertNotNull(detail.previewPack());
    assertNotNull(detail.summary());
    assertNotNull(detail.queueHealth());
    assertTrue(
        detail.previewPack().channels().stream()
            .anyMatch(channel -> "linkedin".equals(channel.channelCode())));
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
                    "")),
            java.util.List.of());
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

  @Test
  void publishRecoveryClassifiesRetryableBlockedAndManualDrafts() {
    when(discordPublisherService.status())
        .thenReturn(
            new CampaignPublisherStatus("discord", true, false, true, true, Duration.ofMinutes(15)));
    when(blueskyPublisherService.status())
        .thenReturn(
            new CampaignPublisherStatus("bluesky", true, true, true, true, Duration.ofMinutes(15)));
    when(mastodonPublisherService.status())
        .thenReturn(
            new CampaignPublisherStatus("mastodon", false, false, true, false, Duration.ofMinutes(15)));

    Instant now = Instant.now();
    CampaignStateSnapshot snapshot =
        new CampaignStateSnapshot(
            CampaignStateSnapshot.SCHEMA_VERSION,
            now,
            java.util.List.of(
                new CampaignDraftState(
                    "retry-draft",
                    "product_pulse",
                    now.minus(2, ChronoUnit.DAYS),
                    java.util.Map.of("version", "3.505.0"),
                    java.util.List.of("discord"),
                    true,
                    CampaignWorkflowState.SCHEDULED,
                    now.minus(4, ChronoUnit.HOURS),
                    "sergio.canales.e@gmail.com",
                    now.minus(3, ChronoUnit.HOURS),
                    now.minus(30, ChronoUnit.MINUTES),
                    true,
                    java.util.Map.of(),
                    now.minus(20, ChronoUnit.MINUTES),
                    "discord_failed"),
                new CampaignDraftState(
                    "blocked-draft",
                    "community_spotlight",
                    now.minus(2, ChronoUnit.DAYS),
                    java.util.Map.of("title", "Community refresh", "source", "internet", "publishedAt", LocalDate.now().toString()),
                    java.util.List.of("mastodon"),
                    true,
                    CampaignWorkflowState.SCHEDULED,
                    now.minus(5, ChronoUnit.HOURS),
                    "sergio.canales.e@gmail.com",
                    now.minus(4, ChronoUnit.HOURS),
                    now.minus(4, ChronoUnit.HOURS),
                    true,
                    java.util.Map.of(),
                    null,
                    ""),
                new CampaignDraftState(
                    "manual-draft",
                    "event_spotlight",
                    now.minus(2, ChronoUnit.DAYS),
                    java.util.Map.of(
                        "eventTitle", "DevOpsDays Santiago 2026",
                        "eventType", EventType.CONFERENCE.name(),
                        "eventDate", LocalDate.now().plusDays(10).toString(),
                        "eventUrl", "/event/devopsdays-santiago-2026"),
                    java.util.List.of("bluesky"),
                    true,
                    CampaignWorkflowState.SCHEDULED,
                    now.minus(5, ChronoUnit.HOURS),
                    "sergio.canales.e@gmail.com",
                    now.minus(4, ChronoUnit.HOURS),
                    now.minus(4, ChronoUnit.HOURS),
                    true,
                    java.util.Map.of(),
                    null,
                    "")),
            java.util.List.of());
    persistenceService.saveCampaignStateSync(snapshot);

    CampaignService.CampaignPreviewSnapshot preview = campaignService.preview("es");

    assertEquals("high", preview.recoverySummary().statusCode());
    assertEquals(3, preview.recoverySummary().actionableCount());
    assertEquals(1, preview.recoverySummary().retryableCount());
    assertEquals(1, preview.recoverySummary().blockedCount());
    assertEquals(1, preview.recoverySummary().manualCount());
    assertTrue(
        preview.recoveryItems().stream()
            .anyMatch(item -> "retry-draft".equals(item.draftId()) && "retryable".equals(item.stateCode())));
    assertTrue(
        preview.recoveryItems().stream()
            .anyMatch(item -> "blocked-draft".equals(item.draftId()) && "blocked".equals(item.stateCode())));
    assertTrue(
        preview.recoveryItems().stream()
            .anyMatch(item -> "manual-draft".equals(item.draftId()) && "manual".equals(item.stateCode())));
  }
}
