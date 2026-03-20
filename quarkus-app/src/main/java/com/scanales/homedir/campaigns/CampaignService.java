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
    return new CampaignPreviewSnapshot(
        snapshot.generatedAt(),
        List.copyOf(cards),
        globalPublishingEnabled,
        operationsStatus(operationsState, bundle, locale),
        List.copyOf(publisherStatuses),
        summarize(snapshot, bundle, locale),
        recentActivity(visibleDrafts, bundle, locale),
        cadenceGuidance,
        List.copyOf(previewPacks),
        attributionSummary(visibleDrafts, bundle),
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
            List.copyOf(risks),
            linkedinHandoff,
            schedulingReadiness(selectedCard),
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
    return new CampaignChannelPreview(
        channelCode,
        channelLabel,
        headline,
        message,
        landingUrl,
        named(bundle, "campaigns_admin_preview_length", Map.of("count", String.valueOf(charCount), "limit", String.valueOf(limit))),
        bundleText(bundle, readinessKey));
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
    if (!currentOperationsState.isChannelAutomationEnabled(channel)) {
      return "channel_paused";
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
        status.channelEnabled() && operationsState.isChannelAutomationEnabled(status.channel());
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
      List<CampaignRecentActivity> recentActivity,
      CampaignCadenceGuidance cadenceGuidance,
      List<CampaignPreviewPack> previewPacks,
      List<CampaignAttributionSummary> attribution,
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
      String readinessLabel) {}

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
      List<CampaignQueueRiskItem> risks,
      CampaignLinkedinHandoff linkedinHandoff,
      CampaignScheduleReadinessView readiness,
      CampaignOperationsSummary summary,
      CampaignQueueHealth queueHealth) {}
}
