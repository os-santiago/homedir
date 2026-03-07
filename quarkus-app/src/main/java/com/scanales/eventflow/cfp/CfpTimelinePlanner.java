package com.scanales.eventflow.cfp;

import com.scanales.eventflow.model.Event;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
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

    LocalDate resultsDate = eventStart.minusMonths(3L);
    LocalDate cfpClose = toLocalDate(cfpClosesAt, zone);
    if (cfpClose == null) {
      cfpClose = resultsDate.minusMonths(2L);
    }
    if (cfpClose.isAfter(resultsDate)) {
      cfpClose = resultsDate;
    }

    LocalDate cfpOpen = toLocalDate(cfpOpensAt, zone);
    if (cfpOpen == null) {
      cfpOpen = cfpClose.minusMonths(2L);
    }
    if (cfpOpen.isAfter(cfpClose)) {
      cfpOpen = cfpClose;
    }

    if (resultsDate.isBefore(cfpClose)) {
      resultsDate = cfpClose;
    }
    if (resultsDate.isAfter(eventStart)) {
      resultsDate = eventStart;
    }

    LocalDate nowDate = ZonedDateTime.ofInstant(now != null ? now : Instant.now(), zone).toLocalDate();
    LocalDate evaluationStart = cfpClose;
    LocalDate evaluationEnd = resultsDate.minusDays(7L);
    if (evaluationEnd.isBefore(evaluationStart)) {
      evaluationEnd = evaluationStart;
    }
    LocalDate presentationsDeadline = LocalDate.of(eventStart.getYear(), Month.AUGUST, 25);
    if (presentationsDeadline.isBefore(resultsDate)) {
      presentationsDeadline = resultsDate;
    }
    if (presentationsDeadline.isAfter(eventStart)) {
      presentationsDeadline = eventStart;
    }

    List<CfpTimelineStageView> stages = List.of(
        new CfpTimelineStageView(
            "cfp",
            spanDays(cfpOpen, cfpClose),
            formatWithoutYear(cfpOpen, safeLocale),
            formatWithoutYear(cfpClose, safeLocale),
            isActive(nowDate, cfpOpen, cfpClose)),
        new CfpTimelineStageView(
            "evaluation",
            spanDays(evaluationStart, evaluationEnd),
            formatWithoutYear(evaluationStart, safeLocale),
            formatWithoutYear(evaluationEnd, safeLocale),
            isActive(nowDate, evaluationStart, evaluationEnd)),
        new CfpTimelineStageView(
            "results",
            spanDays(resultsDate, resultsDate),
            formatWithoutYear(resultsDate, safeLocale),
            formatWithoutYear(resultsDate, safeLocale),
            isActive(nowDate, resultsDate, resultsDate)),
        new CfpTimelineStageView(
            "presentations",
            spanDays(resultsDate, presentationsDeadline),
            formatWithoutYear(resultsDate, safeLocale),
            formatWithoutYear(presentationsDeadline, safeLocale),
            isActive(nowDate, resultsDate, presentationsDeadline)),
        new CfpTimelineStageView(
            "event",
            spanDays(eventStart, eventEnd),
            formatWithoutYear(eventStart, safeLocale),
            formatWithoutYear(eventEnd, safeLocale),
            isActive(nowDate, eventStart, eventEnd)));

    return Optional.of(
        new CfpTimelineView(
            event.getId(),
            event.getTitle(),
            formatWithYear(cfpOpen, safeLocale),
            formatWithYear(eventEnd, safeLocale),
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

  private static String formatWithoutYear(LocalDate date, Locale locale) {
    if (date == null) {
      return "";
    }
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM", locale);
    return date.format(formatter);
  }

  private static String formatWithYear(LocalDate date, Locale locale) {
    if (date == null) {
      return "";
    }
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy", locale);
    return date.format(formatter);
  }
}
