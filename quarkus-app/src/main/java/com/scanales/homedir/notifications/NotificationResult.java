package com.scanales.homedir.notifications;

/** Result of attempting to enqueue a notification. */
public enum NotificationResult {
  ACCEPTED_PERSISTED,
  ACCEPTED_VOLATILE,
  DROPPED_DUPLICATE,
  DROPPED_CAPACITY,
  ERROR
}
