package com.scanales.eventflow.cfp;

import com.scanales.eventflow.model.Event;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class CfpTimelinePlanner {

  private CfpTimelinePlanner() {
  }

  public static Optional<CfpTimelineView> build(
      Event event,
      Instant cfpOpensAt,
      Instant cfpClosesAt,
      Locale locale) {
    return build(event, cfpOpensAt, cfpClosesAt, locale, Instant.now());
  }

  static Optional<CfpTimelineView> build(
      Event event,
      Instant cfpOpensAt,
      Instant cfpClosesAt,
      Locale locale,
      Instant now) {
    if (event == null || event.getDate() == null) {
      return Optional.empty();
    }
    Locale safeLocale = locale != null ? locale : Locale.forLanguageTag("es");
    ZoneId zone = event.getZoneId();

    LocalDate eventStart = event.getDate();
    int timelineEventDays = Math.max(1, Math.min(2, event.getDays() > 0 ? event.getDays() : 2));
    LocalDate eventEnd = eventStart.plusDays(timelineEventDays - 1L);

    LocalDate resultsStart = eventStart.minusMonths(3L);
    LocalDate cfpClose = toLocalDate(cfpClosesAt, zone);
    if (cfpClose == null) {
      cfpClose = resultsStart;
    }
    if (cfpClose.isAfter(resultsStart)) {
      cfpClose = resultsStart;
    }

    LocalDate cfpOpen = toLocalDate(cfpOpensAt, zone);
    if (cfpOpen == null) {
      cfpOpen = cfpClose.minusMonths(2L);
    }
    if (cfpOpen.isAfter(cfpClose)) {
      cfpOpen = cfpClose;
    }

    if (resultsStart.isBefore(cfpClose)) {
      resultsStart = cfpClose;
    }
    if (resultsStart.isAfter(eventStart)) {
      resultsStart = eventStart;
    }

    LocalDate nowDate = ZonedDateTime.ofInstant(now != null ? now : Instant.now(), zone).toLocalDate();
    LocalDate evaluationStart = cfpClose;
    LocalDate evaluationEnd = resultsStart;
    LocalDate resultsEnd = eventStart;

    List<CfpTimelineStageView> stages = List.of(
        new CfpTimelineStageView(
            "cfp",
            spanDays(cfpOpen, cfpClose),
            format(cfpOpen, safeLocale),
            format(cfpClose, safeLocale),
            isActive(nowDate, cfpOpen, cfpClose)),
        new CfpTimelineStageView(
            "evaluation",
            spanDays(evaluationStart, evaluationEnd),
            format(evaluationStart, safeLocale),
            format(evaluationEnd, safeLocale),
            isActive(nowDate, evaluationStart, evaluationEnd)),
        new CfpTimelineStageView(
            "results",
            spanDays(resultsStart, resultsEnd),
            format(resultsStart, safeLocale),
            format(resultsEnd, safeLocale),
            isActive(nowDate, resultsStart, resultsEnd)),
        new CfpTimelineStageView(
            "event",
            spanDays(eventStart, eventEnd),
            format(eventStart, safeLocale),
            format(eventEnd, safeLocale),
            isActive(nowDate, eventStart, eventEnd)));

    return Optional.of(
        new CfpTimelineView(
            event.getId(),
            event.getTitle(),
            format(cfpOpen, safeLocale),
            format(eventEnd, safeLocale),
            stages));
  }

  private static LocalDate toLocalDate(Instant instant, ZoneId zoneId) {
    if (instant == null) {
      return null;
    }
    return ZonedDateTime.ofInstant(instant, zoneId).toLocalDate();
  }

  private static int spanDays(LocalDate from, LocalDate to) {
    if (from == null || to == null) {
      return 1;
    }
    LocalDate start = from.isBefore(to) ? from : to;
    LocalDate end = from.isBefore(to) ? to : from;
    long days = ChronoUnit.DAYS.between(start, end) + 1L;
    return (int) Math.max(1L, days);
  }

  private static boolean isActive(LocalDate now, LocalDate from, LocalDate to) {
    if (now == null || from == null || to == null) {
      return false;
    }
    LocalDate start = from.isBefore(to) ? from : to;
    LocalDate end = from.isBefore(to) ? to : from;
    return (!now.isBefore(start)) && (!now.isAfter(end));
  }

  private static String format(LocalDate date, Locale locale) {
    if (date == null) {
      return "";
    }
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy", locale);
    return date.format(formatter);
  }
}

