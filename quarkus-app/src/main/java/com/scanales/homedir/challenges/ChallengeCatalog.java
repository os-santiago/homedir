package com.scanales.homedir.challenges;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ChallengeCatalog {

  private static final List<ChallengeDefinition> DEFINITIONS =
      List.of(
          new ChallengeDefinition(
              "community-scout",
              "Community Scout",
              "Learn the community loop by voting, exploring the board, and opening a member profile.",
              30,
              Map.of(
                  "community_vote", 3,
                  "community_board_members_view", 1,
                  "board_profile_open", 1)),
          new ChallengeDefinition(
              "event-explorer",
              "Event Explorer",
              "Navigate an event, inspect the agenda, and open a talk.",
              35,
              Map.of(
                  "event_view", 1,
                  "agenda_view", 1,
                  "talk_view", 1)),
          new ChallengeDefinition(
              "open-source-identity",
              "Open Source Identity",
              "Create your Homedir identity and connect GitHub plus Discord.",
              45,
              Map.of(
                  "first_login_bonus", 1,
                  "github_linked", 1,
                  "discord_linked", 1)));

  private static final Map<String, ChallengeDefinition> BY_ID = byId(DEFINITIONS);

  private ChallengeCatalog() {
  }

  public static List<ChallengeDefinition> definitions() {
    return DEFINITIONS;
  }

  public static ChallengeDefinition find(String id) {
    if (id == null || id.isBlank()) {
      return null;
    }
    return BY_ID.get(id.trim().toLowerCase(java.util.Locale.ROOT));
  }

  private static Map<String, ChallengeDefinition> byId(List<ChallengeDefinition> definitions) {
    Map<String, ChallengeDefinition> byId = new LinkedHashMap<>();
    for (ChallengeDefinition definition : definitions) {
      if (definition == null || definition.id() == null || definition.id().isBlank()) {
        continue;
      }
      byId.put(definition.id(), definition);
    }
    return Map.copyOf(byId);
  }
}
