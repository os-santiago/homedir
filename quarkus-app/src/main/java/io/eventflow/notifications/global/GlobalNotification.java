package io.eventflow.notifications.global;

/**
 * DTO for a global notification.
 */
public class GlobalNotification {
  public String id; // ULID/UUID
  public String type; // e.g. AGENDA_UPDATED
  public String category; // "event" | "break" | "talk" | "announcement"
  public String eventId; // optional
  public String talkId; // optional (required for category=break)
  public String title;
  public String message;
  public long createdAt; // epoch millis
  public String dedupeKey; // hash(type|eventId|timeSlot)
  public Long expiresAt; // optional
}
