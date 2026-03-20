package com.scanales.homedir.campaigns;

import com.scanales.homedir.challenges.ChallengeCatalog;
import com.scanales.homedir.challenges.ChallengeDefinition;
import com.scanales.homedir.challenges.ChallengeService;
import com.scanales.homedir.community.CommunityContentService;
import com.scanales.homedir.insights.DevelopmentInsightsLedgerService;
import com.scanales.homedir.insights.DevelopmentInsightsStatus;
import com.scanales.homedir.model.Event;
import com.scanales.homedir.service.EventService;
import com.scanales.homedir.service.PersistenceService;
import com.scanales.homedir.service.UsageMetricsService;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CampaignService {

  private static final Logger LOG = Logger.getLogger(CampaignService.class);
  private static final String MARKETING_INITIATIVE_ID = "marketing-campaigns";
  private static final String KIND_PRODUCT_PULSE = "product_pulse";
  private static final String KIND_CHALLENGE_SPOTLIGHT = "challenge_spotlight";
  private static final String KIND_COMMUNITY_SPOTLIGHT = "community_spotlight";
  private static final String KIND_EVENT_SPOTLIGHT = "event_spotlight";
  private static final Duration STALE_DRAFT_WINDOW = Duration.ofHours(24);
  private static final Duration STALE_APPROVED_WINDOW = Duration.ofHours(24);
  private static final Duration OVERDUE_SCHEDULE_WINDOW = Duration.ofMinutes(30);
  private static final Duration STALE_LINKEDIN_HANDOFF_WINDOW = Duration.ofHours(24);
  private static final int MAX_ACTIVITY_ENTRIES = 120;

  @Inject PersistenceService persistenceService;
  @Inject UsageMetricsService usageMetricsService;
  @Inject DevelopmentInsightsLedgerService insightsLedgerService;
  @Inject CommunityContentService communityContentService;
  @Inject EventService eventService;
  @Inject ChallengeService challengeService;
  @Inject CampaignDiscordPublisherService discordPublisherService;
  @Inject CampaignBlueskyPublisherService blueskyPublisherService;
  @Inject CampaignMastodonPublisherService mastodonPublisherService;

  @ConfigProperty(name = "quarkus.application.version", defaultValue = "dev")
  String runtimeVersion;

  @ConfigProperty(name = "app.public-url", defaultValue = "http://localhost:8080")
  String publicBaseUrl;

  private final Object stateLock = new Object();
  private volatile CampaignStateSnapshot currentState = CampaignStateSnapshot.empty();
  private volatile long lastKnownStateMtime = Long.MIN_VALUE;
  private volatile CampaignOperationsStateSnapshot currentOperationsState =
      CampaignOperationsStateSnapshot.empty();
  private volatile long lastKnownOperationsStateMtime = Long.MIN_VALUE;

  @PostConstruct
  void init() {
    synchronized (stateLock) {
      refreshFromDisk(true);
      refreshOperationsFromDisk(true);
      if (currentState.drafts().isEmpty()) {
        generateAndPersist("startup");
      }
    }
  }

  @Scheduled(every = "{campaigns.drafts.refresh-interval:6h}")
  void scheduledRefresh() {
    synchronized (stateLock) {
      refreshOperationsFromDisk(false);
      if (!currentOperationsState.refreshAutomationEnabled()) {
        return;
      }
      generateAndPersist("schedule");
    }
  }

  @Scheduled(every = "{campaigns.publish.scan-interval:5m}")
  void scheduledPublish() {
    synchronized (stateLock) {
      refreshOperationsFromDisk(false);
      if (!currentOperationsState.publishAutomationEnabled()) {
        return;
      }
      publishScheduledDrafts("schedule", true);
    }
  }

  public CampaignStateSnapshot currentState() {
    synchronized (stateLock) {
      refreshFromDisk(false);
      return currentState;
    }
  }

  public CampaignStateSnapshot refreshDrafts() {
    synchronized (stateLock) {
      return generateAndPersist("manual_admin");
    }
  }

  public CampaignStateSnapshot approveDraft(String draftId, String actor) {
    synchronized (stateLock) {
      refreshFromDisk(false);
      currentState = mutateDraftState(draftId, source -> {
        Instant now = Instant.now();
        return source.withWorkflow(
            CampaignWorkflowState.APPROVED,
            now,
            safe(actor),
            null,
            now,
            source.sourceAvailable());
      });
      currentState =
          appendActivity(
              currentState,
              draftActivity(draftId, "workflow.approved", "", "", safe(actor), Instant.now()));
      saveState(currentState);
      recordWorkflowChange("CAMPAIGN_DRAFT_APPROVED", draftId);
      return currentState;
    }
  }

  public CampaignStateSnapshot resetDraft(String draftId) {
    synchronized (stateLock) {
      refreshFromDisk(false);
      currentState = mutateDraftState(draftId, source -> source.withWorkflow(
          CampaignWorkflowState.DRAFT,
          null,
          "",
          null,
          Instant.now(),
          source.sourceAvailable()));
      currentState =
          appendActivity(
              currentState,
              draftActivity(draftId, "workflow.reset", "", "", "system", Instant.now()));
      saveState(currentState);
      recordWorkflowChange("CAMPAIGN_DRAFT_RESET", draftId);
      return currentState;
    }
  }

  public CampaignStateSnapshot scheduleDraft(String draftId, LocalDateTime scheduledLocal, String actor) {
    synchronized (stateLock) {
      refreshFromDisk(false);
      CampaignDraftState draft =
          currentState.drafts().stream().filter(item -> item.id().equals(draftId)).findFirst().orElse(null);
      if (draft == null || !scheduleReadiness(draft).ready()) {
        throw new IllegalStateException("campaign_not_ready");
      }
      Instant scheduledFor = scheduledLocal.atZone(ZoneId.systemDefault()).toInstant();
      currentState = mutateDraftState(draftId, source -> {
        Instant now = Instant.now();
        Instant approvedAt = source.approvedAt() != null ? source.approvedAt() : now;
        String approvedBy = source.approvedBy().isBlank() ? safe(actor) : source.approvedBy();
        return source.withWorkflow(
            CampaignWorkflowState.SCHEDULED,
            approvedAt,
            approvedBy,
            scheduledFor,
            now,
            source.sourceAvailable());
      });
      currentState =
          appendActivity(
              currentState,
              draftActivity(
                  draftId,
                  "workflow.scheduled",
                  "",
                  scheduledFor.toString(),
                  safe(actor),
                  Instant.now()));
      saveState(currentState);
      recordWorkflowChange("CAMPAIGN_DRAFT_SCHEDULED", draftId);
      return currentState;
    }
  }

  public CampaignStateSnapshot unscheduleDraft(String draftId) {
    synchronized (stateLock) {
      refreshFromDisk(false);
      currentState = mutateDraftState(draftId, source -> source.withWorkflow(
          CampaignWorkflowState.APPROVED,
          source.approvedAt(),
          source.approvedBy(),
          null,
          Instant.now(),
          source.sourceAvailable()));
      currentState =
          appendActivity(
              currentState,
              draftActivity(draftId, "workflow.unscheduled", "", "", "system", Instant.now()));
      saveState(currentState);
      recordWorkflowChange("CAMPAIGN_DRAFT_UNSCHEDULED", draftId);
      return currentState;
    }
  }

  public CampaignStateSnapshot publishScheduledNow() {
    synchronized (stateLock) {
      return publishScheduledDrafts("manual_admin", false);
    }
  }

  public CampaignOperationsStateSnapshot currentOperationsState() {
    synchronized (stateLock) {
      refreshOperationsFromDisk(false);
      return currentOperationsState;
    }
  }

  public CampaignOperationsStateSnapshot setRefreshAutomationEnabled(boolean enabled, String actor) {
    synchronized (stateLock) {
      refreshOperationsFromDisk(false);
      currentOperationsState = currentOperationsState.withRefreshAutomation(enabled, safe(actor));
      saveOperationsState(currentOperationsState);
      currentState =
          appendActivity(
              currentState,
              new CampaignActivityEntry(
                  Instant.now(),
                  "",
                  "",
                  "",
                  enabled ? "ops.refresh.enabled" : "ops.refresh.disabled",
                  "",
                  enabled ? "enabled" : "disabled",
                  safe(actor)));
      saveState(currentState);
      return currentOperationsState;
    }
  }

  public CampaignOperationsStateSnapshot setPublishAutomationEnabled(boolean enabled, String actor) {
    synchronized (stateLock) {
      refreshOperationsFromDisk(false);
      currentOperationsState = currentOperationsState.withPublishAutomation(enabled, safe(actor));
      saveOperationsState(currentOperationsState);
      currentState =
          appendActivity(
              currentState,
              new CampaignActivityEntry(
                  Instant.now(),
                  "",
                  "",
                  "",
                  enabled ? "ops.publish.enabled" : "ops.publish.disabled",
                  "",
                  enabled ? "enabled" : "disabled",
                  safe(actor)));
      saveState(currentState);
      return currentOperationsState;
    }
  }

  public CampaignOperationsStateSnapshot setChannelAutomationEnabled(
      String channel, boolean enabled, String actor) {
    synchronized (stateLock) {
      refreshOperationsFromDisk(false);
      String normalizedChannel = safeChannel(channel);
      if (normalizedChannel.isBlank()) {
        return currentOperationsState;
      }
      currentOperationsState =
          currentOperationsState.withChannelAutomation(normalizedChannel, enabled, safe(actor));
      saveOperationsState(currentOperationsState);
      currentState =
          appendActivity(
              currentState,
              new CampaignActivityEntry(
                  Instant.now(),
                  "",
                  "",
                  "",
                  enabled ? "ops.channel.enabled" : "ops.channel.disabled",
                  normalizedChannel,
                  enabled ? "enabled" : "disabled",
                  safe(actor)));
      saveState(currentState);
      return currentOperationsState;
    }
  }

  public CampaignOperationsStateSnapshot setChannelGoLiveAcknowledged(
      String channel, boolean acknowledged, String actor) {
    synchronized (stateLock) {
      refreshOperationsFromDisk(false);
      String normalizedChannel = safeChannel(channel);
      if (normalizedChannel.isBlank()) {
        return currentOperationsState;
      }
      currentOperationsState =
          currentOperationsState.withChannelGoLiveAcknowledgement(
              normalizedChannel, acknowledged, safe(actor));
      saveOperationsState(currentOperationsState);
      currentState =
          appendActivity(
              currentState,
              new CampaignActivityEntry(
                  Instant.now(),
                  "",
                  "",
                  "",
                  acknowledged ? "ops.channel.golive.ack" : "ops.channel.golive.clear",
                  normalizedChannel,
                  acknowledged ? "acknowledged" : "cleared",
                  safe(actor)));
      saveState(currentState);
      return currentOperationsState;
    }
  }

  public CampaignOperationsStateSnapshot setPilotLiveChannel(String channel, String actor) {
    synchronized (stateLock) {
      refreshOperationsFromDisk(false);
      String normalizedChannel = safeChannel(channel);
      currentOperationsState = currentOperationsState.withPilotLiveChannel(normalizedChannel, safe(actor));
      saveOperationsState(currentOperationsState);
      currentState =
          appendActivity(
              currentState,
              new CampaignActivityEntry(
                  Instant.now(),
                  "",
                  "",
                  "",
                  normalizedChannel.isBlank() ? "ops.pilot.clear" : "ops.pilot.set",
                  normalizedChannel,
                  normalizedChannel.isBlank() ? "cleared" : "selected",
                  safe(actor)));
      saveState(currentState);
      return currentOperationsState;
    }
  }

  public CampaignOperationsStateSnapshot setPilotLiveArmed(boolean armed, String actor) {
    synchronized (stateLock) {
      refreshOperationsFromDisk(false);
      currentOperationsState = currentOperationsState.withPilotLiveArmed(armed, safe(actor));
      saveOperationsState(currentOperationsState);
      currentState =
          appendActivity(
              currentState,
              new CampaignActivityEntry(
                  Instant.now(),
                  "",
                  "",
                  "",
                  armed ? "ops.pilot.arm" : "ops.pilot.disarm",
                  currentOperationsState.pilotLiveChannel(),
                  armed ? "armed" : "disarmed",
                  safe(actor)));
      saveState(currentState);
      return currentOperationsState;
    }
  }

  public CampaignStateSnapshot markLinkedinPublished(String draftId, String actor) {
    synchronized (stateLock) {
      refreshFromDisk(false);
      Instant now = Instant.now();
      currentState =
          mutateDraftState(
              draftId,
              source ->
                  source.withManualChannelPublished(
                      "linkedin",
                      now,
                      "published_linkedin_manual",
                      now,
                      safe(actor)));
      currentState =
          appendActivity(
              currentState,
              draftActivity(
                  draftId,
                  "publish.linkedin.manual",
                  "linkedin",
                  "published_linkedin_manual",
                  safe(actor),
                  now));
      saveState(currentState);
      recordWorkflowChange("CAMPAIGN_LINKEDIN_HANDOFF_COMPLETED", draftId);
      recordPublishInsight(
          eventNameForChannel("linkedin", true),
          draftId,
          "manual_admin",
          "linkedin",
          "published_linkedin_manual");
      return currentState;
    }
  }

  public CampaignStateSnapshot retryChannel(String draftId, String channel, String actor) {
    synchronized (stateLock) {
      refreshFromDisk(false);
      refreshOperationsFromDisk(false);
      String normalizedChannel = safeChannel(channel);
      CampaignDraftState draft =
          currentState.drafts().stream()
              .filter(item -> item.id().equals(draftId))
              .findFirst()
              .orElse(null);
      if (draft == null || normalizedChannel.isBlank() || "linkedin".equals(normalizedChannel)) {
        throw new IllegalArgumentException("campaign_invalid_channel");
      }
      if (!isManualRetryAllowed(draft, normalizedChannel, Instant.now())) {
        throw new IllegalStateException("campaign_retry_not_ready");
      }
      CampaignPublisherStatus status =
          effectivePublisherStatuses(false).stream()
              .filter(item -> item.channel().equals(normalizedChannel))
              .findFirst()
              .orElse(null);
      if (status == null
          || !status.globalEnabled()
          || status.dryRun()
          || !status.channelEnabled()
          || !status.configured()) {
        throw new IllegalStateException("campaign_retry_not_ready");
      }
      Instant now = Instant.now();
      CampaignPublishResult result = publishToChannel(normalizedChannel, draft);
      Map<String, Instant> nextPublishedChannels = new LinkedHashMap<>(draft.publishedChannels());
      CampaignWorkflowState nextState = draft.workflowState();
      Instant activityAt = now;
      if (result.published()) {
        Instant publishedAt = result.publishedAt() != null ? result.publishedAt() : now;
        nextPublishedChannels.put(result.channel(), publishedAt);
        nextState = CampaignWorkflowState.PUBLISHED;
        activityAt = publishedAt;
        recordPublishInsight(
            eventNameForChannel(result.channel(), true),
            draftId,
            "manual_retry",
            result.channel(),
            result.outcome());
      } else {
        recordPublishInsight(
            result.skipped() ? "CAMPAIGN_PUBLISH_SKIPPED" : "CAMPAIGN_PUBLISH_FAILED",
            draftId,
            "manual_retry",
            result.channel(),
            result.outcome());
      }
      CampaignWorkflowState nextWorkflowState = nextState;
      Map<String, Instant> updatedPublishedChannels = Map.copyOf(nextPublishedChannels);
      String outcome = safe(result.outcome());
      String eventCode = result.published()
          ? "publish.channel.retry"
          : (result.skipped() ? "publish.retry.skipped" : "publish.retry.failed");
      currentState =
          appendActivity(
              mutateDraftState(
                  draftId,
                  source ->
                      source.withPublishStatus(
                          nextWorkflowState,
                          updatedPublishedChannels,
                          now,
                          outcome,
                          now)),
              draftActivity(
                  draftId,
                  eventCode,
                  result.channel(),
                  outcome,
                  safe(actor),
                  activityAt));
      saveState(currentState);
      return currentState;
    }
  }

  public CampaignPreviewSnapshot preview(String localeCode) {
    return preview(localeCode, CampaignAdminFilters.empty());
  }

  public CampaignPreviewSnapshot preview(String localeCode, CampaignAdminFilters filters) {
    CampaignStateSnapshot snapshot = currentState();
    CampaignOperationsStateSnapshot operationsState = currentOperationsState();
    ResourceBundle bundle = localizedBundle(localeCode);
    Locale locale = bundle.getLocale() == null ? Locale.forLanguageTag("es") : bundle.getLocale();
    CampaignCadenceGuidance cadenceGuidance = cadenceGuidance(bundle);
    Map<String, String> cadenceByKind = new LinkedHashMap<>();
    for (CampaignCadenceWindow window : cadenceGuidance.windowsByKind()) {
      cadenceByKind.put(window.label(), window.slotLabel());
    }
    CampaignAdminFilters normalizedFilters = filters == null ? CampaignAdminFilters.empty() : filters;
    List<CampaignDraftState> visibleDrafts =
        filterDrafts(snapshot.drafts(), normalizedFilters, bundle, locale, cadenceByKind);
    List<CampaignPreviewCard> cards =
        visibleDrafts.stream()
            .map(item -> toPreview(item, bundle, locale, cadenceByKind))
            .toList();
    List<CampaignPreviewPack> previewPacks =
        visibleDrafts.stream()
            .map(draft -> {
              CampaignPreviewCard preview =
                  cards.stream().filter(item -> item.id().equals(draft.id())).findFirst().orElseThrow();
              return toPreviewPack(draft, preview, bundle);
            })
            .toList();
    List<CampaignPublisherPreviewStatus> publisherStatuses =
        effectivePublisherStatuses(false).stream()
            .map(status -> toPublisherStatus(status, bundle))
            .toList();
    boolean globalPublishingEnabled = publisherStatuses.stream().anyMatch(CampaignPublisherPreviewStatus::globalEnabled);
    List<CampaignAttributionSummary> attribution = attributionSummary(visibleDrafts, bundle);
    return new CampaignPreviewSnapshot(
        snapshot.generatedAt(),
        List.copyOf(cards),
        globalPublishingEnabled,
        operationsStatus(operationsState, bundle, locale),
        List.copyOf(publisherStatuses),
        summarize(snapshot, bundle, locale),
        businessDashboard(cards, attribution, bundle),
        rolloutChecklist(bundle, locale),
        recentActivity(visibleDrafts, bundle, locale),
        cadenceGuidance,
        List.copyOf(previewPacks),
        List.copyOf(attribution),
        publishRecoverySummary(snapshot, bundle, locale),
        publishRecoveryItems(visibleDrafts, bundle, locale),
        queueHealth(snapshot, bundle, locale),
        queueRisks(visibleDrafts, bundle, locale),
        auditTrail(snapshot, visibleDrafts, normalizedFilters, bundle, locale),
        visibleDrafts.stream()
            .filter(this::eligibleForLinkedinHandoff)
            .map(draft -> toLinkedinHandoff(draft, bundle, locale))
            .toList());
  }

  public Optional<CampaignDetailSnapshot> detail(
      String localeCode, String draftId, CampaignAdminFilters filters) {
    CampaignPreviewSnapshot preview = preview(localeCode, filters);
    Optional<CampaignPreviewCard> card =
        preview.drafts().stream().filter(item -> item.id().equals(draftId)).findFirst();
    if (card.isEmpty()) {
      CampaignPreviewSnapshot fullPreview = preview(localeCode, CampaignAdminFilters.empty());
      card = fullPreview.drafts().stream().filter(item -> item.id().equals(draftId)).findFirst();
      if (card.isEmpty()) {
        return Optional.empty();
      }
      preview = fullPreview;
    }
    CampaignPreviewSnapshot selectedPreview = preview;
    CampaignPreviewCard selectedCard = card.orElseThrow();
    CampaignPreviewPack pack =
        selectedPreview.previewPacks().stream()
            .filter(item -> item.draftId().equals(draftId))
            .findFirst()
            .orElse(null);
    CampaignAttributionSummary attribution =
        selectedPreview.attribution().stream()
            .filter(item -> item.draftId().equals(draftId))
            .findFirst()
            .orElse(null);
    CampaignLinkedinHandoff linkedinHandoff =
        selectedPreview.linkedinHandoffs().stream()
            .filter(item -> item.draftId().equals(draftId))
            .findFirst()
            .orElse(null);
    List<CampaignQueueRiskItem> risks =
        selectedPreview.queueRisks().stream()
            .filter(item -> item.draftId().equals(draftId))
            .toList();
    List<CampaignPublishRecoveryItem> recoveryItems =
        selectedPreview.recoveryItems().stream()
            .filter(item -> item.draftId().equals(draftId))
            .toList();
    List<CampaignAuditTrailEntry> auditEntries =
        selectedPreview.auditTrail().stream()
            .filter(item -> item.draftId().equals(draftId))
            .toList();
    return Optional.of(
        new CampaignDetailSnapshot(
            selectedCard,
            pack,
            attribution,
            List.copyOf(auditEntries),
            List.copyOf(recoveryItems),
            List.copyOf(risks),
            linkedinHandoff,
            schedulingReadiness(selectedCard),
            selectedPreview.recoverySummary(),
            selectedPreview.summary(),
            selectedPreview.queueHealth()));
  }

  public void resetStateForTests() {
    synchronized (stateLock) {
      currentState = CampaignStateSnapshot.empty();
      persistenceService.saveCampaignStateSync(currentState);
      lastKnownStateMtime = persistenceService.campaignStateLastModifiedMillis();
      currentOperationsState = CampaignOperationsStateSnapshot.empty();
      persistenceService.saveCampaignOperationsStateSync(currentOperationsState);
      lastKnownOperationsStateMtime = persistenceService.campaignOperationsStateLastModifiedMillis();
    }
  }

  private CampaignStateSnapshot generateAndPersist(String source) {
    CampaignStateSnapshot previous = currentState == null ? CampaignStateSnapshot.empty() : currentState;
    CampaignStateSnapshot snapshot =
        new CampaignStateSnapshot(
            CampaignStateSnapshot.SCHEMA_VERSION,
            Instant.now(),
            mergeDrafts(previous.drafts(), buildDrafts()),
            previous.activity());
    currentState =
        appendActivity(
            snapshot,
            new CampaignActivityEntry(
                Instant.now(),
                "",
                "",
                "",
                "system.refresh",
                "",
                safe(source),
                "system"));
    saveState(currentState);
    recordInsightRefresh(currentState, source);
    return currentState;
  }

  private List<CampaignDraftState> buildDrafts() {
    List<CampaignDraftState> drafts = new ArrayList<>();
    Instant now = Instant.now();
    DevelopmentInsightsStatus insights = safeInsightsStatus();
    Map<String, Long> metrics = usageMetricsService.snapshot();

    drafts.add(
        new CampaignDraftState(
            "product-pulse",
            KIND_PRODUCT_PULSE,
            now,
            Map.of(
                "version", safe(runtimeVersion),
                "events24", String.valueOf(Math.max(0, insights.eventsLast24Hours())),
                "initiatives24", String.valueOf(Math.max(0, insights.activeInitiativesLast24Hours())),
                "prSuccess7d", formatPct(insights.prValidationSuccessRatePctLast7Days()),
                "prodSuccess7d", formatPct(insights.productionSuccessRatePctLast7Days()),
                "challengeCompleted", String.valueOf(metrics.getOrDefault("funnel:challenge_completed", 0L))),
            List.of("discord", "bluesky", "mastodon", "linkedin"),
            true,
            CampaignWorkflowState.DRAFT,
            null,
            "",
            null,
            now,
            true,
            Map.of(),
            null,
            ""));

    challengeService.trendingChallenges(Duration.ofDays(7), 1).stream().findFirst().ifPresent(
        trend -> {
          ChallengeDefinition definition = ChallengeCatalog.find(trend.challengeId());
          drafts.add(
              new CampaignDraftState(
                  "challenge-" + safeId(trend.challengeId()),
                  KIND_CHALLENGE_SPOTLIGHT,
                  now,
                  Map.of(
                      "challengeId", safe(trend.challengeId()),
                      "challengeTitleKey", challengeTitleKey(trend.challengeId()),
                      "rewardHcoin", String.valueOf(definition != null ? definition.rewardHcoin() : 0),
                      "completions", String.valueOf(Math.max(0, trend.completions()))),
                  List.of("linkedin", "discord", "bluesky"),
                  true,
                  CampaignWorkflowState.DRAFT,
                  null,
                  "",
                  null,
                  now,
                  true,
                  Map.of(),
                  null,
                  ""));
        });

    communityContentService.listNew(1, 0).stream().findFirst().ifPresent(
        item ->
            drafts.add(
                new CampaignDraftState(
                    "community-" + safeId(item.id()),
                    KIND_COMMUNITY_SPOTLIGHT,
                    now,
                    Map.of(
                        "title", safe(item.title()),
                        "url", safe(item.url()),
                        "source", safe(item.source()),
                        "publishedAt", item.publishedAt() != null ? item.publishedAt().atZone(ZoneOffset.UTC).toLocalDate().toString() : ""),
                    List.of("discord", "bluesky", "mastodon", "linkedin"),
                    true,
                    CampaignWorkflowState.DRAFT,
                    null,
                    "",
                    null,
                    now,
                    true,
                    Map.of(),
                    null,
                    "")));

    nextUpcomingEvent().ifPresent(
        event ->
            drafts.add(
                new CampaignDraftState(
                    "event-" + safeId(event.getId()),
                    KIND_EVENT_SPOTLIGHT,
                    now,
                    Map.of(
                        "eventId", safe(event.getId()),
                        "eventTitle", safe(event.getTitle()),
                        "eventDate", event.getDate() != null ? event.getDate().toString() : "",
                        "eventType", safe(event.getType() != null ? event.getType().name() : "OTHER"),
                        "eventUrl", "/event/" + safe(event.getId())),
                    List.of("discord", "linkedin", "bluesky", "mastodon"),
                    true,
                    CampaignWorkflowState.DRAFT,
                    null,
                    "",
                    null,
                    now,
                    true,
                    Map.of(),
                    null,
                    "")));

    drafts.sort(Comparator.comparing(CampaignDraftState::kind).thenComparing(CampaignDraftState::id));
    return List.copyOf(drafts);
  }

  private List<CampaignDraftState> mergeDrafts(List<CampaignDraftState> previousDrafts, List<CampaignDraftState> freshDrafts) {
    Map<String, CampaignDraftState> previousById = new LinkedHashMap<>();
    for (CampaignDraftState previous : previousDrafts) {
      previousById.put(previous.id(), previous);
    }
    Map<String, CampaignDraftState> merged = new LinkedHashMap<>();
    for (CampaignDraftState fresh : freshDrafts) {
      CampaignDraftState previous = previousById.remove(fresh.id());
      if (previous == null) {
        merged.put(fresh.id(), fresh);
        continue;
      }
      merged.put(
          fresh.id(),
          fresh.withWorkflow(
              previous.workflowState(),
              previous.approvedAt(),
              previous.approvedBy(),
              previous.scheduledFor(),
              previous.updatedAt() != null ? previous.updatedAt() : fresh.updatedAt(),
              true));
    }
    for (CampaignDraftState leftover : previousById.values()) {
      if (leftover.workflowState() != CampaignWorkflowState.DRAFT) {
        merged.put(
            leftover.id(),
            leftover.withWorkflow(
                leftover.workflowState(),
                leftover.approvedAt(),
                leftover.approvedBy(),
                leftover.scheduledFor(),
                leftover.updatedAt(),
                false));
      }
    }
    return merged.values().stream()
        .sorted(
            Comparator.comparing(CampaignDraftState::workflowState)
                .thenComparing(CampaignDraftState::kind)
                .thenComparing(CampaignDraftState::id))
        .toList();
  }

  private Optional<Event> nextUpcomingEvent() {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    return eventService.listEvents().stream()
        .filter(event -> event != null && event.getId() != null && !event.getId().isBlank())
        .filter(event -> event.getDate() != null && !event.getDate().isBefore(today))
        .sorted(Comparator.comparing(Event::getDate).thenComparing(Event::getTitle, String.CASE_INSENSITIVE_ORDER))
        .findFirst();
  }

  private CampaignPreviewCard toPreview(
      CampaignDraftState draft,
      ResourceBundle bundle,
      Locale locale,
      Map<String, String> cadenceByKind) {
    CampaignSchedulingReadiness readiness = scheduleReadiness(draft);
    String kindLabel = bundleText(bundle, "campaigns_kind_" + draft.kind());
    String workflowLabel = bundleText(bundle, "campaigns_workflow_" + draft.workflowState().name().toLowerCase(Locale.ROOT));
    String sourceStatusLabel =
        bundleText(bundle, draft.sourceAvailable() ? "campaigns_admin_source_live" : "campaigns_admin_source_stale");
    String scheduledLabel = draft.scheduledFor() == null ? "—" : localizedDateTime(draft.scheduledFor(), locale);
    String publisherOutcomeLabel =
        draft.lastPublishOutcome().isBlank()
            ? "—"
            : bundleText(bundle, "campaigns_publish_outcome_" + draft.lastPublishOutcome());
    String publishedChannelsLabel =
        draft.publishedChannels().isEmpty()
            ? "—"
            : draft.publishedChannels().keySet().stream()
                .map(channel -> bundleText(bundle, "campaigns_channel_" + channel))
                .sorted()
                .reduce((left, right) -> left + ", " + right)
                .orElse("—");
    String recommendedWindowLabel =
        cadenceByKind.getOrDefault(
            bundleText(bundle, "campaigns_kind_" + draft.kind()),
            bundleText(bundle, "campaigns_admin_cadence_no_window"));
    String scheduleReadinessLabel =
        bundleText(
            bundle,
            readiness.ready()
                ? "campaigns_admin_schedule_readiness_ready"
                : "campaigns_admin_schedule_readiness_blocked");
    String scheduleReadinessDetailLabel =
        readiness.ready()
            ? named(
                bundle,
                "campaigns_admin_schedule_ready_channels",
                Map.of(
                    "channels",
                    readiness.readyChannels().stream()
                        .map(channel -> bundleText(bundle, "campaigns_channel_" + channel))
                        .reduce((left, right) -> left + ", " + right)
                        .orElse("—")))
            : readiness.blockerCodes().stream()
                .map(code -> bundleText(bundle, "campaigns_admin_schedule_blocker_" + code))
                .distinct()
                .reduce((left, right) -> left + " · " + right)
                .orElse(bundleText(bundle, "campaigns_admin_schedule_blocker_none"));
    return switch (draft.kind()) {
      case KIND_PRODUCT_PULSE -> new CampaignPreviewCard(
          draft.id(),
          kindLabel,
          named(bundle, "campaigns_product_pulse_title", Map.of("version", value(draft, "version"))),
          named(
              bundle,
              "campaigns_product_pulse_body",
              Map.of(
                  "events24", value(draft, "events24"),
                  "initiatives24", value(draft, "initiatives24"))),
          bundleText(bundle, "campaigns_product_pulse_cta"),
          "/about",
          localizedChannels(bundle, draft.suggestedChannels()),
          List.of(
              named(bundle, "campaigns_product_pulse_evidence_1", Map.of("prSuccess7d", value(draft, "prSuccess7d"))),
              named(bundle, "campaigns_product_pulse_evidence_2", Map.of("prodSuccess7d", value(draft, "prodSuccess7d"))),
              named(bundle, "campaigns_product_pulse_evidence_3", Map.of("challengeCompleted", value(draft, "challengeCompleted")))),
          bundleText(bundle, draft.approvalRequired() ? "campaigns_admin_requires_approval" : "campaigns_admin_draft_only"),
          draft.workflowState().name().toLowerCase(Locale.ROOT),
          workflowLabel,
          sourceStatusLabel,
          scheduledLabel,
          publishedChannelsLabel,
          publisherOutcomeLabel,
          recommendedWindowLabel,
          readiness.ready(),
          scheduleReadinessLabel,
          scheduleReadinessDetailLabel);
      case KIND_CHALLENGE_SPOTLIGHT -> {
        String challengeTitle = bundleText(bundle, value(draft, "challengeTitleKey"));
        yield new CampaignPreviewCard(
            draft.id(),
            kindLabel,
            named(bundle, "campaigns_challenge_title", Map.of("challengeTitle", challengeTitle)),
            named(bundle, "campaigns_challenge_body", Map.of("completions", value(draft, "completions"))),
            bundleText(bundle, "campaigns_challenge_cta"),
            "/private/profile#challenges-panel",
            localizedChannels(bundle, draft.suggestedChannels()),
            List.of(
                named(bundle, "campaigns_challenge_evidence_1", Map.of("completions", value(draft, "completions"))),
                named(bundle, "campaigns_challenge_evidence_2", Map.of("rewardHcoin", value(draft, "rewardHcoin")))),
            bundleText(bundle, draft.approvalRequired() ? "campaigns_admin_requires_approval" : "campaigns_admin_draft_only"),
            draft.workflowState().name().toLowerCase(Locale.ROOT),
            workflowLabel,
            sourceStatusLabel,
            scheduledLabel,
            publishedChannelsLabel,
            publisherOutcomeLabel,
            recommendedWindowLabel,
            readiness.ready(),
            scheduleReadinessLabel,
            scheduleReadinessDetailLabel);
      }
      case KIND_COMMUNITY_SPOTLIGHT -> new CampaignPreviewCard(
          draft.id(),
          kindLabel,
          named(bundle, "campaigns_community_title", Map.of("title", value(draft, "title"))),
          bundleText(bundle, "campaigns_community_body"),
          bundleText(bundle, "campaigns_community_cta"),
          "/comunidad",
          localizedChannels(bundle, draft.suggestedChannels()),
          List.of(
              named(bundle, "campaigns_community_evidence_1", Map.of("source", value(draft, "source"))),
              named(bundle, "campaigns_community_evidence_2", Map.of("publishedAt", localizedDate(value(draft, "publishedAt"), locale)))),
          bundleText(bundle, draft.approvalRequired() ? "campaigns_admin_requires_approval" : "campaigns_admin_draft_only"),
          draft.workflowState().name().toLowerCase(Locale.ROOT),
          workflowLabel,
          sourceStatusLabel,
          scheduledLabel,
          publishedChannelsLabel,
          publisherOutcomeLabel,
          recommendedWindowLabel,
          readiness.ready(),
          scheduleReadinessLabel,
          scheduleReadinessDetailLabel);
      case KIND_EVENT_SPOTLIGHT -> new CampaignPreviewCard(
          draft.id(),
          kindLabel,
          named(bundle, "campaigns_event_title", Map.of("eventTitle", value(draft, "eventTitle"))),
          bundleText(bundle, "campaigns_event_body"),
          bundleText(bundle, "campaigns_event_cta"),
          value(draft, "eventUrl"),
          localizedChannels(bundle, draft.suggestedChannels()),
          List.of(
              named(bundle, "campaigns_event_evidence_1", Map.of("eventDate", localizedDate(value(draft, "eventDate"), locale))),
              named(bundle, "campaigns_event_evidence_2", Map.of("eventType", humanizeEnum(value(draft, "eventType"))))),
          bundleText(bundle, draft.approvalRequired() ? "campaigns_admin_requires_approval" : "campaigns_admin_draft_only"),
          draft.workflowState().name().toLowerCase(Locale.ROOT),
          workflowLabel,
          sourceStatusLabel,
          scheduledLabel,
          publishedChannelsLabel,
          publisherOutcomeLabel,
          recommendedWindowLabel,
          readiness.ready(),
          scheduleReadinessLabel,
          scheduleReadinessDetailLabel);
      default -> new CampaignPreviewCard(
          draft.id(),
          kindLabel,
          draft.id(),
          draft.kind(),
          bundleText(bundle, "campaigns_admin_unknown_cta"),
          "/private/admin",
          localizedChannels(bundle, draft.suggestedChannels()),
          List.of(),
          bundleText(bundle, "campaigns_admin_draft_only"),
          draft.workflowState().name().toLowerCase(Locale.ROOT),
          workflowLabel,
          sourceStatusLabel,
          scheduledLabel,
          publishedChannelsLabel,
          publisherOutcomeLabel,
          recommendedWindowLabel,
          readiness.ready(),
          scheduleReadinessLabel,
          scheduleReadinessDetailLabel);
    };
  }

  private List<String> localizedChannels(ResourceBundle bundle, List<String> channels) {
    return channels.stream().map(channel -> bundleText(bundle, "campaigns_channel_" + channel)).toList();
  }

  private void refreshFromDisk(boolean force) {
    long diskMtime = persistenceService.campaignStateLastModifiedMillis();
    if (!force && diskMtime == lastKnownStateMtime) {
      return;
    }
    currentState = persistenceService.loadCampaignState().orElse(CampaignStateSnapshot.empty());
    lastKnownStateMtime = diskMtime;
  }

  private void refreshOperationsFromDisk(boolean force) {
    long diskMtime = persistenceService.campaignOperationsStateLastModifiedMillis();
    if (!force && diskMtime == lastKnownOperationsStateMtime) {
      return;
    }
    currentOperationsState =
        persistenceService
            .loadCampaignOperationsState()
            .orElse(CampaignOperationsStateSnapshot.empty());
    lastKnownOperationsStateMtime = diskMtime;
  }

  private void recordInsightRefresh(CampaignStateSnapshot snapshot, String source) {
    try {
      insightsLedgerService.startInitiative(
          MARKETING_INITIATIVE_ID,
          "Marketing campaigns foundation",
          Instant.now().toString(),
          Map.of("module", "campaigns"));
      insightsLedgerService.append(
          MARKETING_INITIATIVE_ID,
          "CAMPAIGN_DRAFTS_REFRESHED",
          Map.of(
              "module", "campaigns",
              "source", safe(source),
              "drafts", String.valueOf(snapshot.drafts().size())));
    } catch (IllegalStateException e) {
      LOG.debug("campaigns_insights_disabled");
    } catch (Exception e) {
      LOG.warn("campaigns_insights_refresh_failed", e);
    }
  }

  private void recordWorkflowChange(String eventType, String draftId) {
    try {
      insightsLedgerService.append(
          MARKETING_INITIATIVE_ID,
          eventType,
          Map.of(
              "module", "campaigns",
              "draftId", safe(draftId)));
    } catch (IllegalStateException e) {
      LOG.debug("campaigns_insights_disabled");
    } catch (Exception e) {
      LOG.warn("campaigns_insights_workflow_failed", e);
    }
  }

  private CampaignStateSnapshot publishScheduledDrafts(String source, boolean respectPublishAutomation) {
    refreshFromDisk(false);
    refreshOperationsFromDisk(false);
    Instant now = Instant.now();
    List<CampaignActivityEntry> activity = new ArrayList<>();
    List<CampaignDraftState> drafts =
        currentState.drafts().stream()
            .map(
                draft -> {
                  PublishMutation mutation = publishIfDue(draft, now, source, respectPublishAutomation);
                  activity.addAll(mutation.activity());
                  return mutation.draft();
                })
            .toList();
    currentState =
        new CampaignStateSnapshot(
            CampaignStateSnapshot.SCHEMA_VERSION,
            currentState.generatedAt(),
            drafts,
            currentState.activity());
    for (CampaignActivityEntry item : activity) {
      currentState = appendActivity(currentState, item);
    }
    saveState(currentState);
    return currentState;
  }

  private PublishMutation publishIfDue(
      CampaignDraftState draft, Instant now, String source, boolean respectPublishAutomation) {
    if ((draft.workflowState() != CampaignWorkflowState.SCHEDULED
            && draft.workflowState() != CampaignWorkflowState.PUBLISHED)
        || draft.scheduledFor() == null) {
      return new PublishMutation(draft, List.of());
    }
    if (draft.scheduledFor().isAfter(now)) {
      return new PublishMutation(draft, List.of());
    }
    Map<String, Instant> nextPublishedChannels = new LinkedHashMap<>(draft.publishedChannels());
    CampaignWorkflowState nextState = draft.workflowState();
    Instant lastAttemptAt = draft.lastPublishAttemptAt();
    String lastOutcome = draft.lastPublishOutcome();
    List<CampaignActivityEntry> activity = new ArrayList<>();
    for (CampaignPublisherStatus status : effectivePublisherStatuses(respectPublishAutomation)) {
      if (!draft.suggestedChannels().contains(status.channel())) {
        continue;
      }
      if (nextPublishedChannels.containsKey(status.channel())) {
        continue;
      }
      if (lastAttemptAt != null && lastAttemptAt.plus(status.minInterval()).isAfter(now)) {
        continue;
      }
      CampaignPublishResult result = publishToChannel(status.channel(), draft);
      lastAttemptAt = now;
      lastOutcome = safe(result.outcome());
      if (result.published()) {
        Instant publishedAt = result.publishedAt() != null ? result.publishedAt() : now;
        nextPublishedChannels.put(result.channel(), publishedAt);
        nextState = CampaignWorkflowState.PUBLISHED;
        recordPublishInsight(eventNameForChannel(result.channel(), true), draft.id(), source, result.channel(), result.outcome());
        activity.add(
            draftActivity(
                draft.id(),
                "publish.channel",
                result.channel(),
                safe(result.outcome()),
                "system",
                publishedAt));
      } else if (result.skipped()) {
        recordPublishInsight("CAMPAIGN_PUBLISH_SKIPPED", draft.id(), source, result.channel(), result.outcome());
        activity.add(
            draftActivity(
                draft.id(),
                "publish.skipped",
                result.channel(),
                safe(result.outcome()),
                "system",
                now));
      } else {
        recordPublishInsight("CAMPAIGN_PUBLISH_FAILED", draft.id(), source, result.channel(), result.outcome());
        activity.add(
            draftActivity(
                draft.id(),
                "publish.failed",
                result.channel(),
                safe(result.outcome()),
                "system",
                now));
      }
    }
    return new PublishMutation(
        draft.withPublishStatus(
            nextState,
            Map.copyOf(nextPublishedChannels),
            lastAttemptAt,
            lastOutcome,
            now),
        List.copyOf(activity));
  }

  private CampaignPublishResult publishToChannel(String channel, CampaignDraftState draft) {
    return switch (channel) {
      case "discord" -> discordPublisherService.publish(draft);
      case "bluesky" -> blueskyPublisherService.publish(draft);
      case "mastodon" -> mastodonPublisherService.publish(draft);
      default -> CampaignPublishResult.skipped(channel, "channel_not_supported");
    };
  }

  private String eventNameForChannel(String channel, boolean success) {
    if (!success) {
      return "CAMPAIGN_PUBLISH_FAILED";
    }
    return switch (channel) {
      case "discord" -> "CAMPAIGN_PUBLISHED_DISCORD";
      case "bluesky" -> "CAMPAIGN_PUBLISHED_BLUESKY";
      case "mastodon" -> "CAMPAIGN_PUBLISHED_MASTODON";
      case "linkedin" -> "CAMPAIGN_PUBLISHED_LINKEDIN";
      default -> "CAMPAIGN_PUBLISHED_CHANNEL";
    };
  }

  private void recordPublishInsight(String eventType, String draftId, String source, String channel, String outcome) {
    try {
      insightsLedgerService.append(
          MARKETING_INITIATIVE_ID,
          eventType,
          Map.of(
              "module", "campaigns",
              "draftId", safe(draftId),
              "source", safe(source),
              "channel", safe(channel),
              "outcome", safe(outcome)));
    } catch (IllegalStateException e) {
      LOG.debug("campaigns_insights_disabled");
    } catch (Exception e) {
      LOG.warn("campaigns_insights_publish_failed", e);
    }
  }

  private CampaignStateSnapshot mutateDraftState(String draftId, java.util.function.Function<CampaignDraftState, CampaignDraftState> mutator) {
    List<CampaignDraftState> drafts = currentState.drafts().stream().map(item -> {
      if (item.id().equals(draftId)) {
        return mutator.apply(item);
      }
      return item;
    }).toList();
    return new CampaignStateSnapshot(
        CampaignStateSnapshot.SCHEMA_VERSION,
        currentState.generatedAt(),
        drafts,
        currentState.activity());
  }

  private CampaignStateSnapshot appendActivity(
      CampaignStateSnapshot snapshot, CampaignActivityEntry entry) {
    if (entry == null) {
      return snapshot;
    }
    List<CampaignActivityEntry> nextActivity = new ArrayList<>(snapshot.activity());
    nextActivity.add(entry);
    if (nextActivity.size() > MAX_ACTIVITY_ENTRIES) {
      nextActivity = nextActivity.subList(nextActivity.size() - MAX_ACTIVITY_ENTRIES, nextActivity.size());
    }
    return new CampaignStateSnapshot(
        CampaignStateSnapshot.SCHEMA_VERSION,
        snapshot.generatedAt(),
        snapshot.drafts(),
        List.copyOf(nextActivity));
  }

  private void saveState(CampaignStateSnapshot snapshot) {
    persistenceService.saveCampaignState(snapshot);
    lastKnownStateMtime = persistenceService.campaignStateLastModifiedMillis();
  }

  private CampaignActivityEntry draftActivity(
      String draftId,
      String eventCode,
      String channel,
      String outcome,
      String actor,
      Instant timestamp) {
    CampaignDraftState draft =
        currentState.drafts().stream().filter(item -> item.id().equals(draftId)).findFirst().orElse(null);
    String kind = draft == null ? "" : safe(draft.kind());
    String workflow =
        draft == null || draft.workflowState() == null
            ? ""
            : draft.workflowState().name().toLowerCase(Locale.ROOT);
    return new CampaignActivityEntry(
        timestamp == null ? Instant.now() : timestamp,
        safe(draftId),
        kind,
        workflow,
        safe(eventCode),
        safe(channel),
        safe(outcome),
        safe(actor));
  }

  private DevelopmentInsightsStatus safeInsightsStatus() {
    try {
      return insightsLedgerService.status();
    } catch (Exception e) {
      LOG.debug("campaigns_insights_status_unavailable");
      return new DevelopmentInsightsStatus(
          false,
          "",
          0L,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          null,
          0,
          null,
          0,
          0,
          null,
          0,
          0,
          null,
          null,
          null,
          0,
          0,
          null,
          0,
          0,
          0,
          null,
          0,
          0,
          0,
          null,
          0,
          Map.of(),
          null,
          false,
          null,
          0L,
          0L,
          0L);
    }
  }

  private ResourceBundle localizedBundle(String localeCode) {
    Locale bundleLocale = Locale.forLanguageTag("en".equalsIgnoreCase(localeCode) ? "en" : "es");
    return ResourceBundle.getBundle("i18n", bundleLocale);
  }

  private static String bundleText(ResourceBundle bundle, String key) {
    return bundle.containsKey(key) ? bundle.getString(key) : key;
  }

  private static String named(ResourceBundle bundle, String key, Map<String, String> values) {
    String pattern = bundleText(bundle, key);
    String result = pattern;
    for (Map.Entry<String, String> entry : values.entrySet()) {
      result = result.replace("{" + entry.getKey() + "}", safe(entry.getValue()));
    }
    return result;
  }

  private static String value(CampaignDraftState draft, String key) {
    return safe(draft.metadata().get(key));
  }

  private static String safe(String value) {
    return value == null ? "" : value.trim();
  }

  private static String safeId(String value) {
    if (value == null || value.isBlank()) {
      return "draft";
    }
    return value.replaceAll("[^a-zA-Z0-9\\-]+", "-");
  }

  private static String formatPct(Long value) {
    return value == null ? "n/a" : value + "%";
  }

  private static String challengeTitleKey(String challengeId) {
    return switch (challengeId) {
      case "community-scout" -> "challenge_community_scout_title";
      case "event-explorer" -> "challenge_event_explorer_title";
      case "open-source-identity" -> "challenge_open_source_identity_title";
      default -> "campaigns_admin_unknown_challenge";
    };
  }

  private static String humanizeEnum(String raw) {
    if (raw == null || raw.isBlank()) {
      return "—";
    }
    String lower = raw.toLowerCase(Locale.ROOT).replace('_', ' ');
    return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
  }

  private static String localizedDate(String raw, Locale locale) {
    if (raw == null || raw.isBlank()) {
      return "—";
    }
    try {
      return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(LocalDate.parse(raw));
    } catch (Exception ignored) {
      return raw;
    }
  }

  private static String localizedDateTime(Instant value, Locale locale) {
    if (value == null) {
      return "—";
    }
    return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withLocale(locale)
        .withZone(ZoneId.systemDefault())
        .format(value);
  }

  private CampaignPublisherPreviewStatus toPublisherStatus(
      CampaignPublisherStatus status, ResourceBundle bundle) {
    return new CampaignPublisherPreviewStatus(
        status.channel(),
        bundleText(bundle, "campaigns_channel_" + status.channel()),
        status.globalEnabled(),
        status.dryRun(),
        status.channelEnabled(),
        status.configured(),
        String.valueOf(status.minInterval()));
  }

  private CampaignAutomationStatus operationsStatus(
      CampaignOperationsStateSnapshot operationsState, ResourceBundle bundle, Locale locale) {
    List<CampaignChannelAutomationStatus> channels =
        publisherStatuses().stream()
            .map(
                status ->
                    new CampaignChannelAutomationStatus(
                        status.channel(),
                        bundleText(bundle, "campaigns_channel_" + status.channel()),
                        operationsState.isChannelAutomationEnabled(status.channel()),
                        status.channelEnabled(),
                        status.configured(),
                        status.globalEnabled()
                            && operationsState.publishAutomationEnabled()
                            && status.channelEnabled()
                            && operationsState.isChannelAutomationEnabled(status.channel())
                            && status.configured()
                            && !status.dryRun(),
                        status.dryRun(),
                        String.valueOf(status.minInterval())))
            .toList();
    String updatedBy =
        operationsState.updatedBy().isBlank()
            ? bundleText(bundle, "campaigns_admin_audit_system")
            : operationsState.updatedBy();
    return new CampaignAutomationStatus(
        operationsState.refreshAutomationEnabled(),
        operationsState.publishAutomationEnabled(),
        localizedDateTime(operationsState.updatedAt(), locale),
        updatedBy,
        channels);
  }

  private void saveOperationsState(CampaignOperationsStateSnapshot snapshot) {
    persistenceService.saveCampaignOperationsState(snapshot);
    lastKnownOperationsStateMtime = persistenceService.campaignOperationsStateLastModifiedMillis();
  }

  private static String safeChannel(String value) {
    String normalized = safe(value).toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "discord", "bluesky", "mastodon" -> normalized;
      default -> "";
    };
  }

  private CampaignCadenceGuidance cadenceGuidance(ResourceBundle bundle) {
    UsageMetricsService.ObservabilityWindow window = usageMetricsService.observabilityWindow(168);
    List<CampaignCadenceWindow> overall =
        bestWindows(
            window,
            List.of("home", "community", "events", "profile", "project"),
            3,
            bundle);
    List<CampaignCadenceWindow> byKind =
        List.of(
            cadenceWindowForKind(KIND_PRODUCT_PULSE, bundle, window),
            cadenceWindowForKind(KIND_CHALLENGE_SPOTLIGHT, bundle, window),
            cadenceWindowForKind(KIND_COMMUNITY_SPOTLIGHT, bundle, window),
            cadenceWindowForKind(KIND_EVENT_SPOTLIGHT, bundle, window));
    return new CampaignCadenceGuidance(List.copyOf(overall), List.copyOf(byKind));
  }

  private CampaignCadenceWindow cadenceWindowForKind(
      String kind, ResourceBundle bundle, UsageMetricsService.ObservabilityWindow window) {
    List<CampaignCadenceWindow> best = bestWindows(window, preferredModulesForKind(kind), 1, bundle);
    if (!best.isEmpty()) {
      CampaignCadenceWindow top = best.get(0);
      return new CampaignCadenceWindow(
          bundleText(bundle, "campaigns_kind_" + kind), top.slotLabel(), top.detailLabel());
    }
    return new CampaignCadenceWindow(
        bundleText(bundle, "campaigns_kind_" + kind),
        bundleText(bundle, "campaigns_admin_cadence_no_window"),
        bundleText(bundle, "campaigns_admin_cadence_no_window"));
  }

  private List<CampaignCadenceWindow> bestWindows(
      UsageMetricsService.ObservabilityWindow window,
      List<String> modules,
      int limit,
      ResourceBundle bundle) {
    Map<String, Long> totalsBySlot = new LinkedHashMap<>();
    for (UsageMetricsService.ObservabilitySeriesSnapshot row : window.modules()) {
      if (!modules.contains(row.code())) {
        continue;
      }
      List<Long> counts = row.counts();
      for (int i = 0; i < counts.size() && i < window.hourLabels().size(); i++) {
        totalsBySlot.merge(window.hourLabels().get(i), counts.get(i), Long::sum);
      }
    }
    return totalsBySlot.entrySet().stream()
        .filter(entry -> entry.getValue() > 0L)
        .sorted(
            Comparator.comparing(Map.Entry<String, Long>::getValue, Comparator.reverseOrder())
                .thenComparing(Map.Entry::getKey))
        .limit(limit)
        .map(
            entry ->
                new CampaignCadenceWindow(
                    "",
                    named(bundle, "campaigns_admin_cadence_window_slot", Map.of("slot", entry.getKey())),
                    named(
                        bundle,
                        "campaigns_admin_cadence_window_support",
                        Map.of("score", String.valueOf(entry.getValue())))))
        .toList();
  }

  private List<String> preferredModulesForKind(String kind) {
    return switch (kind) {
      case KIND_PRODUCT_PULSE -> List.of("home", "project", "profile");
      case KIND_CHALLENGE_SPOTLIGHT -> List.of("community", "profile", "home");
      case KIND_COMMUNITY_SPOTLIGHT -> List.of("community", "home");
      case KIND_EVENT_SPOTLIGHT -> List.of("events", "community", "home");
      default -> List.of("home", "community", "events");
    };
  }

  private CampaignOperationsSummary summarize(
      CampaignStateSnapshot snapshot, ResourceBundle bundle, Locale locale) {
    int draftCount = 0;
    int approvedCount = 0;
    int scheduledCount = 0;
    int publishedCount = 0;
    int linkedinPendingCount = 0;
    int linkedinCompletedCount = 0;
    Instant lastPublishedAt = null;
    for (CampaignDraftState draft : snapshot.drafts()) {
      switch (draft.workflowState()) {
        case DRAFT -> draftCount++;
        case APPROVED -> approvedCount++;
        case SCHEDULED -> scheduledCount++;
        case PUBLISHED -> publishedCount++;
      }
      if (eligibleForLinkedinHandoff(draft)) {
        if (draft.publishedChannels().containsKey("linkedin")) {
          linkedinCompletedCount++;
        } else {
          linkedinPendingCount++;
        }
      }
      for (Instant publishedAt : draft.publishedChannels().values()) {
        if (publishedAt != null && (lastPublishedAt == null || publishedAt.isAfter(lastPublishedAt))) {
          lastPublishedAt = publishedAt;
        }
      }
    }
    return new CampaignOperationsSummary(
        draftCount,
        approvedCount,
        scheduledCount,
        publishedCount,
        linkedinPendingCount,
        linkedinCompletedCount,
        localizedDateTime(lastPublishedAt, locale),
        snapshot.drafts().size());
  }

  private List<CampaignDraftState> filterDrafts(
      List<CampaignDraftState> drafts,
      CampaignAdminFilters filters,
      ResourceBundle bundle,
      Locale locale,
      Map<String, String> cadenceByKind) {
    if (filters == null || !filters.hasAny()) {
      return List.copyOf(drafts);
    }
    return drafts.stream()
        .filter(draft -> matchesDraftFilters(draft, filters, bundle, locale, cadenceByKind))
        .toList();
  }

  private boolean matchesDraftFilters(
      CampaignDraftState draft,
      CampaignAdminFilters filters,
      ResourceBundle bundle,
      Locale locale,
      Map<String, String> cadenceByKind) {
    if (filters == null) {
      return true;
    }
    if (!filters.workflow().isBlank()
        && !draft.workflowState().name().equalsIgnoreCase(filters.workflow())) {
      return false;
    }
    if (!filters.kind().isBlank() && !draft.kind().equalsIgnoreCase(filters.kind())) {
      return false;
    }
    if (!filters.channel().isBlank()
        && !draft.suggestedChannels().contains(filters.channel())
        && !draft.publishedChannels().containsKey(filters.channel())) {
      return false;
    }
    if (filters.query().isBlank()) {
      return true;
    }
    CampaignPreviewCard preview = toPreview(draft, bundle, locale, cadenceByKind);
    String haystack =
        String.join(
            "\n",
            draft.id(),
            draft.kind(),
            preview.title(),
            preview.body(),
            preview.ctaLabel(),
            String.join(" ", preview.evidence()));
    return normalizeSearch(haystack).contains(normalizeSearch(filters.query()));
  }

  private String normalizeSearch(String raw) {
    return raw == null ? "" : raw.toLowerCase(Locale.ROOT).trim();
  }

  private List<CampaignRecentActivity> recentActivity(
      List<CampaignDraftState> drafts, ResourceBundle bundle, Locale locale) {
    Map<String, String> cadenceByKind = new LinkedHashMap<>();
    for (CampaignCadenceWindow window : cadenceGuidance(bundle).windowsByKind()) {
      cadenceByKind.put(window.label(), window.slotLabel());
    }
    return drafts.stream()
        .sorted(
            Comparator.comparing(CampaignDraftState::updatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(CampaignDraftState::generatedAt, Comparator.reverseOrder()))
        .limit(6)
        .map(
            draft ->
                new CampaignRecentActivity(
                    toPreview(draft, bundle, locale, cadenceByKind).title(),
                    bundleText(
                        bundle,
                        "campaigns_workflow_" + draft.workflowState().name().toLowerCase(Locale.ROOT)),
                    draft.lastPublishOutcome().isBlank()
                        ? bundleText(bundle, "campaigns_admin_recent_activity_updated")
                        : bundleText(bundle, "campaigns_publish_outcome_" + draft.lastPublishOutcome()),
                    localizedDateTime(
                        draft.updatedAt() != null ? draft.updatedAt() : draft.generatedAt(), locale)))
        .toList();
  }

  private boolean eligibleForLinkedinHandoff(CampaignDraftState draft) {
    return draft != null
        && draft.suggestedChannels().contains("linkedin")
        && draft.workflowState() != CampaignWorkflowState.DRAFT;
  }

  private CampaignLinkedinHandoff toLinkedinHandoff(
      CampaignDraftState draft, ResourceBundle bundle, Locale locale) {
    Map<String, String> cadenceByKind = new LinkedHashMap<>();
    for (CampaignCadenceWindow window : cadenceGuidance(bundle).windowsByKind()) {
      cadenceByKind.put(window.label(), window.slotLabel());
    }
    CampaignPreviewCard preview = toPreview(draft, bundle, locale, cadenceByKind);
    boolean completed = draft.publishedChannels().containsKey("linkedin");
    return new CampaignLinkedinHandoff(
        draft.id(),
        preview.title(),
        preview.workflowLabel(),
        preview.body(),
        preview.ctaLabel(),
        CampaignPublishMessageSupport.trackedUrl(draft, "linkedin"),
        linkedinHeadline(preview.title(), bundle),
        linkedinMessage(draft, preview, bundle, CampaignPublishMessageSupport.trackedUrl(draft, "linkedin")),
        completed ? bundleText(bundle, "campaigns_admin_linkedin_done") : bundleText(bundle, "campaigns_admin_linkedin_pending"),
        completed,
        localizedDateTime(draft.publishedChannels().get("linkedin"), locale));
  }

  private CampaignPreviewPack toPreviewPack(
      CampaignDraftState draft, CampaignPreviewCard preview, ResourceBundle bundle) {
    return new CampaignPreviewPack(
        draft.id(),
        preview.kindLabel(),
        preview.title(),
        preview.workflowLabel(),
        preview.recommendedWindowLabel(),
        preview.suggestedChannels().stream()
            .map(channelLabel -> toChannelPreview(draft, preview, channelLabel, bundle))
            .toList());
  }

  private CampaignChannelPreview toChannelPreview(
      CampaignDraftState draft,
      CampaignPreviewCard preview,
      String channelLabel,
      ResourceBundle bundle) {
    String channelCode = channelCodeForLabel(bundle, channelLabel);
    String landingUrl = CampaignPublishMessageSupport.trackedUrl(draft, channelCode);
    String headline = preview.title();
    String message =
        switch (channelCode) {
          case "discord" -> discordMessage(preview, landingUrl);
          case "bluesky" -> socialMessage(preview, bundle, true, landingUrl);
          case "mastodon" -> socialMessage(preview, bundle, false, landingUrl);
          case "linkedin" -> linkedinMessage(draft, preview, bundle, landingUrl);
          default -> socialMessage(preview, bundle, false, landingUrl);
        };
    int charCount = message.length();
    int limit =
        switch (channelCode) {
          case "bluesky" -> 300;
          case "mastodon" -> 500;
          case "linkedin" -> 3000;
          default -> 2000;
        };
    String readinessKey;
    if ("linkedin".equals(channelCode)) {
      readinessKey = "campaigns_admin_preview_status_manual";
    } else if (charCount > limit) {
      readinessKey = "campaigns_admin_preview_status_trim";
    } else {
      readinessKey = "campaigns_admin_preview_status_ready";
    }
    boolean published = draft.publishedChannels().containsKey(channelCode);
    boolean retryReady = isManualRetryAllowed(draft, channelCode, Instant.now());
    return new CampaignChannelPreview(
        channelCode,
        channelLabel,
        headline,
        message,
        landingUrl,
        named(bundle, "campaigns_admin_preview_length", Map.of("count", String.valueOf(charCount), "limit", String.valueOf(limit))),
        bundleText(bundle, readinessKey),
        published,
        retryReady);
  }

  private String channelCodeForLabel(ResourceBundle bundle, String channelLabel) {
    for (String channel : List.of("discord", "bluesky", "mastodon", "linkedin")) {
      if (bundleText(bundle, "campaigns_channel_" + channel).equals(channelLabel)) {
        return channel;
      }
    }
    return "";
  }

  private String discordMessage(CampaignPreviewCard preview, String landingUrl) {
    return safe(preview.title())
        + "\n"
        + safe(preview.body())
        + "\n"
        + safe(preview.ctaLabel())
        + ": "
        + safe(landingUrl);
  }

  private String socialMessage(
      CampaignPreviewCard preview, ResourceBundle bundle, boolean compact, String landingUrl) {
    String separator = compact ? " — " : "\n";
    String cta = safe(preview.ctaLabel()) + ": " + safe(landingUrl);
    String evidence =
        preview.evidence().isEmpty()
            ? ""
            : separator + named(bundle, "campaigns_admin_preview_evidence", Map.of("evidence", String.join(" · ", preview.evidence())));
    return safe(preview.title()) + separator + safe(preview.body()) + evidence + separator + cta;
  }

  private List<CampaignAttributionSummary> attributionSummary(
      List<CampaignDraftState> drafts, ResourceBundle bundle) {
    Map<String, Long> metrics = usageMetricsService.snapshot();
    List<CampaignAttributionSummary> rows = new ArrayList<>();
    for (CampaignDraftState draft : drafts) {
      List<CampaignAttributionChannel> channels = new ArrayList<>();
      long total = 0L;
      for (String channel : List.of("discord", "bluesky", "mastodon", "linkedin")) {
        long visits = metrics.getOrDefault("funnel:campaign.visit." + channel + "." + draft.id(), 0L);
        total += visits;
        if (visits > 0L) {
          channels.add(
              new CampaignAttributionChannel(
                  channel,
                  bundleText(bundle, "campaigns_channel_" + channel),
                  String.valueOf(visits)));
        }
      }
      rows.add(
          new CampaignAttributionSummary(
              draft.id(),
              bundleText(bundle, "campaigns_kind_" + draft.kind()),
              titleForAttribution(draft, bundle),
              String.valueOf(total),
              List.copyOf(channels)));
    }
    rows.sort(Comparator.comparingLong((CampaignAttributionSummary row) -> Long.parseLong(row.totalVisits())).reversed());
    return List.copyOf(rows);
  }

  private CampaignBusinessDashboard businessDashboard(
      List<CampaignPreviewCard> cards,
      List<CampaignAttributionSummary> attribution,
      ResourceBundle bundle) {
    Map<String, CampaignPreviewCard> cardsById = new LinkedHashMap<>();
    for (CampaignPreviewCard card : cards) {
      cardsById.put(card.id(), card);
    }
    long totalVisits = 0L;
    int draftsWithTraffic = 0;
    Map<String, Long> channelTotals = new LinkedHashMap<>();
    List<CampaignBusinessHighlight> highlights = new ArrayList<>();
    CampaignAttributionSummary topDraft = null;
    for (CampaignAttributionSummary item : attribution) {
      long rowTotal = parseMetricCount(item.totalVisits());
      totalVisits += rowTotal;
      if (rowTotal > 0L) {
        draftsWithTraffic++;
        if (topDraft == null) {
          topDraft = item;
        }
        for (CampaignAttributionChannel channel : item.channels()) {
          channelTotals.merge(channel.channelLabel(), parseMetricCount(channel.visitsLabel()), Long::sum);
        }
        CampaignPreviewCard card = cardsById.get(item.draftId());
        highlights.add(
            new CampaignBusinessHighlight(
                item.draftId(),
                item.title(),
                item.kindLabel(),
                card == null ? bundleText(bundle, "campaigns_admin_business_none") : safe(card.workflowLabel()),
                item.totalVisits(),
                dominantChannelLabel(item.channels(), bundle)));
      }
    }
    highlights.sort(
        Comparator.comparingLong((CampaignBusinessHighlight item) -> parseMetricCount(item.totalVisitsLabel()))
            .reversed());
    String bestChannelLabel =
        channelTotals.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(bundleText(bundle, "campaigns_admin_business_none"));
    String topDraftTitle = topDraft == null ? bundleText(bundle, "campaigns_admin_business_none") : topDraft.title();
    String topDraftId = topDraft == null ? "" : topDraft.draftId();
    long averageVisits = draftsWithTraffic == 0 ? 0L : Math.round((double) totalVisits / draftsWithTraffic);
    return new CampaignBusinessDashboard(
        String.valueOf(totalVisits),
        draftsWithTraffic,
        String.valueOf(averageVisits),
        bestChannelLabel,
        topDraftTitle,
        topDraftId,
        topDraft != null,
        List.copyOf(highlights.stream().limit(3).toList()));
  }

  private CampaignRolloutChecklist rolloutChecklist(ResourceBundle bundle, Locale locale) {
    CampaignOperationsStateSnapshot operationsState = currentOperationsState();
    List<CampaignRolloutChannel> channels =
        effectivePublisherStatuses(true).stream()
            .map(status -> toRolloutChannel(status, operationsState, bundle, locale))
            .toList();
    int readyCount = 0;
    int blockedCount = 0;
    int dryRunCount = 0;
    int acknowledgedCount = 0;
    for (CampaignRolloutChannel channel : channels) {
      if (channel.ready()) {
        readyCount++;
      } else {
        blockedCount++;
      }
      if (channel.dryRun()) {
        dryRunCount++;
      }
      if (channel.acknowledged()) {
        acknowledgedCount++;
      }
    }
    String statusCode;
    if (readyCount == channels.size() && !channels.isEmpty()) {
      statusCode = "good";
    } else if (readyCount > 0) {
      statusCode = "watch";
    } else {
      statusCode = "danger";
    }
    return new CampaignRolloutChecklist(
        statusCode,
        bundleText(
            bundle,
            switch (statusCode) {
              case "good" -> "campaigns_admin_rollout_status_ready";
              case "watch" -> "campaigns_admin_rollout_status_watch";
              default -> "campaigns_admin_rollout_status_blocked";
            }),
        readyCount,
        blockedCount,
        dryRunCount,
        acknowledgedCount,
        operationsState.pilotLiveChannel().isBlank()
            ? bundleText(bundle, "campaigns_admin_rollout_pilot_none")
            : bundleText(bundle, "campaigns_channel_" + operationsState.pilotLiveChannel()),
        operationsState.pilotLiveChannelUpdatedAt() == null
            ? "—"
            : localizedDateTime(operationsState.pilotLiveChannelUpdatedAt(), locale),
        operationsState.pilotLiveChannelUpdatedBy().isBlank()
            ? "—"
            : operationsState.pilotLiveChannelUpdatedBy(),
        operationsState.pilotLiveArmed()
            ? bundleText(bundle, "campaigns_admin_rollout_activation_armed")
            : bundleText(bundle, "campaigns_admin_rollout_activation_disarmed"),
        operationsState.pilotLiveArmedAt() == null
            ? "—"
            : localizedDateTime(operationsState.pilotLiveArmedAt(), locale),
        operationsState.pilotLiveArmedBy().isBlank()
            ? "—"
            : operationsState.pilotLiveArmedBy(),
        localizedDateTime(Instant.now(), locale),
        List.copyOf(channels));
  }

  private CampaignRolloutChannel toRolloutChannel(
      CampaignPublisherStatus status,
      CampaignOperationsStateSnapshot operationsState,
      ResourceBundle bundle,
      Locale locale) {
    boolean ready =
        status.globalEnabled() && status.channelEnabled() && status.configured() && !status.dryRun();
    CampaignGoLiveAck goLiveAck = operationsState.goLiveAcknowledgement(status.channel());
    String stateCode;
    String recommendationKey;
    if (ready) {
      stateCode = "good";
      recommendationKey = "campaigns_admin_rollout_recommendation_ready";
    } else if (!status.globalEnabled()) {
      stateCode = "danger";
      recommendationKey = "campaigns_admin_rollout_recommendation_enable_global";
    } else if (!status.channelEnabled()) {
      stateCode = "watch";
      recommendationKey = "campaigns_admin_rollout_recommendation_enable_channel";
    } else if (!status.configured()) {
      stateCode = "danger";
      recommendationKey = "campaigns_admin_rollout_recommendation_add_config";
    } else {
      stateCode = "watch";
      recommendationKey = "campaigns_admin_rollout_recommendation_disable_dry_run";
    }
    return new CampaignRolloutChannel(
        status.channel(),
        bundleText(bundle, "campaigns_channel_" + status.channel()),
        stateCode,
        bundleText(
            bundle,
            switch (stateCode) {
              case "good" -> "campaigns_admin_rollout_status_ready";
              case "watch" -> "campaigns_admin_rollout_status_watch";
              default -> "campaigns_admin_rollout_status_blocked";
            }),
        bundleText(bundle, recommendationKey),
        ready,
        goLiveAck.acknowledged(),
        goLiveAck.acknowledged()
            ? bundleText(bundle, "campaigns_admin_rollout_ack_acknowledged")
            : bundleText(bundle, "campaigns_admin_rollout_ack_pending"),
        goLiveAck.acknowledgedAt() == null ? "—" : localizedDateTime(goLiveAck.acknowledgedAt(), locale),
        goLiveAck.acknowledgedBy().isBlank() ? "—" : goLiveAck.acknowledgedBy(),
        operationsState.hasPilotLiveChannel() && operationsState.isPilotLiveChannel(status.channel()),
        operationsState.isPilotLiveActive(status.channel()),
        status.globalEnabled(),
        status.channelEnabled(),
        status.configured(),
        status.dryRun(),
        status.minInterval().isZero()
            ? bundleText(bundle, "campaigns_admin_cadence_no_window")
            : humanDuration(
                status.minInterval().toHours(),
                status.minInterval().minusHours(status.minInterval().toHours()).toMinutes()));
  }

  private long parseMetricCount(String value) {
    if (value == null || value.isBlank()) {
      return 0L;
    }
    try {
      return Long.parseLong(value.trim());
    } catch (NumberFormatException ignored) {
      return 0L;
    }
  }

  private String dominantChannelLabel(
      List<CampaignAttributionChannel> channels, ResourceBundle bundle) {
    return channels.stream()
        .max(Comparator.comparingLong(channel -> parseMetricCount(channel.visitsLabel())))
        .map(CampaignAttributionChannel::channelLabel)
        .orElse(bundleText(bundle, "campaigns_admin_business_none"));
  }

  private String titleForAttribution(CampaignDraftState draft, ResourceBundle bundle) {
    return switch (draft.kind()) {
      case KIND_PRODUCT_PULSE -> named(bundle, "campaigns_product_pulse_title", Map.of("version", value(draft, "version")));
      case KIND_CHALLENGE_SPOTLIGHT ->
          named(
              bundle,
              "campaigns_challenge_title",
              Map.of("challengeTitle", bundleText(bundle, value(draft, "challengeTitleKey"))));
      case KIND_COMMUNITY_SPOTLIGHT -> named(bundle, "campaigns_community_title", Map.of("title", value(draft, "title")));
      case KIND_EVENT_SPOTLIGHT -> named(bundle, "campaigns_event_title", Map.of("eventTitle", value(draft, "eventTitle")));
      default -> draft.id();
    };
  }

  private String linkedinHeadline(String title, ResourceBundle bundle) {
    return named(bundle, "campaigns_admin_linkedin_headline", Map.of("title", safe(title)));
  }

  private String linkedinMessage(
      CampaignDraftState draft, CampaignPreviewCard preview, ResourceBundle bundle, String landingUrl) {
    return named(
        bundle,
        "campaigns_admin_linkedin_message",
        Map.of(
            "title", safe(preview.title()),
            "body", safe(preview.body()),
            "ctaLabel", safe(preview.ctaLabel()),
            "ctaUrl", safe(landingUrl),
            "evidence", String.join(" · ", preview.evidence())));
  }

  private CampaignQueueHealth queueHealth(
      CampaignStateSnapshot snapshot, ResourceBundle bundle, Locale locale) {
    int staleDraftCount = 0;
    int staleApprovedCount = 0;
    int overdueScheduledCount = 0;
    int blockedPublicationCount = 0;
    int staleLinkedinCount = 0;
    Instant now = Instant.now();
    for (CampaignDraftState draft : snapshot.drafts()) {
      Instant baseline = effectiveUpdatedAt(draft);
      switch (draft.workflowState()) {
        case DRAFT -> {
          if (isOlderThan(baseline, now, STALE_DRAFT_WINDOW)) {
            staleDraftCount++;
          }
        }
        case APPROVED -> {
          if (isOlderThan(baseline, now, STALE_APPROVED_WINDOW)) {
            staleApprovedCount++;
          }
        }
        case SCHEDULED -> {
          if (isOverdue(draft, now)) {
            overdueScheduledCount++;
            if (isPublishBlocked(draft)) {
              blockedPublicationCount++;
            }
          }
        }
        case PUBLISHED -> {
          if (hasPendingLinkedinHandoff(draft)
              && isOlderThan(baseline, now, STALE_LINKEDIN_HANDOFF_WINDOW)) {
            staleLinkedinCount++;
          }
        }
      }
    }
    int attentionCount =
        staleDraftCount
            + staleApprovedCount
            + overdueScheduledCount
            + blockedPublicationCount
            + staleLinkedinCount;
    String statusCode;
    if (overdueScheduledCount > 0 || blockedPublicationCount > 0) {
      statusCode = "high";
    } else if (attentionCount > 0) {
      statusCode = "watch";
    } else {
      statusCode = "healthy";
    }
    return new CampaignQueueHealth(
        statusCode,
        bundleText(bundle, "campaigns_admin_queue_health_status_" + statusCode),
        attentionCount,
        staleDraftCount,
        staleApprovedCount,
        overdueScheduledCount,
        blockedPublicationCount,
        staleLinkedinCount,
        localizedDateTime(now, locale));
  }

  private CampaignPublishRecoverySummary publishRecoverySummary(
      CampaignStateSnapshot snapshot, ResourceBundle bundle, Locale locale) {
    int retryableCount = 0;
    int blockedCount = 0;
    int manualCount = 0;
    Instant now = Instant.now();
    for (CampaignDraftState draft : snapshot.drafts()) {
      PublishRecoveryDescriptor descriptor = publishRecoveryDescriptor(draft, bundle, locale, now);
      if (descriptor == null) {
        continue;
      }
      switch (descriptor.stateCode()) {
        case "retryable" -> retryableCount++;
        case "blocked" -> blockedCount++;
        case "manual" -> manualCount++;
        default -> {
        }
      }
    }
    int totalActionable = retryableCount + blockedCount + manualCount;
    String statusCode;
    if (retryableCount > 0) {
      statusCode = "high";
    } else if (blockedCount > 0 || manualCount > 0) {
      statusCode = "watch";
    } else {
      statusCode = "healthy";
    }
    return new CampaignPublishRecoverySummary(
        statusCode,
        bundleText(bundle, "campaigns_admin_recovery_status_" + statusCode),
        totalActionable,
        retryableCount,
        blockedCount,
        manualCount,
        localizedDateTime(now, locale));
  }

  private List<CampaignPublishRecoveryItem> publishRecoveryItems(
      List<CampaignDraftState> drafts, ResourceBundle bundle, Locale locale) {
    Instant now = Instant.now();
    Map<String, String> cadenceByKind = new LinkedHashMap<>();
    for (CampaignCadenceWindow window : cadenceGuidance(bundle).windowsByKind()) {
      cadenceByKind.put(window.label(), window.slotLabel());
    }
    return drafts.stream()
        .map(draft -> toPublishRecoveryItem(draft, bundle, locale, now, cadenceByKind))
        .flatMap(Optional::stream)
        .sorted(
            Comparator.comparing(CampaignPublishRecoveryItem::severityRank)
                .thenComparing(CampaignPublishRecoveryItem::referenceAt, Comparator.reverseOrder()))
        .limit(8)
        .toList();
  }

  private Optional<CampaignPublishRecoveryItem> toPublishRecoveryItem(
      CampaignDraftState draft,
      ResourceBundle bundle,
      Locale locale,
      Instant now,
      Map<String, String> cadenceByKind) {
    PublishRecoveryDescriptor descriptor = publishRecoveryDescriptor(draft, bundle, locale, now);
    if (descriptor == null) {
      return Optional.empty();
    }
    CampaignPreviewCard preview = toPreview(draft, bundle, locale, cadenceByKind);
    return Optional.of(
        new CampaignPublishRecoveryItem(
            draft.id(),
            preview.title(),
            preview.kindLabel(),
            preview.workflowLabel(),
            descriptor.channelLabel(),
            descriptor.outcomeLabel(),
            descriptor.stateCode(),
            descriptor.stateLabel(),
            descriptor.recommendationLabel(),
            descriptor.actionLabel(),
            descriptor.ageLabel(),
            descriptor.badgeClass(),
            descriptor.severityRank(),
            descriptor.referenceAt()));
  }

  private PublishRecoveryDescriptor publishRecoveryDescriptor(
      CampaignDraftState draft, ResourceBundle bundle, Locale locale, Instant now) {
    if (draft == null) {
      return null;
    }
    String channel = unresolvedAutomationChannel(draft);
    String outcomeCode = safeRecoveryOutcome(draft.lastPublishOutcome());
    if (outcomeCode.isBlank()
        && draft.workflowState() == CampaignWorkflowState.SCHEDULED
        && isOverdue(draft, now)
        && channel != null) {
      outcomeCode = scheduleBlockerCode(channel);
    }
    if (outcomeCode.isBlank()) {
      return null;
    }
    String normalizedCode = normalizeRecoveryCode(outcomeCode);
    String stateCode = recoveryStateCode(normalizedCode);
    if (stateCode.isBlank()) {
      return null;
    }
    Instant referenceAt = publishReferenceAt(draft);
    String channelLabel =
        channel == null ? bundleText(bundle, "campaigns_admin_filter_all") : bundleText(bundle, "campaigns_channel_" + channel);
    return new PublishRecoveryDescriptor(
        normalizedCode,
        stateCode,
        bundleText(bundle, "campaigns_admin_recovery_state_" + stateCode),
        channelLabel,
        bundleText(bundle, "campaigns_publish_outcome_" + normalizedCode),
        recoveryRecommendation(bundle, stateCode, channelLabel),
        recoveryAction(bundle, stateCode),
        recoveryBadgeClass(stateCode),
        "retryable".equals(stateCode) ? 0 : ("blocked".equals(stateCode) ? 1 : 2),
        recoveryAgeLabel(referenceAt, now, bundle),
        referenceAt);
  }

  private String safeRecoveryOutcome(String outcomeCode) {
    return outcomeCode == null ? "" : outcomeCode.trim();
  }

  private String normalizeRecoveryCode(String outcomeCode) {
    if (outcomeCode == null || outcomeCode.isBlank()) {
      return "";
    }
    if (outcomeCode.endsWith("_failed")) {
      return "publish_failed";
    }
    if (outcomeCode.endsWith("_error")) {
      return "publish_error";
    }
    if (outcomeCode.endsWith("_not_configured")) {
      return "not_configured";
    }
    if (outcomeCode.endsWith("_disabled")) {
      return "channel_disabled";
    }
    if ("dry_run".equals(outcomeCode)
        || "global_disabled".equals(outcomeCode)
        || "publish_paused".equals(outcomeCode)
        || "channel_paused".equals(outcomeCode)
        || "channel_not_supported".equals(outcomeCode)) {
      return outcomeCode;
    }
    if ("unsupported".equals(outcomeCode)) {
      return "channel_not_supported";
    }
    return "";
  }

  private String recoveryStateCode(String normalizedCode) {
    return switch (normalizedCode) {
      case "publish_failed", "publish_error" -> "retryable";
      case "global_disabled", "not_configured", "channel_disabled", "publish_paused", "channel_paused", "channel_not_supported", "unsupported" ->
          "blocked";
      case "dry_run" -> "manual";
      default -> "";
    };
  }

  private String recoveryRecommendation(ResourceBundle bundle, String stateCode, String channelLabel) {
    return named(
        bundle,
        "campaigns_admin_recovery_recommend_" + stateCode,
        Map.of("channel", safe(channelLabel)));
  }

  private String recoveryAction(ResourceBundle bundle, String stateCode) {
    return bundleText(bundle, "campaigns_admin_recovery_action_" + stateCode);
  }

  private String recoveryBadgeClass(String stateCode) {
    return switch (stateCode) {
      case "retryable" -> "high";
      case "blocked", "manual" -> "watch";
      default -> "healthy";
    };
  }

  private Instant publishReferenceAt(CampaignDraftState draft) {
    if (draft.lastPublishAttemptAt() != null) {
      return draft.lastPublishAttemptAt();
    }
    if (draft.scheduledFor() != null) {
      return draft.scheduledFor();
    }
    return effectiveUpdatedAt(draft);
  }

  private String recoveryAgeLabel(Instant referenceAt, Instant now, ResourceBundle bundle) {
    Duration age = referenceAt == null ? Duration.ZERO : Duration.between(referenceAt, now);
    long hours = Math.max(0L, age.toHours());
    long minutes = Math.max(0L, age.toMinutes());
    return named(
        bundle,
        "campaigns_admin_recovery_age",
        Map.of("duration", humanDuration(hours, minutes)));
  }

  private String unresolvedAutomationChannel(CampaignDraftState draft) {
    if (draft == null) {
      return null;
    }
    for (String channel : draft.suggestedChannels()) {
      if ("linkedin".equals(channel) || draft.publishedChannels().containsKey(channel)) {
        continue;
      }
      return channel;
    }
    return null;
  }

  private List<CampaignQueueRiskItem> queueRisks(
      List<CampaignDraftState> drafts, ResourceBundle bundle, Locale locale) {
    Instant now = Instant.now();
    Map<String, String> cadenceByKind = new LinkedHashMap<>();
    for (CampaignCadenceWindow window : cadenceGuidance(bundle).windowsByKind()) {
      cadenceByKind.put(window.label(), window.slotLabel());
    }
    return drafts.stream()
        .map(draft -> toRiskItem(draft, bundle, locale, now, cadenceByKind))
        .flatMap(Optional::stream)
        .sorted(
            Comparator.comparing(CampaignQueueRiskItem::severityRank)
                .thenComparing(CampaignQueueRiskItem::referenceAt, Comparator.reverseOrder()))
        .limit(8)
        .toList();
  }

  private Optional<CampaignQueueRiskItem> toRiskItem(
      CampaignDraftState draft,
      ResourceBundle bundle,
      Locale locale,
      Instant now,
      Map<String, String> cadenceByKind) {
    String riskCode = null;
    String badgeClass = null;
    Instant referenceAt = effectiveUpdatedAt(draft);
    if (draft.workflowState() == CampaignWorkflowState.SCHEDULED && isOverdue(draft, now)) {
      referenceAt = draft.scheduledFor() != null ? draft.scheduledFor() : referenceAt;
      if (isPublishBlocked(draft)) {
        riskCode = "publish_blocked";
        badgeClass = "high";
      } else {
        riskCode = "scheduled_overdue";
        badgeClass = "high";
      }
    } else if (draft.workflowState() == CampaignWorkflowState.APPROVED
        && !scheduleReadiness(draft).ready()) {
      riskCode = "approved_blocked";
      badgeClass = "watch";
    } else if (draft.workflowState() == CampaignWorkflowState.APPROVED
        && isOlderThan(referenceAt, now, STALE_APPROVED_WINDOW)) {
      riskCode = "approved_stale";
      badgeClass = "watch";
    } else if (draft.workflowState() == CampaignWorkflowState.DRAFT
        && isOlderThan(referenceAt, now, STALE_DRAFT_WINDOW)) {
      riskCode = "draft_stale";
      badgeClass = "watch";
    } else if (draft.workflowState() == CampaignWorkflowState.PUBLISHED
        && hasPendingLinkedinHandoff(draft)
        && isOlderThan(referenceAt, now, STALE_LINKEDIN_HANDOFF_WINDOW)) {
      riskCode = "linkedin_pending";
      badgeClass = "watch";
    }
    if (riskCode == null) {
      return Optional.empty();
    }
    CampaignPreviewCard preview = toPreview(draft, bundle, locale, cadenceByKind);
    return Optional.of(
        new CampaignQueueRiskItem(
            draft.id(),
            preview.title(),
            preview.kindLabel(),
            preview.workflowLabel(),
            bundleText(bundle, "campaigns_admin_queue_risk_" + riskCode),
            bundleText(bundle, "campaigns_admin_queue_recommend_" + riskCode),
            riskAgeLabel(draft, riskCode, now, bundle),
            badgeClass,
            "high".equals(badgeClass) ? 0 : 1,
            referenceAt == null ? Instant.EPOCH : referenceAt));
  }

  private String riskAgeLabel(
      CampaignDraftState draft, String riskCode, Instant now, ResourceBundle bundle) {
    Instant referenceAt =
        switch (riskCode) {
          case "scheduled_overdue", "publish_blocked" ->
              draft.scheduledFor() != null ? draft.scheduledFor() : effectiveUpdatedAt(draft);
          default -> effectiveUpdatedAt(draft);
        };
    Duration age = referenceAt == null ? Duration.ZERO : Duration.between(referenceAt, now);
    long hours = Math.max(0L, age.toHours());
    long minutes = Math.max(0L, age.toMinutes());
    if ("scheduled_overdue".equals(riskCode) || "publish_blocked".equals(riskCode)) {
      return named(
          bundle,
          "campaigns_admin_queue_age_overdue",
          Map.of("duration", humanDuration(hours, minutes)));
    }
    return named(
        bundle,
        "campaigns_admin_queue_age_updated",
        Map.of("duration", humanDuration(hours, minutes)));
  }

  private String humanDuration(long hours, long minutes) {
    if (hours >= 24) {
      return (hours / 24) + "d";
    }
    if (hours > 0) {
      return hours + "h";
    }
    return Math.max(1L, minutes) + "m";
  }

  private Instant effectiveUpdatedAt(CampaignDraftState draft) {
    if (draft.updatedAt() != null) {
      return draft.updatedAt();
    }
    if (draft.approvedAt() != null) {
      return draft.approvedAt();
    }
    return draft.generatedAt();
  }

  private boolean isOlderThan(Instant baseline, Instant now, Duration threshold) {
    return baseline != null && baseline.plus(threshold).isBefore(now);
  }

  private boolean isOverdue(CampaignDraftState draft, Instant now) {
    return draft.scheduledFor() != null && draft.scheduledFor().plus(OVERDUE_SCHEDULE_WINDOW).isBefore(now);
  }

  private boolean isPublishBlocked(CampaignDraftState draft) {
    boolean hasPendingAutomationChannel = false;
    for (CampaignPublisherStatus status : effectivePublisherStatuses(true)) {
      if (!draft.suggestedChannels().contains(status.channel())
          || draft.publishedChannels().containsKey(status.channel())) {
        continue;
      }
      hasPendingAutomationChannel = true;
      if (status.globalEnabled() && status.channelEnabled() && status.configured()) {
        return false;
      }
    }
    return hasPendingAutomationChannel;
  }

  private boolean isManualRetryAllowed(CampaignDraftState draft, String channel, Instant now) {
    if (draft == null || channel == null || channel.isBlank()) {
      return false;
    }
    if ((draft.workflowState() != CampaignWorkflowState.SCHEDULED
            && draft.workflowState() != CampaignWorkflowState.PUBLISHED)
        || draft.scheduledFor() == null
        || draft.scheduledFor().isAfter(now)) {
      return false;
    }
    return draft.suggestedChannels().contains(channel) && !draft.publishedChannels().containsKey(channel);
  }

  private CampaignSchedulingReadiness scheduleReadiness(CampaignDraftState draft) {
    if (draft == null) {
      return new CampaignSchedulingReadiness(false, List.of("none"), List.of());
    }
    List<String> blockerCodes = new ArrayList<>();
    List<String> readyChannels = new ArrayList<>();
    for (String channel : draft.suggestedChannels()) {
      if ("linkedin".equals(channel)) {
        continue;
      }
      String blocker = scheduleBlockerCode(channel);
      if (blocker == null) {
        readyChannels.add(channel);
      } else {
        blockerCodes.add(blocker);
      }
    }
    return new CampaignSchedulingReadiness(!readyChannels.isEmpty(), List.copyOf(blockerCodes), List.copyOf(readyChannels));
  }

  private String scheduleBlockerCode(String channel) {
    CampaignPublisherStatus rawStatus =
        publisherStatuses().stream()
            .filter(item -> item.channel().equals(channel))
            .findFirst()
            .orElse(null);
    if (rawStatus == null) {
      return "unsupported";
    }
    if (!rawStatus.globalEnabled()) {
      return "global_disabled";
    }
    if (rawStatus.dryRun()) {
      return "dry_run";
    }
    if (!rawStatus.configured()) {
      return "not_configured";
    }
    if (!rawStatus.channelEnabled()) {
      return "channel_disabled";
    }
    if (!currentOperationsState.publishAutomationEnabled()) {
      return "publish_paused";
    }
    if (!currentOperationsState.hasPilotLiveChannel()) {
      return "pilot_not_selected";
    }
    if (!currentOperationsState.isChannelAutomationEnabled(channel)) {
      return "channel_paused";
    }
    if (currentOperationsState.hasPilotLiveChannel() && !currentOperationsState.isPilotLiveChannel(channel)) {
      return "pilot_locked";
    }
    if (!currentOperationsState.isPilotLiveActive(channel)) {
      return "pilot_gate_closed";
    }
    return null;
  }

  private CampaignScheduleReadinessView schedulingReadiness(CampaignPreviewCard draft) {
    return new CampaignScheduleReadinessView(
        draft.scheduleReady(),
        draft.scheduleReadinessLabel(),
        draft.scheduleReadinessDetailLabel());
  }

  private boolean hasPendingLinkedinHandoff(CampaignDraftState draft) {
    return eligibleForLinkedinHandoff(draft) && !draft.publishedChannels().containsKey("linkedin");
  }

  private List<CampaignPublisherStatus> publisherStatuses() {
    return List.of(
        discordPublisherService.status(),
        blueskyPublisherService.status(),
        mastodonPublisherService.status());
  }

  private List<CampaignPublisherStatus> effectivePublisherStatuses(boolean respectPublishAutomation) {
    CampaignOperationsStateSnapshot operationsState = currentOperationsState();
    return publisherStatuses().stream()
        .map(status -> applyOperationsState(status, operationsState, respectPublishAutomation))
        .toList();
  }

  private CampaignPublisherStatus applyOperationsState(
      CampaignPublisherStatus status,
      CampaignOperationsStateSnapshot operationsState,
      boolean respectPublishAutomation) {
    boolean globalEnabled =
        status.globalEnabled()
            && (!respectPublishAutomation || operationsState.publishAutomationEnabled());
    boolean channelEnabled =
        status.channelEnabled()
            && operationsState.isChannelAutomationEnabled(status.channel())
            && operationsState.hasPilotLiveChannel()
            && operationsState.isPilotLiveActive(status.channel());
    return new CampaignPublisherStatus(
        status.channel(),
        globalEnabled,
        status.dryRun(),
        channelEnabled,
        status.configured(),
        status.minInterval());
  }

  private List<CampaignAuditTrailEntry> auditTrail(
      CampaignStateSnapshot snapshot,
      List<CampaignDraftState> visibleDrafts,
      CampaignAdminFilters filters,
      ResourceBundle bundle,
      Locale locale) {
    Set<String> visibleIds = new HashSet<>();
    for (CampaignDraftState draft : visibleDrafts) {
      visibleIds.add(draft.id());
    }
    return snapshot.activity().stream()
        .filter(item -> matchesAuditFilters(item, visibleIds, filters))
        .sorted(
            Comparator.comparing(
                CampaignActivityEntry::timestamp,
                Comparator.nullsLast(Comparator.reverseOrder())))
        .limit(18)
        .map(item -> toAuditTrailEntry(item, bundle, locale))
        .toList();
  }

  private boolean matchesAuditFilters(
      CampaignActivityEntry item, Set<String> visibleDraftIds, CampaignAdminFilters filters) {
    if (filters == null || !filters.hasAny()) {
      return true;
    }
    if (item.draftId().isBlank()) {
      return false;
    }
    return visibleDraftIds.contains(item.draftId());
  }

  private CampaignAuditTrailEntry toAuditTrailEntry(
      CampaignActivityEntry item, ResourceBundle bundle, Locale locale) {
    String title =
        item.draftId().isBlank()
            ? bundleText(bundle, "campaigns_admin_audit_system")
            : titleForDraftId(item.draftId(), bundle, locale);
    String kindLabel =
        item.kind().isBlank()
            ? bundleText(bundle, "campaigns_admin_audit_system")
            : bundleText(bundle, "campaigns_kind_" + item.kind());
    String workflowLabel =
        item.workflowState().isBlank()
            ? bundleText(bundle, "campaigns_admin_audit_system")
            : bundleText(bundle, "campaigns_workflow_" + item.workflowState());
    String eventLabel =
        item.eventCode().isBlank()
            ? bundleText(bundle, "campaigns_admin_audit_system")
            : bundleText(bundle, "campaigns_admin_audit_event_" + item.eventCode());
    String channelLabel =
        item.channel().isBlank()
            ? "—"
            : bundleText(bundle, "campaigns_channel_" + item.channel());
    String outcomeLabel =
        item.outcome().isBlank()
            ? "—"
            : bundleText(bundle, "campaigns_publish_outcome_" + item.outcome());
    String actorLabel =
        item.actor().isBlank() || "system".equals(item.actor())
            ? bundleText(bundle, "campaigns_admin_audit_system")
            : item.actor();
    return new CampaignAuditTrailEntry(
        item.draftId(),
        title,
        kindLabel,
        workflowLabel,
        eventLabel,
        channelLabel,
        outcomeLabel,
        actorLabel,
        localizedDateTime(item.timestamp(), locale));
  }

  private String titleForDraftId(String draftId, ResourceBundle bundle, Locale locale) {
    return currentState.drafts().stream()
        .filter(item -> item.id().equals(draftId))
        .findFirst()
        .map(draft -> toPreview(draft, bundle, locale, Map.of()).title())
        .orElse(draftId);
  }

  private String absoluteUrl(String path) {
    String baseUrl = CampaignPublishMessageSupport.normalizeBaseUrl(publicBaseUrl, "https://homedir.opensourcesantiago.io");
    String normalizedPath = safe(path);
    if (normalizedPath.isBlank()) {
      normalizedPath = "/about";
    }
    if (normalizedPath.startsWith("http://") || normalizedPath.startsWith("https://")) {
      return normalizedPath;
    }
    if (!normalizedPath.startsWith("/")) {
      normalizedPath = "/" + normalizedPath;
    }
    return baseUrl + normalizedPath;
  }

  public record CampaignPreviewSnapshot(
      Instant generatedAt,
      List<CampaignPreviewCard> drafts,
      boolean globalPublishingEnabled,
      CampaignAutomationStatus automation,
      List<CampaignPublisherPreviewStatus> publisherStatuses,
      CampaignOperationsSummary summary,
      CampaignBusinessDashboard businessDashboard,
      CampaignRolloutChecklist rolloutChecklist,
      List<CampaignRecentActivity> recentActivity,
      CampaignCadenceGuidance cadenceGuidance,
      List<CampaignPreviewPack> previewPacks,
      List<CampaignAttributionSummary> attribution,
      CampaignPublishRecoverySummary recoverySummary,
      List<CampaignPublishRecoveryItem> recoveryItems,
      CampaignQueueHealth queueHealth,
      List<CampaignQueueRiskItem> queueRisks,
      List<CampaignAuditTrailEntry> auditTrail,
      List<CampaignLinkedinHandoff> linkedinHandoffs) {}

  public record CampaignAdminFilters(String query, String workflow, String kind, String channel) {
    public static CampaignAdminFilters empty() {
      return new CampaignAdminFilters("", "", "", "");
    }

    public boolean hasAny() {
      return !(query == null || query.isBlank())
          || !(workflow == null || workflow.isBlank())
          || !(kind == null || kind.isBlank())
          || !(channel == null || channel.isBlank());
    }
  }

  public record CampaignPublisherPreviewStatus(
      String channelCode,
      String channelLabel,
      boolean globalEnabled,
      boolean dryRun,
      boolean channelEnabled,
      boolean configured,
      String minIntervalLabel) {}

  public record CampaignAutomationStatus(
      boolean refreshAutomationEnabled,
      boolean publishAutomationEnabled,
      String updatedAtLabel,
      String updatedByLabel,
      List<CampaignChannelAutomationStatus> channels) {}

  public record CampaignChannelAutomationStatus(
      String channelCode,
      String channelLabel,
      boolean automationEnabled,
      boolean configEnabled,
      boolean configured,
      boolean effectiveEnabled,
      boolean dryRun,
      String minIntervalLabel) {}

  public record CampaignPreviewCard(
      String id,
      String kindLabel,
      String title,
      String body,
      String ctaLabel,
      String ctaUrl,
      List<String> suggestedChannels,
      List<String> evidence,
      String modeLabel,
      String workflowStateCode,
      String workflowLabel,
      String sourceStatusLabel,
      String scheduledForLabel,
      String publishedChannelsLabel,
      String publisherOutcomeLabel,
      String recommendedWindowLabel,
      boolean scheduleReady,
      String scheduleReadinessLabel,
      String scheduleReadinessDetailLabel) {}

  private record CampaignSchedulingReadiness(
      boolean ready, List<String> blockerCodes, List<String> readyChannels) {}

  public record CampaignScheduleReadinessView(
      boolean ready, String label, String detailLabel) {}

  public record CampaignOperationsSummary(
      int draftCount,
      int approvedCount,
      int scheduledCount,
      int publishedCount,
      int linkedinPendingCount,
      int linkedinCompletedCount,
      String lastPublishedAtLabel,
      int totalDraftCount) {}

  public record CampaignBusinessDashboard(
      String totalVisitsLabel,
      int draftsWithTrafficCount,
      String averageVisitsLabel,
      String bestChannelLabel,
      String topDraftLabel,
      String topDraftId,
      boolean hasTopDraft,
      List<CampaignBusinessHighlight> highlights) {}

  public record CampaignBusinessHighlight(
      String draftId,
      String title,
      String kindLabel,
      String workflowLabel,
      String totalVisitsLabel,
      String bestChannelLabel) {}

  public record CampaignRolloutChecklist(
      String statusCode,
      String statusLabel,
      int readyCount,
      int blockedCount,
      int dryRunCount,
      int acknowledgedCount,
      String pilotChannelLabel,
      String pilotUpdatedAtLabel,
      String pilotUpdatedByLabel,
      String pilotActivationLabel,
      String pilotActivationUpdatedAtLabel,
      String pilotActivationUpdatedByLabel,
      String evaluatedAtLabel,
      List<CampaignRolloutChannel> channels) {}

  public record CampaignRolloutChannel(
      String channelCode,
      String channelLabel,
      String statusCode,
      String statusLabel,
      String recommendationLabel,
      boolean ready,
      boolean acknowledged,
      String acknowledgementLabel,
      String acknowledgedAtLabel,
      String acknowledgedByLabel,
      boolean pilotLive,
      boolean liveArmed,
      boolean globalEnabled,
      boolean channelEnabled,
      boolean configured,
      boolean dryRun,
      String minIntervalLabel) {}

  public record CampaignRecentActivity(
      String title,
      String workflowLabel,
      String activityLabel,
      String timestampLabel) {}

  public record CampaignCadenceGuidance(
      List<CampaignCadenceWindow> overallWindows,
      List<CampaignCadenceWindow> windowsByKind) {}

  public record CampaignCadenceWindow(
      String label,
      String slotLabel,
      String detailLabel) {}

  public record CampaignPreviewPack(
      String draftId,
      String kindLabel,
      String title,
      String workflowLabel,
      String recommendedWindowLabel,
      List<CampaignChannelPreview> channels) {}

  public record CampaignChannelPreview(
      String channelCode,
      String channelLabel,
      String headline,
      String message,
      String landingUrl,
      String lengthLabel,
      String readinessLabel,
      boolean published,
      boolean retryReady) {}

  public record CampaignAttributionSummary(
      String draftId,
      String kindLabel,
      String title,
      String totalVisits,
      List<CampaignAttributionChannel> channels) {}

  public record CampaignAttributionChannel(
      String channelCode,
      String channelLabel,
      String visitsLabel) {}

  public record CampaignPublishRecoverySummary(
      String statusCode,
      String statusLabel,
      int actionableCount,
      int retryableCount,
      int blockedCount,
      int manualCount,
      String evaluatedAtLabel) {}

  public record CampaignPublishRecoveryItem(
      String draftId,
      String title,
      String kindLabel,
      String workflowLabel,
      String channelLabel,
      String outcomeLabel,
      String stateCode,
      String stateLabel,
      String recommendationLabel,
      String actionLabel,
      String ageLabel,
      String badgeClass,
      int severityRank,
      Instant referenceAt) {}

  public record CampaignQueueHealth(
      String statusCode,
      String statusLabel,
      int attentionCount,
      int staleDraftCount,
      int staleApprovedCount,
      int overdueScheduledCount,
      int blockedPublicationCount,
      int staleLinkedinCount,
      String evaluatedAtLabel) {}

  public record CampaignQueueRiskItem(
      String draftId,
      String title,
      String kindLabel,
      String workflowLabel,
      String riskLabel,
      String recommendationLabel,
      String ageLabel,
      String badgeClass,
      int severityRank,
      Instant referenceAt) {}

  public record CampaignAuditTrailEntry(
      String draftId,
      String title,
      String kindLabel,
      String workflowLabel,
      String eventLabel,
      String channelLabel,
      String outcomeLabel,
      String actorLabel,
      String timestampLabel) {}

  private record PublishMutation(CampaignDraftState draft, List<CampaignActivityEntry> activity) {}

  public record CampaignLinkedinHandoff(
      String draftId,
      String title,
      String workflowLabel,
      String body,
      String ctaLabel,
      String landingUrl,
      String headline,
      String message,
      String statusLabel,
      boolean completed,
      String publishedAtLabel) {}

  public record CampaignDetailSnapshot(
      CampaignPreviewCard draft,
      CampaignPreviewPack previewPack,
      CampaignAttributionSummary attribution,
      List<CampaignAuditTrailEntry> auditTrail,
      List<CampaignPublishRecoveryItem> recoveryItems,
      List<CampaignQueueRiskItem> risks,
      CampaignLinkedinHandoff linkedinHandoff,
      CampaignScheduleReadinessView readiness,
      CampaignPublishRecoverySummary recoverySummary,
      CampaignOperationsSummary summary,
      CampaignQueueHealth queueHealth) {}

  private record PublishRecoveryDescriptor(
      String outcomeCode,
      String stateCode,
      String stateLabel,
      String channelLabel,
      String outcomeLabel,
      String recommendationLabel,
      String actionLabel,
      String badgeClass,
      int severityRank,
      String ageLabel,
      Instant referenceAt) {}
}
