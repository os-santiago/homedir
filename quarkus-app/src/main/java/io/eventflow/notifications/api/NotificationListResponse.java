package io.eventflow.notifications.api;

import com.scanales.eventflow.notifications.NotificationService.NotificationPage;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;

@RegisterForReflection
public record NotificationListResponse(List<Item> items, Long nextCursor, long unreadCount) {
  @RegisterForReflection
  public record Item(
      String id, String title, String message, String type, long createdAt, Long readAt) {}

  public static NotificationListResponse from(NotificationPage page) {
    List<Item> items =
        page.items().stream()
            .map(
                n ->
                    new Item(
                        n.id,
                        n.title,
                        n.message,
                        n.type != null ? n.type.name() : null,
                        n.createdAt,
                        n.readAt))
            .toList();
    return new NotificationListResponse(items, page.nextCursor(), page.unreadCount());
  }
}
