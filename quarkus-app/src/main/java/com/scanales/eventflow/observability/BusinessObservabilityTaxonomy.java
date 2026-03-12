package com.scanales.eventflow.observability;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Canonical taxonomy for business observability modules and user-facing actions. */
public final class BusinessObservabilityTaxonomy {

  private static final Map<String, String> ACTION_ALIASES = buildActionAliases();
  private static final Map<String, String> ACTION_MODULES = buildActionModules();
  private static final List<String> MODULE_ORDER =
      List.of("home", "community", "events", "project", "profile", "admin", "info", "other");
  private static final List<String> ACTION_ORDER =
      List.of(
          "login_success",
          "event_view",
          "talk_view",
          "stage_visit",
          "talk_register",
          "community_vote",
          "community_propose_submit",
          "community_lightning_post",
          "community_lightning_comment",
          "board_profile_open",
          "profile_public_open",
          "cfp_submit",
          "cfp_approved",
          "volunteer_submit",
          "volunteer_selected",
          "volunteer_lounge_post",
          "economy_purchase");

  private BusinessObservabilityTaxonomy() {}

  public static List<String> moduleOrder() {
    return MODULE_ORDER;
  }

  public static List<String> actionOrder() {
    return ACTION_ORDER;
  }

  public static String moduleForRoute(String route) {
    if (route == null || route.isBlank()) {
      return "other";
    }
    String normalized = route.trim().toLowerCase(Locale.ROOT);
    if (normalized.equals("/") || normalized.startsWith("/legacy-home")) {
      return "home";
    }
    if (normalized.startsWith("/comunidad") || normalized.startsWith("/community")) {
      return "community";
    }
    if (normalized.startsWith("/eventos")
        || normalized.startsWith("/events")
        || normalized.startsWith("/event/")
        || normalized.startsWith("/scenario")
        || normalized.startsWith("/talk/")) {
      return "events";
    }
    if (normalized.startsWith("/proyectos") || normalized.startsWith("/projects")) {
      return "project";
    }
    if (normalized.startsWith("/private/profile") || normalized.startsWith("/u/")) {
      return "profile";
    }
    if (normalized.startsWith("/private/admin")) {
      return "admin";
    }
    if (normalized.startsWith("/privacy-policy")
        || normalized.startsWith("/politica-de-privacidad")
        || normalized.startsWith("/terms-of-service")
        || normalized.startsWith("/condiciones-del-servicio")
        || normalized.startsWith("/docs")
        || normalized.startsWith("/contacto")) {
      return "info";
    }
    return "other";
  }

  public static String canonicalAction(String rawAction) {
    if (rawAction == null || rawAction.isBlank()) {
      return null;
    }
    String normalized = sanitize(rawAction);
    if (normalized == null) {
      return null;
    }
    return ACTION_ALIASES.getOrDefault(normalized, fallbackAction(normalized));
  }

  public static String moduleForAction(String canonicalAction) {
    if (canonicalAction == null || canonicalAction.isBlank()) {
      return "other";
    }
    return ACTION_MODULES.getOrDefault(canonicalAction, "other");
  }

  private static String fallbackAction(String normalized) {
    if (normalized.startsWith("community.")) {
      return "community_activity";
    }
    if (normalized.startsWith("cfp.")) {
      return "cfp_activity";
    }
    if (normalized.startsWith("volunteer.")) {
      return "volunteer_activity";
    }
    if (normalized.startsWith("beta.")) {
      return "beta_activity";
    }
    return null;
  }

  private static String sanitize(String raw) {
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    if (normalized.isBlank()) {
      return null;
    }
    StringBuilder safe = new StringBuilder(normalized.length());
    for (int i = 0; i < normalized.length(); i++) {
      char c = normalized.charAt(i);
      if ((c >= 'a' && c <= 'z')
          || (c >= '0' && c <= '9')
          || c == '.'
          || c == '-'
          || c == '_') {
        safe.append(c);
      }
    }
    if (safe.isEmpty() || safe.length() > 80) {
      return null;
    }
    return safe.toString();
  }

  private static Map<String, String> buildActionAliases() {
    Map<String, String> aliases = new LinkedHashMap<>();
    aliases.put("login_success", "login_success");
    aliases.put("auth.login.callback", "login_success");
    aliases.put("event_view", "event_view");
    aliases.put("talk_view", "talk_view");
    aliases.put("stage_visit", "stage_visit");
    aliases.put("talk_register", "talk_register");
    aliases.put("community_vote", "community_vote");
    aliases.put("community.vote", "community_vote");
    aliases.put("community.vote.recommended", "community_vote");
    aliases.put("community.vote.must_see", "community_vote");
    aliases.put("community.vote.not_for_me", "community_vote");
    aliases.put("community_propose_submit", "community_propose_submit");
    aliases.put("community.submission.create", "community_propose_submit");
    aliases.put("community_lightning_post", "community_lightning_post");
    aliases.put("community.lightning.thread.create", "community_lightning_post");
    aliases.put("community_lightning_comment", "community_lightning_comment");
    aliases.put("community.lightning.comment.create", "community_lightning_comment");
    aliases.put("board_profile_open", "board_profile_open");
    aliases.put("profile.public.open", "profile_public_open");
    aliases.put("cfp_submit", "cfp_submit");
    aliases.put("cfp.submission.create", "cfp_submit");
    aliases.put("cfp_approved", "cfp_approved");
    aliases.put("cfp.submission.status.accepted", "cfp_approved");
    aliases.put("volunteer_submit", "volunteer_submit");
    aliases.put("volunteer.submission.create", "volunteer_submit");
    aliases.put("volunteer_selected", "volunteer_selected");
    aliases.put("volunteer.submission.status.selected", "volunteer_selected");
    aliases.put("volunteer.submission.status.accepted", "volunteer_selected");
    aliases.put("volunteer_lounge_post", "volunteer_lounge_post");
    aliases.put("volunteer.lounge.post", "volunteer_lounge_post");
    aliases.put("economy_purchase", "economy_purchase");
    return aliases;
  }

  private static Map<String, String> buildActionModules() {
    Map<String, String> modules = new LinkedHashMap<>();
    modules.put("login_success", "profile");
    modules.put("event_view", "events");
    modules.put("talk_view", "events");
    modules.put("stage_visit", "events");
    modules.put("talk_register", "events");
    modules.put("community_vote", "community");
    modules.put("community_propose_submit", "community");
    modules.put("community_lightning_post", "community");
    modules.put("community_lightning_comment", "community");
    modules.put("community_activity", "community");
    modules.put("board_profile_open", "community");
    modules.put("profile_public_open", "profile");
    modules.put("cfp_submit", "events");
    modules.put("cfp_approved", "events");
    modules.put("cfp_activity", "events");
    modules.put("volunteer_submit", "events");
    modules.put("volunteer_selected", "events");
    modules.put("volunteer_lounge_post", "events");
    modules.put("volunteer_activity", "events");
    modules.put("economy_purchase", "profile");
    modules.put("beta_activity", "other");
    return modules;
  }
}
