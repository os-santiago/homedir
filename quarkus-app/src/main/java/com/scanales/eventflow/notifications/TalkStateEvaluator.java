package com.scanales.eventflow.notifications;

import com.scanales.eventflow.model.Talk;
import com.scanales.eventflow.model.TalkInfo;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.UserScheduleService;
import io.eventflow.time.AppClock;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import org.jboss.logging.Logger;

/** Periodically evaluates registered talks to emit runtime notifications. */
@ApplicationScoped
public class TalkStateEvaluator {

  private static final Logger LOG = Logger.getLogger(TalkStateEvaluator.class);

  @Inject
  UserScheduleService schedules;
  @Inject
  EventService events;
  @Inject
  NotificationService notifications;
  @Inject
  AppClock clock;

  @Scheduled(every = "{notifications.scheduler.interval}")
  void evaluate() {
    if (!NotificationConfig.schedulerEnabled)
      return;
    Set<String> users = schedules.listUsers();
    for (String user : users) {
      for (String talkId : schedules.getTalksForUser(user)) {
        try {
          evaluateTalk(user, talkId);
        } catch (Exception e) {
          LOG.debugf(e, "evaluator error talk=%s", talkId);
        }
      }
    }
  }

  private void evaluateTalk(String user, String talkId) {
    TalkInfo info = events.findTalkInfo(talkId);
    if (info == null)
      return;
    Talk talk = info.talk();
    if (talk.getStartTime() == null || talk.getEndTime() == null)
      return;
    ZoneId zone = info.event() != null && info.event().getTimezone() != null
        ? ZoneId.of(info.event().getTimezone())
        : ZoneId.of("America/Santiago");
    ZonedDateTime now = clock.now(zone);
    ZonedDateTime start = now.with(talk.getStartTime());
    long diff = ChronoUnit.MINUTES.between(now, start);
    if (diff > 12 * 60) {
      start = start.minusDays(1);
    } else if (diff < -12 * 60) {
      start = start.plusDays(1);
    }
    ZonedDateTime end = start.plusMinutes(talk.getDurationMinutes());
    if (now.isBefore(start) && start.minus(NotificationConfig.upcomingWindow).isBefore(now)) {
      enqueue(user, talkId, info, NotificationType.UPCOMING, "Charla pronto");
    } else if (!now.isBefore(start) && now.isBefore(end)) {
      if (end.minus(NotificationConfig.endingSoonWindow).isBefore(now)) {
        enqueue(user, talkId, info, NotificationType.ENDING_SOON, "Termina pronto");
      } else {
        enqueue(user, talkId, info, NotificationType.STARTED, "Ha comenzado");
      }
    } else if (!now.isBefore(end)) {
      enqueue(user, talkId, info, NotificationType.FINISHED, "Finalizó");
    }
  }

  private void enqueue(
      String user, String talkId, TalkInfo info, NotificationType type, String message) {
    Notification n = new Notification();
    n.userId = user;
    n.talkId = talkId;
    n.eventId = info.event() != null ? info.event().getId() : null;
    n.type = type;
    n.title = info.talk().getName();
    n.message = message;
    notifications.enqueue(n);
  }
}
