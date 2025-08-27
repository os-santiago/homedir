package com.scanales.eventflow.notifications;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

/** Utility to build deduplication keys based on a time window. */
public final class NotificationKey {
  private NotificationKey() {}

  public static String build(
      String userId, String talkId, NotificationType type, long createdAt, Duration window) {
    long slot = window.toMillis() > 0 ? createdAt / window.toMillis() : 0;
    String raw = userId + '|' + talkId + '|' + type + '|' + slot;
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-1");
      byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : digest) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
