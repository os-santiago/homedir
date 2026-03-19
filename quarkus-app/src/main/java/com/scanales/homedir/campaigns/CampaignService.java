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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Comparator;
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

  @ConfigProperty(name = "quarkus.application.version", defaultValue = "dev")
  String runtimeVersion;

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

  public CampaignPreviewSnapshot preview(String localeCode) {
    CampaignStateSnapshot snapshot = currentState();
    ResourceBundle bundle = localizedBundle(localeCode);
    Locale locale = bundle.getLocale() == null ? Locale.forLanguageTag("es") : bundle.getLocale();
    List<CampaignPreviewCard> cards = snapshot.drafts().stream().map(item -> toPreview(item, bundle, locale)).toList();
    return new CampaignPreviewSnapshot(snapshot.generatedAt(), List.copyOf(cards));
  }

  private CampaignStateSnapshot generateAndPersist(String source) {
    CampaignStateSnapshot snapshot =
        new CampaignStateSnapshot(CampaignStateSnapshot.SCHEMA_VERSION, Instant.now(), buildDrafts());
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
            true));

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
                  true));
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
                    true)));

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
                    true)));

    drafts.sort(Comparator.comparing(CampaignDraftState::kind).thenComparing(CampaignDraftState::id));
    return List.copyOf(drafts);
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
          bundleText(bundle, draft.approvalRequired() ? "campaigns_admin_requires_approval" : "campaigns_admin_draft_only"));
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
            bundleText(bundle, draft.approvalRequired() ? "campaigns_admin_requires_approval" : "campaigns_admin_draft_only"));
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
          bundleText(bundle, draft.approvalRequired() ? "campaigns_admin_requires_approval" : "campaigns_admin_draft_only"));
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
          bundleText(bundle, draft.approvalRequired() ? "campaigns_admin_requires_approval" : "campaigns_admin_draft_only"));
      default -> new CampaignPreviewCard(
          draft.id(),
          kindLabel,
          draft.id(),
          draft.kind(),
          bundleText(bundle, "campaigns_admin_unknown_cta"),
          "/private/admin",
          localizedChannels(bundle, draft.suggestedChannels()),
          List.of(),
          bundleText(bundle, "campaigns_admin_draft_only"));
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

  public record CampaignPreviewSnapshot(Instant generatedAt, List<CampaignPreviewCard> drafts) {}

  public record CampaignPreviewCard(
      String id,
      String kindLabel,
      String title,
      String body,
      String ctaLabel,
      String ctaUrl,
      List<String> suggestedChannels,
      List<String> evidence,
      String modeLabel) {}
}
