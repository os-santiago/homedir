package com.scanales.eventflow.notifications;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Periodic tasks for notifications: retention cleanup. */
@ApplicationScoped
public class NotificationMaintenance {
  @Inject NotificationService service;

  @Scheduled(every = "300s")
  void cleanup() {
    service.purgeOld();
  }
}
