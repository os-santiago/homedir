package com.scanales.eventflow.notifications;

import io.quarkus.runtime.annotations.RegisterForReflection;

/** Simple notification model persisted per user. */
@RegisterForReflection
public class Notification {
  public String id;
  public String userId;
  public String talkId;
  public String eventId;
  public NotificationType type;
  public String title;
  public String message;
  public long createdAt;
  public Long readAt;
  public Long dismissedAt;
  public String dedupeKey;
  public Long expiresAt;
}
