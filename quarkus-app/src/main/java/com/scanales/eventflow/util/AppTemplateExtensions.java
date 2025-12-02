package com.scanales.eventflow.util;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.Talk;
import com.scanales.eventflow.service.EventService;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.qute.TemplateExtension;
import io.quarkus.security.identity.SecurityIdentity;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.regex.Pattern;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

@TemplateExtension(namespace = "app")
public class AppTemplateExtensions {

  public static int currentYear() {
    return LocalDate.now().getYear();
  }

  public static String version() {
    try {
      Config config = ConfigProvider.getConfig();
      return config.getOptionalValue("quarkus.application.version", String.class).orElse("dev");
    } catch (Exception e) {
      return "dev";
    }
  }

  public static boolean isAuthenticated() {
    SecurityIdentity identity = resolveIdentity();
    return identity != null && !identity.isAnonymous();
  }

  public static boolean isAdmin() {
    return AdminUtils.isAdmin(resolveIdentity());
  }

  /** Returns the display name of the authenticated user or {@code null}. */
  public static String userName() {
    SecurityIdentity identity = resolveIdentity();
    if (identity == null || identity.isAnonymous()) {
      return null;
    }
    String name = AdminUtils.getClaim(identity, "name");
    if (name == null || name.isBlank()) {
      name = identity.getPrincipal().getName();
    }
    return name;
  }

  /** Returns the avatar URL of the authenticated user if available. */
  public static String userAvatar() {
    SecurityIdentity identity = resolveIdentity();
    if (identity == null || identity.isAnonymous()) {
      return null;
    }
    return AdminUtils.getClaim(identity, "picture");
  }

  /** Simple URL validation for http/https links. */
  public static boolean validUrl(String url) {
    if (url == null || url.isBlank()) {
      return false;
    }
    try {
      URI uri = new URI(url);
      String scheme = uri.getScheme();
      return scheme != null && (scheme.equals("http") || scheme.equals("https"));
    } catch (Exception e) {
      return false;
    }
  }

  /** Basic email validation. */
  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

  public static boolean validEmail(String email) {
    return email != null && EMAIL_PATTERN.matcher(email).matches();
  }

  /** List of common time zones for event selection. */
  public static List<String> commonTimezones() {
    return List.of(
        "UTC",
        "America/Los_Angeles",
        "America/New_York",
        "America/Mexico_City",
        "America/Sao_Paulo",
        "America/Argentina/Buenos_Aires",
        "America/Bogota",
        "America/Santiago",
        "Europe/London",
        "Europe/Madrid",
        "Europe/Paris",
        "Asia/Tokyo",
        "Asia/Hong_Kong",
        "Asia/Kolkata",
        "Australia/Sydney");
  }

  /** Returns the standard UTC offset of the given time zone, e.g. "UTC-4". */
  public static String zoneDetail(String zoneId) {
    try {
      ZoneOffset offset = ZoneId.of(zoneId).getRules().getStandardOffset(Instant.now());
      int totalSeconds = offset.getTotalSeconds();
      if (totalSeconds == 0) {
        return "UTC";
      }
      int hours = totalSeconds / 3600;
      int minutes = Math.abs((totalSeconds / 60) % 60);
      if (minutes == 0) {
        return String.format("UTC%+d", hours);
      }
      return String.format("UTC%+d:%02d", hours, minutes);
    } catch (Exception e) {
      return "UTC";
    }
  }

  /** Returns a human-readable state for the given talk based on current time. */
  public static String talkState(Talk t) {
    return talkState(t, null);
  }

  /**
   * Variant of {@link #talkState(Talk)} that takes the parent {@link Event} so the calculation
   * considers the event date and day of the talk.
   */
  public static String talkState(Talk t, Event event) {
    if (t == null || t.getStartTime() == null) {
      return "Programada";
    }
    if (event != null && event.getDate() != null) {
      ZoneId zone;
      try {
        zone =
            event.getTimezone() != null ? ZoneId.of(event.getTimezone()) : ZoneId.systemDefault();
      } catch (Exception e) {
        zone = ZoneId.systemDefault();
      }
      LocalDateTime now = LocalDateTime.now(zone);
      LocalDateTime start =
          event.getDate().plusDays(Math.max(0, t.getDay() - 1L)).atTime(t.getStartTime());
      LocalDateTime end = start.plusMinutes(t.getDurationMinutes());
      if (now.isAfter(end)) {
        return "Finalizada";
      }
      if (!now.isBefore(start)) {
        return "En curso";
      }
      long minutes = Duration.between(now, start).toMinutes();
      if (minutes <= 15) {
        return "Por comenzar";
      }
      return "Programada";
    }
    // Fallback to time-only comparison when event date is unavailable
    LocalTime now = LocalTime.now();
    LocalTime start = t.getStartTime();
    LocalTime end = t.getEndTime();
    if (now.isAfter(end)) {
      return "Finalizada";
    }
    if (!now.isBefore(start)) {
      return "En curso";
    }
    long minutes = Duration.between(now, start).toMinutes();
    if (minutes <= 15) {
      return "Por comenzar";
    }
    return "Programada";
  }

  /** CSS class for the talk state badge. */
  public static String talkStateClass(Talk t) {
    return talkStateClass(t, null);
  }

  /**
   * Variant of {@link #talkStateClass(Talk)} that takes the parent event for accurate state
   * calculations.
   */
  public static String talkStateClass(Talk t, Event event) {
    return switch (talkState(t, event)) {
      case "Por comenzar" -> "warning";
      case "En curso" -> "info";
      case "Finalizada" -> "past";
      default -> "success";
    };
  }

  /** Returns the event id that contains the given talk. */
  public static String eventIdByTalk(String talkId) {
    EventService es = resolveEventService();
    if (es == null || talkId == null) {
      return null;
    }
    Event ev = es.findEventByTalk(talkId);
    return ev != null ? ev.getId() : null;
  }

  /** Returns the event id that contains the given scenario. */
  public static String eventIdByScenario(String scenarioId) {
    EventService es = resolveEventService();
    if (es == null || scenarioId == null) {
      return null;
    }
    Event ev = es.findEventByScenario(scenarioId);
    return ev != null ? ev.getId() : null;
  }

  private static SecurityIdentity resolveIdentity() {
    return getBean(SecurityIdentity.class);
  }

  private static EventService resolveEventService() {
    return getBean(EventService.class);
  }

  private static <T> T getBean(Class<T> type) {
    try {
      var container = Arc.container();
      if (container == null) {
        return null;
      }
      InstanceHandle<T> handle = container.instance(type);
      if (handle == null || !handle.isAvailable()) {
        return null;
      }
      return handle.get();
    } catch (IllegalStateException e) {
      return null;
    }
  }
}
