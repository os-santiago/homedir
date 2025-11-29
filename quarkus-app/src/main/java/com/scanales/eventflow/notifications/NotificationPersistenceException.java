package com.scanales.eventflow.notifications;

/** Signals failures while reading or writing persisted notifications. */
public class NotificationPersistenceException extends RuntimeException {

  public NotificationPersistenceException(String message, Throwable cause) {
    super(message, cause);
  }
}
