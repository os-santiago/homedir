package io.eventflow.notifications.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class NotificationDTO {
  public String id;

  @NotBlank public String talkId;
  @NotBlank public String eventId;

  @NotBlank
  @Pattern(regexp = "UPCOMING|STARTED|ENDING_SOON|FINISHED")
  public String type;

  @NotBlank public String title;
  @NotBlank public String message;

  public Long createdAt;
}
