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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
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

  @PostConstruct
  void init() {
    synchronized (stateLock) {
      refreshFromDisk(true);
      if (currentState.drafts().isEmpty()) {
        generateAndPersist("startup");
      }
    }
  }

  @Scheduled(every = "{campaigns.drafts.refresh-interval:6h}")
  void scheduledRefresh() {
    synchronized (stateLock) {
      generateAndPersist("schedule");
    }
  }

  @Scheduled(every = "{campaigns.publish.scan-interval:5m}")
  void scheduledPublish() {
    synchronized (stateLock) {
      publishScheduledDrafts("schedule");
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
      persistenceService.saveCampaignState(currentState);
      lastKnownStateMtime = persistenceService.campaignStateLastModifiedMillis();
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
      persistenceService.saveCampaignState(currentState);
      lastKnownStateMtime = persistenceService.campaignStateLastModifiedMillis();
      recordWorkflowChange("CAMPAIGN_DRAFT_RESET", draftId);
      return currentState;
    }
  }

  public CampaignStateSnapshot scheduleDraft(String draftId, LocalDateTime scheduledLocal, String actor) {
    synchronized (stateLock) {
      refreshFromDisk(false);
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
      persistenceService.saveCampaignState(currentState);
      lastKnownStateMtime = persistenceService.campaignStateLastModifiedMillis();
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
      persistenceService.saveCampaignState(currentState);
      lastKnownStateMtime = persistenceService.campaignStateLastModifiedMillis();
      recordWorkflowChange("CAMPAIGN_DRAFT_UNSCHEDULED", draftId);
      return currentState;
    }
  }

  public CampaignStateSnapshot publishScheduledNow() {
    synchronized (stateLock) {
      return publishScheduledDrafts("manual_admin");
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
      persistenceService.saveCampaignState(currentState);
      lastKnownStateMtime = persistenceService.campaignStateLastModifiedMillis();
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
    CampaignStateSnapshot snapshot = currentState();
    ResourceBundle bundle = localizedBundle(localeCode);
    Locale locale = bundle.getLocale() == null ? Locale.forLanguageTag("es") : bundle.getLocale();
    List<CampaignPreviewCard> cards = snapshot.drafts().stream().map(item -> toPreview(item, bundle, locale)).toList();
    List<CampaignPublisherPreviewStatus> publisherStatuses =
        List.of(discordPublisherService.status(), blueskyPublisherService.status(), mastodonPublisherService.status())
            .stream()
            .map(status -> toPublisherStatus(status, bundle))
            .toList();
    boolean globalPublishingEnabled = publisherStatuses.stream().anyMatch(CampaignPublisherPreviewStatus::globalEnabled);
    return new CampaignPreviewSnapshot(
        snapshot.generatedAt(),
        List.copyOf(cards),
        globalPublishingEnabled,
        List.copyOf(publisherStatuses),
        summarize(snapshot, bundle, locale),
        recentActivity(snapshot, bundle, locale),
        snapshot.drafts().stream()
            .filter(this::eligibleForLinkedinHandoff)
            .map(draft -> toLinkedinHandoff(draft, bundle, locale))
            .toList());
  }

  void resetStateForTests() {
    synchronized (stateLock) {
      currentState = CampaignStateSnapshot.empty();
      persistenceService.saveCampaignStateSync(currentState);
      lastKnownStateMtime = persistenceService.campaignStateLastModifiedMillis();
    }
  }

  private CampaignStateSnapshot generateAndPersist(String source) {
    CampaignStateSnapshot previous = currentState == null ? CampaignStateSnapshot.empty() : currentState;
    CampaignStateSnapshot snapshot =
        new CampaignStateSnapshot(
            CampaignStateSnapshot.SCHEMA_VERSION,
            Instant.now(),
            mergeDrafts(previous.drafts(), buildDrafts()));
    currentState = snapshot;
    persistenceService.saveCampaignState(snapshot);
    lastKnownStateMtime = persistenceService.campaignStateLastModifiedMillis();
    recordInsightRefresh(snapshot, source);
    return snapshot;
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

  private CampaignPreviewCard toPreview(CampaignDraftState draft, ResourceBundle bundle, Locale locale) {
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
          publisherOutcomeLabel);
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
            publisherOutcomeLabel);
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
          publisherOutcomeLabel);
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
          publisherOutcomeLabel);
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
          publisherOutcomeLabel);
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

  private CampaignStateSnapshot publishScheduledDrafts(String source) {
    refreshFromDisk(false);
    Instant now = Instant.now();
    List<CampaignDraftState> drafts = currentState.drafts().stream().map(draft -> publishIfDue(draft, now, source)).toList();
    currentState = new CampaignStateSnapshot(CampaignStateSnapshot.SCHEMA_VERSION, currentState.generatedAt(), drafts);
    persistenceService.saveCampaignState(currentState);
    lastKnownStateMtime = persistenceService.campaignStateLastModifiedMillis();
    return currentState;
  }

  private CampaignDraftState publishIfDue(CampaignDraftState draft, Instant now, String source) {
    if ((draft.workflowState() != CampaignWorkflowState.SCHEDULED
            && draft.workflowState() != CampaignWorkflowState.PUBLISHED)
        || draft.scheduledFor() == null) {
      return draft;
    }
    if (draft.scheduledFor().isAfter(now)) {
      return draft;
    }
    Map<String, Instant> nextPublishedChannels = new LinkedHashMap<>(draft.publishedChannels());
    CampaignWorkflowState nextState = draft.workflowState();
    Instant lastAttemptAt = draft.lastPublishAttemptAt();
    String lastOutcome = draft.lastPublishOutcome();
    for (CampaignPublisherStatus status :
        List.of(discordPublisherService.status(), blueskyPublisherService.status(), mastodonPublisherService.status())) {
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
      } else if (result.skipped()) {
        recordPublishInsight("CAMPAIGN_PUBLISH_SKIPPED", draft.id(), source, result.channel(), result.outcome());
      } else {
        recordPublishInsight("CAMPAIGN_PUBLISH_FAILED", draft.id(), source, result.channel(), result.outcome());
      }
    }
    return draft.withPublishStatus(
        nextState,
        Map.copyOf(nextPublishedChannels),
        lastAttemptAt,
        lastOutcome,
        now);
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
    return new CampaignStateSnapshot(CampaignStateSnapshot.SCHEMA_VERSION, currentState.generatedAt(), drafts);
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

  private List<CampaignRecentActivity> recentActivity(
      CampaignStateSnapshot snapshot, ResourceBundle bundle, Locale locale) {
    return snapshot.drafts().stream()
        .sorted(
            Comparator.comparing(CampaignDraftState::updatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(CampaignDraftState::generatedAt, Comparator.reverseOrder()))
        .limit(6)
        .map(
            draft ->
                new CampaignRecentActivity(
                    toPreview(draft, bundle, locale).title(),
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
    CampaignPreviewCard preview = toPreview(draft, bundle, locale);
    boolean completed = draft.publishedChannels().containsKey("linkedin");
    return new CampaignLinkedinHandoff(
        draft.id(),
        preview.title(),
        preview.workflowLabel(),
        preview.body(),
        preview.ctaLabel(),
        absoluteUrl(preview.ctaUrl()),
        linkedinHeadline(preview.title(), bundle),
        linkedinMessage(draft, preview, bundle),
        completed ? bundleText(bundle, "campaigns_admin_linkedin_done") : bundleText(bundle, "campaigns_admin_linkedin_pending"),
        completed,
        localizedDateTime(draft.publishedChannels().get("linkedin"), locale));
  }

  private String linkedinHeadline(String title, ResourceBundle bundle) {
    return named(bundle, "campaigns_admin_linkedin_headline", Map.of("title", safe(title)));
  }

  private String linkedinMessage(
      CampaignDraftState draft, CampaignPreviewCard preview, ResourceBundle bundle) {
    return named(
        bundle,
        "campaigns_admin_linkedin_message",
        Map.of(
            "title", safe(preview.title()),
            "body", safe(preview.body()),
            "ctaLabel", safe(preview.ctaLabel()),
            "ctaUrl", absoluteUrl(preview.ctaUrl()),
            "evidence", String.join(" · ", preview.evidence())));
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
      List<CampaignPublisherPreviewStatus> publisherStatuses,
      CampaignOperationsSummary summary,
      List<CampaignRecentActivity> recentActivity,
      List<CampaignLinkedinHandoff> linkedinHandoffs) {}

  public record CampaignPublisherPreviewStatus(
      String channelCode,
      String channelLabel,
      boolean globalEnabled,
      boolean dryRun,
      boolean channelEnabled,
      boolean configured,
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
      String publisherOutcomeLabel) {}

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
}
