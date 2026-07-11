package com.scanales.homedir.notifications;

import com.scanales.homedir.model.Talk;
import com.scanales.homedir.model.TalkInfo;
import com.scanales.homedir.service.EventService;
import com.scanales.homedir.service.UserScheduleService;
import io.homedir.time.AppClock;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;
import org.jboss.logging.Logger;

/** Periodically evaluates registered talks to emit runtime notifications. */
@ApplicationScoped
public class TalkStateEvaluator {

  private static final Logger LOG = Logger.getLogger(TalkStateEvaluator.class);

  @Inject UserScheduleService schedules;
  @Inject EventService events;
  @Inject NotificationService notifications;
  @Inject AppClock clock;

  @Scheduled(every = "{notifications.scheduler.interval}")
  void evaluate() {
    if (!NotificationConfig.schedulerEnabled) return;
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
    if (info == null) return;
    Talk talk = info.talk();
    if (talk.getStartTime() == null || talk.getEndTime() == null) return;
    ZoneId zone =
        info.event() != null && info.event().getTimezone() != null
            ? ZoneId.of(info.event().getTimezone())
            : ZoneId.of("America/Santiago");
    ZonedDateTime now = clock.now(zone);

    if (info.event() == null || info.event().getDate() == null) return;
    java.time.LocalDate talkDate = info.event().getDate().plusDays(talk.getDay() - 1);
    ZonedDateTime start = ZonedDateTime.of(talkDate, talk.getStartTime(), zone);

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
