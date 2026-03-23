package com.scanales.homedir.reputation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Phase-0 canonical mapping between proposed reputation events and existing platform signals. */
public final class ReputationEventTaxonomy {

  private static final List<EventDefinition> DEFINITIONS = buildDefinitions();
  private static final Map<String, EventDefinition> BY_EVENT_TYPE = indexByEventType(DEFINITIONS);

  private ReputationEventTaxonomy() {}

  public static List<EventDefinition> definitions() {
    return DEFINITIONS;
  }

  public static Optional<EventDefinition> find(String eventType) {
    if (eventType == null || eventType.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(BY_EVENT_TYPE.get(eventType.trim().toLowerCase(Locale.ROOT)));
  }

  public record EventDefinition(
      String eventType,
      ReputationDimension dimension,
      int baseWeight,
      List<String> sourceSignals,
      String rationale) {}

  private static List<EventDefinition> buildDefinitions() {
    return List.of(
        new EventDefinition(
            "quest_completed",
            ReputationDimension.CONTRIBUTION,
            10,
            List.of("funnel:challenge.completed", "funnel:challenge.completed.{challenge_id}"),
            "Completion of meaningful challenge paths should add contribution value."),
        new EventDefinition(
            "event_attended",
            ReputationDimension.PARTICIPATION,
            6,
            List.of("event_view:{event_id}", "stage_visit:{stage_id}:{yyyy-mm-dd}"),
            "Attending and following event sessions signals active participation."),
        new EventDefinition(
            "event_speaker",
            ReputationDimension.CONTRIBUTION,
            14,
            List.of("funnel:cfp_approved", "funnel:cfp.submission.status.accepted"),
            "Accepted talks are a higher-effort community contribution."),
        new EventDefinition(
            "content_published",
            ReputationDimension.CONTRIBUTION,
            12,
            List.of(
                "funnel:community_propose_approved",
                "funnel:community.lightning.thread.create",
                "funnel:community_lightning_post"),
            "Publishing useful content increases contributor footprint."),
        new EventDefinition(
            "content_recommended",
            ReputationDimension.RECOGNITION,
            15,
            List.of("funnel:community.vote.recommended", "funnel:community.vote.must_see"),
            "Peer recommendation should weigh more than raw volume."),
        new EventDefinition(
            "streak_milestone",
            ReputationDimension.CONSISTENCY,
            8,
            List.of("gamification:daily_checkin"),
            "Sustained cadence adds consistency, bounded to avoid farming."),
        new EventDefinition(
            "contribution_highlighted",
            ReputationDimension.RECOGNITION,
            18,
            List.of("community.featured.snapshot"),
            "Highlighted work reflects perceived quality by the community system."),
        new EventDefinition(
            "peer_help_acknowledged",
            ReputationDimension.RECOGNITION,
            16,
            List.of("funnel:volunteer_lounge_post", "volunteer.lounge.announcement"),
            "Useful help acknowledged by peers should boost trusted reputation."));
  }

  private static Map<String, EventDefinition> indexByEventType(List<EventDefinition> definitions) {
    Map<String, EventDefinition> index = new LinkedHashMap<>();
    for (EventDefinition definition : definitions) {
      if (definition == null || definition.eventType() == null) {
        continue;
      }
      index.put(definition.eventType().trim().toLowerCase(Locale.ROOT), definition);
    }
    return Map.copyOf(index);
  }
}
